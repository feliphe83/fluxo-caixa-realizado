package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.OrcamentoEstouradoDAO;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * tipo_relatorio = "orcamento_estourado" — avisa por WhatsApp os itens de
 * cotação cujo orçamento estourou e ainda aguardam a aprovação de alçada.
 *
 * É um agendamento recorrente (intervalo em minutos): a cada ciclo consulta o
 * ERP e manda uma mensagem por ITEM novo. Assim que alguém aprova o estouro,
 * o item sai da consulta e para de aparecer.
 *
 * Como nos alertas de ordem aprovada e divergência, não há alçada por
 * destinatário: a consulta não filtra por aprovador, então todos os telefones
 * marcados no agendamento recebem a mesma lista.
 *
 * O controle de não repetir é o mesmo dos outros alertas
 * ({@link AlertaOcPendenteDAO}), com {@link #TIPO} separando-os na tabela. A
 * chave é a solicitação mais a cotação e o material — um item de um estouro é
 * único por esses três, e basta um aviso por item.
 *
 * parametros (opcional): {"negocio": 0} — 0 traz todos; 1/3/4 filtram
 * agrícola/indústria/administrativo.
 */
public class AlertaOrcamentoEstouradoHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(AlertaOrcamentoEstouradoHandler.class.getName());

    /** Distingue este alerta dos demais na tabela de controle de envio. */
    public static final String TIPO = "ORCAMENTO ESTOURADO";

    /** Teto de mensagens por destinatário em um ciclo, pra uma enxurrada não virar spam. */
    private static final int MAX_MENSAGENS_POR_CICLO = 15;

    private final AlertaOcPendenteDAO controle = new AlertaOcPendenteDAO();
    private final OrcamentoEstouradoDAO erp = new OrcamentoEstouradoDAO();

    @Override
    public String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        int negocio = negocio(parametros);

        // Uma consulta só para todos: sem alçada, a lista é a mesma para
        // qualquer destinatário — o que muda é apenas o que cada um já viu.
        List<Map<String, Object>> estouros = erp.buscar(negocio);
        if (estouros.isEmpty()) {
            return "Nenhum orçamento estourado pendente.";
        }

        int totalAvisados = 0;
        List<String> falhas = new ArrayList<>();

        for (Map<String, Object> destinatario : destinatarios) {
            try {
                totalAvisados += avisar(destinatario, estouros);
            } catch (Exception e) {
                // Falha de um destinatário não pode travar os demais.
                falhas.add(descreverFalha(destinatario, e));
                LOG.log(Level.SEVERE, "Erro no alerta de orçamento estourado para " + destinatario.get("nome"), e);
            }
        }

        if (!falhas.isEmpty()) {
            throw new RuntimeException(String.join(" | ", falhas));
        }
        return totalAvisados == 0
                ? "Nenhum estouro novo."
                : totalAvisados + " estouro(s) avisado(s).";
    }

    private static int negocio(JsonObject parametros) {
        if (parametros == null || !parametros.has("negocio") || parametros.get("negocio").isJsonNull()) {
            return 0;
        }
        try { return parametros.get("negocio").getAsInt(); }
        catch (Exception e) { return 0; }
    }

    /** @return quantos estouros foram avisados a este destinatário */
    private int avisar(Map<String, Object> destinatario, List<Map<String, Object>> estouros) throws Exception {
        int idUsuario = ((Number) destinatario.get("id")).intValue();
        String nome = String.valueOf(destinatario.get("nome"));
        String telefone = String.valueOf(destinatario.get("telefone"));

        Set<String> jaEnviados = controle.jaEnviados(idUsuario);

        int enviados = 0;
        for (Map<String, Object> estouro : estouros) {
            String nrSolicitacao = txt(estouro.get("nr_solicitacao"));
            String item = itemChave(estouro);
            if (jaEnviados.contains(AlertaOcPendenteDAO.chave(TIPO, nrSolicitacao, item))) continue;

            if (enviados >= MAX_MENSAGENS_POR_CICLO) {
                LOG.info("Alerta de orçamento estourado para " + nome + ": limite de "
                        + MAX_MENSAGENS_POR_CICLO + " mensagens por ciclo atingido, o resto vai no próximo.");
                break;
            }

            EvolutionApiUtil.enviarTexto(telefone, montarMensagem(estouro));
            // Só marca depois do envio dar certo: se a Evolution API falhar, o
            // estouro continua "não avisado" e entra de novo no próximo ciclo.
            controle.registrarEnviado(idUsuario, TIPO, nrSolicitacao, item);
            enviados++;
        }

        if (enviados > 0) {
            LOG.info("Alerta de orçamento estourado: " + enviados + " estouro(s) avisado(s) para " + nome);
        }
        return enviados;
    }

    /** Identidade do estouro: cotação e material, dentro da solicitação. */
    private static String itemChave(Map<String, Object> estouro) {
        return txt(estouro.get("nr_cotacao")) + "|" + txt(estouro.get("cod_material"));
    }

    private static String montarMensagem(Map<String, Object> e) {
        StringBuilder msg = new StringBuilder();
        msg.append("🚨 ORÇAMENTO ESTOURADO — AGUARDANDO APROVAÇÃO 🚨\n\n")
           .append("🧾 Cotação: ").append(txt(e.get("nr_cotacao"))).append("\n")
           .append("📄 Solicitação: ").append(txt(e.get("nr_solicitacao"))).append("\n")
           .append("📦 Material: ").append(txt(e.get("descricao")))
               .append(" (").append(txt(e.get("cod_material"))).append(")\n")
           .append("🔢 Qtde aprovada: ").append(FormatoMensagem.quantidade(e.get("qtde_aprovada")))
               .append(" ").append(txt(e.get("cod_unidade"))).append("\n")
           .append("🏢 Fornecedor: ").append(txt(e.get("nome"))).append("\n")
           .append("🧑‍💼 Comprador: ").append(txt(e.get("comprador"))).append("\n")
           .append("🏷️ Negócio: ").append(nomeNegocio(e.get("negocio"))).append("\n");

        String estoque = txt(e.get("qtde_estoque"));
        if (!estoque.isBlank()) {
            msg.append("📦 Estoque atual: ").append(FormatoMensagem.quantidade(e.get("qtde_estoque"))).append("\n");
        }

        msg.append("\n💵 VALOR TOTAL: R$ ").append(FormatoMensagem.valor(e.get("precototal"))).append("\n")
           .append("======================");
        return msg.toString();
    }

    /** O código do negócio no ERP vira o nome que a usina usa. */
    private static String nomeNegocio(Object codigo) {
        String c = txt(codigo);
        return switch (c) {
            case "1" -> "Agrícola";
            case "3" -> "Indústria";
            case "4" -> "Administrativo";
            default  -> c.isBlank() ? "—" : c;
        };
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
