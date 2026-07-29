package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.OrdemCompraAprovadaDAO;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * tipo_relatorio = "oc_aprovada_alto_valor" — avisa por WhatsApp as ordens de
 * compra aprovadas hoje cujo valor total passa de um limite (por padrão,
 * R$ 30.000,00).
 *
 * É um agendamento recorrente (intervalo em minutos): a cada ciclo consulta o
 * ERP e manda uma mensagem por ORDEM nova. A consulta devolve uma linha por
 * item, então as linhas são agrupadas por número de processo — a mensagem
 * traz o cabeçalho da ordem, todos os itens dela e o total no fim.
 *
 * Como nos alertas de contrato e divergência, não há alçada: a consulta não
 * filtra por aprovador, então todo destinatário recebe a mesma lista e
 * ninguém precisa de código de logon do ERP.
 *
 * O controle de não repetir é o mesmo dos outros alertas
 * ({@link AlertaOcPendenteDAO}), com {@link #TIPO} separando-os na tabela. A
 * chave é o número da ordem: uma ordem aprovada não muda, então basta um
 * aviso por ordem.
 *
 * parametros: {"valorMinimo": 30000} — total da ordem a partir do qual avisa.
 */
public class AlertaOrdemCompraAprovadaHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(AlertaOrdemCompraAprovadaHandler.class.getName());

    /** Distingue este alerta dos demais na tabela de controle de envio. */
    public static final String TIPO = "OC APROVADA ALTO VALOR";

    /** Usado quando o agendamento não define nada — o mesmo limite da consulta original. */
    private static final double VALOR_MINIMO_PADRAO = 30000;

    /** Teto de mensagens por destinatário em um ciclo, pra uma enxurrada não virar spam. */
    private static final int MAX_MENSAGENS_POR_CICLO = 15;

    private final AlertaOcPendenteDAO controle = new AlertaOcPendenteDAO();
    private final OrdemCompraAprovadaDAO erp = new OrdemCompraAprovadaDAO();

    @Override
    public String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        double valorMinimo = valorMinimo(parametros);

        // Uma consulta só para todos: sem alçada, a lista é a mesma para
        // qualquer destinatário — o que muda é apenas o que cada um já viu.
        List<Ordem> ordens = agrupar(erp.buscarAprovadasAcimaDe(valorMinimo));
        if (ordens.isEmpty()) {
            return "Nenhuma ordem aprovada acima de R$ " + FormatoMensagem.valor(valorMinimo) + ".";
        }

        int totalAvisadas = 0;
        List<String> falhas = new ArrayList<>();

        for (Map<String, Object> destinatario : destinatarios) {
            try {
                totalAvisadas += avisar(destinatario, ordens, valorMinimo);
            } catch (Exception e) {
                // Falha de um destinatário não pode travar os demais.
                falhas.add(descreverFalha(destinatario, e));
                LOG.log(Level.SEVERE, "Erro no alerta de ordem aprovada para " + destinatario.get("nome"), e);
            }
        }

        if (!falhas.isEmpty()) {
            throw new RuntimeException(String.join(" | ", falhas));
        }
        return totalAvisadas == 0
                ? "Nenhuma ordem nova acima de R$ " + FormatoMensagem.valor(valorMinimo) + "."
                : totalAvisadas + " ordem(ns) avisada(s).";
    }

    private static double valorMinimo(JsonObject parametros) {
        if (parametros == null || !parametros.has("valorMinimo") || parametros.get("valorMinimo").isJsonNull()) {
            return VALOR_MINIMO_PADRAO;
        }
        try {
            return parametros.get("valorMinimo").getAsDouble();
        } catch (Exception e) {
            LOG.warning("valorMinimo inválido no agendamento, usando " + VALOR_MINIMO_PADRAO + ": " + e.getMessage());
            return VALOR_MINIMO_PADRAO;
        }
    }

    /** Uma ordem de compra com seus itens, já consolidada a partir das linhas do ERP. */
    private static final class Ordem {
        final String numero;
        final List<Map<String, Object>> itens = new ArrayList<>();
        String aprovador = "";
        String fornecedor = "";
        /** Total da ordem inteira, calculado pela consulta (soma dos itens do processo). */
        Object valorTotal;

        Ordem(String numero) { this.numero = numero; }
    }

    /**
     * Agrupa as linhas por numero_processo — o mesmo recorte que a consulta
     * usa para somar o total da ordem, então o total exibido bate com os itens
     * listados.
     */
    private static List<Ordem> agrupar(List<Map<String, Object>> linhas) {
        Map<String, Ordem> porNumero = new LinkedHashMap<>();
        for (Map<String, Object> l : linhas) {
            Ordem ordem = porNumero.computeIfAbsent(txt(l.get("numero_processo")), Ordem::new);
            if (ordem.itens.isEmpty()) {
                ordem.aprovador = txt(l.get("aprovador"));
                ordem.fornecedor = txt(l.get("nome_fornecedor"));
                ordem.valorTotal = l.get("vlr_total_por_numero_processo");
            }
            ordem.itens.add(l);
        }
        return new ArrayList<>(porNumero.values());
    }

    /** @return quantas ordens foram avisadas a este destinatário */
    private int avisar(Map<String, Object> destinatario, List<Ordem> ordens, double valorMinimo) throws Exception {
        int idUsuario = ((Number) destinatario.get("id")).intValue();
        String nome = String.valueOf(destinatario.get("nome"));
        String telefone = String.valueOf(destinatario.get("telefone"));

        Set<String> jaEnviados = controle.jaEnviados(idUsuario);

        int enviadas = 0;
        for (Ordem ordem : ordens) {
            if (jaEnviados.contains(AlertaOcPendenteDAO.chave(TIPO, ordem.numero, ""))) continue;

            if (enviadas >= MAX_MENSAGENS_POR_CICLO) {
                LOG.info("Alerta de ordem aprovada para " + nome + ": limite de "
                        + MAX_MENSAGENS_POR_CICLO + " mensagens por ciclo atingido, o resto vai no próximo.");
                break;
            }

            EvolutionApiUtil.enviarTexto(telefone, montarMensagem(ordem, valorMinimo));
            // Só marca depois do envio dar certo: se a Evolution API falhar, a
            // ordem continua "não avisada" e entra de novo no próximo ciclo.
            controle.registrarEnviado(idUsuario, TIPO, ordem.numero, "");
            enviadas++;
        }

        if (enviadas > 0) {
            LOG.info("Alerta de ordem aprovada: " + enviadas + " ordem(ns) avisada(s) para " + nome);
        }
        return enviadas;
    }

    /** Mesmo formato da aplicação que rodava fora do sistema. */
    private static String montarMensagem(Ordem ordem, double valorMinimo) {
        StringBuilder msg = new StringBuilder();
        msg.append("⚠️ ORDEM DE COMPRA APROVADA COM VALOR MAIOR QUE R$ ")
           .append(FormatoMensagem.valor(valorMinimo)).append(" ⚠️\n\n")
           .append("🆔 Ordem de Compra: ").append(ordem.numero).append("\n")
           .append("🧑‍💼 Aprovador: ").append(ordem.aprovador).append("\n")
           .append("🏢 Fornecedor: ").append(ordem.fornecedor).append("\n")
           .append("📋 Itens:\n\n");

        for (Map<String, Object> item : ordem.itens) {
            msg.append("   📦 Item: ").append(txt(item.get("desc_material"))).append("\n")
               .append("   🔢 Quantidade: ").append(FormatoMensagem.quantidade(item.get("qtdesolicitada"))).append("\n")
               .append("   💰 Preço Unitário: R$ ").append(FormatoMensagem.valor(item.get("preco"))).append("\n")
               .append("   📊 Preço Unitário da Última Compra: R$ ").append(FormatoMensagem.valor(item.get("ultima_compra"))).append("\n")
               .append("   📈 Variação: ").append(FormatoMensagem.valor(item.get("variacao_percentual"))).append("%\n")
               .append("   📅 Vencimento: ").append(txt(item.get("vencimento"))).append("\n")
               .append("   💵 Valor do Item: R$ ").append(FormatoMensagem.valor(item.get("vlrtotal"))).append("\n")
               .append("   -----------------------------\n");
        }

        msg.append("\n💰 VALOR TOTAL DA ORDEM ").append(ordem.numero).append(": R$ ")
           .append(FormatoMensagem.valor(ordem.valorTotal)).append("\n")
           .append("======================");
        return msg.toString();
    }

    private static String descreverFalha(Map<String, Object> destinatario, Exception e) {
        String nome = txt(destinatario.get("nome"));
        String telefone = txt(destinatario.get("telefone"));
        String motivo = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (motivo.contains("\"exists\":false")) {
            motivo = "número sem conta de WhatsApp (confira o telefone no cadastro)";
        }
        return nome + (telefone.isEmpty() ? "" : " (" + telefone + ")") + ": " + motivo;
    }

    private static String txt(Object v) {
        return FormatoMensagem.texto(v);
    }
}
