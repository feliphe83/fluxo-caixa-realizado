package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.VariacaoPrecoDAO;
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
 * tipo_relatorio = "variacao_preco" — avisa por WhatsApp quando um item de
 * cotação liberado hoje vem com preço bem acima da última compra.
 *
 * O aumento considerado é o real: o preço da última entrada é corrigido pela
 * inflação do período antes da comparação (ver {@link VariacaoPrecoDAO}), de
 * modo que reajuste normal não vira alerta.
 *
 * É um agendamento recorrente (intervalo em minutos): a cada ciclo consulta
 * o ERP e manda uma mensagem por ITEM novo. "Novo" é o item que ainda não
 * foi avisado àquela pessoa — enquanto a cotação seguir liberada ela continua
 * voltando na consulta, mas não é reenviada. O controle é o mesmo do alerta
 * de ordem de compra ({@link AlertaOcPendenteDAO}), com {@link #TIPO}
 * separando os dois na tabela.
 *
 * Cada destinatário precisa do "Código de logon no ERP" no cadastro de
 * usuário: é ele que diz quais cotações são da alçada daquela pessoa. Quem
 * não tiver é ignorado (com aviso no log). A exceção é quem está marcado
 * como "recebe todas", que acompanha o grupo inteiro sem precisar de alçada.
 *
 * parametros: {"variacaoMinima": 10} — percentual mínimo de aumento real.
 */
public class AlertaVariacaoPrecoHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(AlertaVariacaoPrecoHandler.class.getName());

    /** Distingue este alerta do de ordem de compra na tabela de controle de envio. */
    public static final String TIPO = "VARIACAO DE PRECO";

    /** Usado quando o agendamento não define nada — o mesmo limite da consulta original. */
    private static final double VARIACAO_MINIMA_PADRAO = 10;

    /** Teto de mensagens por destinatário em um ciclo, pra uma enxurrada não virar spam. */
    private static final int MAX_MENSAGENS_POR_CICLO = 15;

    private final AlertaOcPendenteDAO controle = new AlertaOcPendenteDAO();
    private final VariacaoPrecoDAO erp = new VariacaoPrecoDAO();

    @Override
    public String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        double variacaoMinima = variacaoMinima(parametros);
        int totalAvisadas = 0;
        int semLogon = 0;
        List<String> falhas = new ArrayList<>();

        // Quem "recebe todas" leva o que os aprovadores deste agendamento
        // receberam, mesmo fora da alçada dele — por isso os itens de todos
        // vão sendo acumulados aqui (sem repetir) e só depois enviados.
        Map<String, Map<String, Object>> itensDeTodos = new LinkedHashMap<>();
        List<Map<String, Object>> copias = new ArrayList<>();

        for (Map<String, Object> destinatario : destinatarios) {
            if (Boolean.TRUE.equals(destinatario.get("copia"))) {
                copias.add(destinatario);
                continue;
            }
            Object idLogon = destinatario.get("idLogonErp");
            if (!(idLogon instanceof Number)) {
                semLogon++;
                LOG.warning("Alerta de variação de preço: " + destinatario.get("nome")
                        + " está sem o código de logon do ERP no cadastro — ignorado.");
                continue;
            }
            try {
                List<Map<String, Object>> itens = erp.buscarVariacoes(((Number) idLogon).intValue(), variacaoMinima);
                for (Map<String, Object> item : itens) itensDeTodos.putIfAbsent(chaveDoItem(item), item);
                totalAvisadas += avisar(destinatario, itens);
            } catch (Exception e) {
                // Falha de um destinatário não pode travar os demais.
                falhas.add(descreverFalha(destinatario, e));
                LOG.log(Level.SEVERE, "Erro no alerta de variação de preço para " + destinatario.get("nome"), e);
            }
        }

        for (Map<String, Object> copia : copias) {
            try {
                totalAvisadas += avisar(copia, new ArrayList<>(itensDeTodos.values()));
            } catch (Exception e) {
                falhas.add(descreverFalha(copia, e));
                LOG.log(Level.SEVERE, "Erro no alerta de variação de preço (cópia) para " + copia.get("nome"), e);
            }
        }

        if (!falhas.isEmpty()) {
            throw new RuntimeException(String.join(" | ", falhas));
        }
        if (semLogon > 0 && totalAvisadas == 0) {
            throw new IllegalStateException(semLogon + " destinatário(s) sem código de logon do ERP no cadastro de usuário.");
        }

        String resumo = totalAvisadas == 0
                ? "Nenhuma variação nova acima de " + FormatoMensagem.valor(variacaoMinima) + "%."
                : totalAvisadas + " item(ns) avisado(s).";
        return semLogon > 0 ? resumo + " " + semLogon + " destinatário(s) sem código de logon do ERP." : resumo;
    }

    private static double variacaoMinima(JsonObject parametros) {
        if (parametros == null || !parametros.has("variacaoMinima") || parametros.get("variacaoMinima").isJsonNull()) {
            return VARIACAO_MINIMA_PADRAO;
        }
        try {
            return parametros.get("variacaoMinima").getAsDouble();
        } catch (Exception e) {
            LOG.warning("variacaoMinima inválida no agendamento, usando " + VARIACAO_MINIMA_PADRAO + "%: " + e.getMessage());
            return VARIACAO_MINIMA_PADRAO;
        }
    }

    /** Um item é a dupla cotação + material — é o que não pode repetir. */
    private static String chaveDoItem(Map<String, Object> item) {
        return AlertaOcPendenteDAO.chave(TIPO, str(item.get("nr_cotacao")), str(item.get("cod_material")));
    }

    private static String descreverFalha(Map<String, Object> destinatario, Exception e) {
        String nome = str(destinatario.get("nome"));
        String telefone = str(destinatario.get("telefone"));
        String motivo = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (motivo.contains("\"exists\":false")) {
            motivo = "número sem conta de WhatsApp (confira o telefone no cadastro)";
        }
        return nome + (telefone.isEmpty() ? "" : " (" + telefone + ")") + ": " + motivo;
    }

    /** @return quantos itens foram avisados a este destinatário */
    private int avisar(Map<String, Object> destinatario, List<Map<String, Object>> itens) throws Exception {
        int idUsuario = ((Number) destinatario.get("id")).intValue();
        String nome = String.valueOf(destinatario.get("nome"));
        String telefone = String.valueOf(destinatario.get("telefone"));

        if (itens.isEmpty()) return 0;

        Set<String> jaEnviados = controle.jaEnviados(idUsuario);

        int enviadas = 0;
        for (Map<String, Object> item : itens) {
            String cotacao = str(item.get("nr_cotacao"));
            String material = str(item.get("cod_material"));

            if (jaEnviados.contains(AlertaOcPendenteDAO.chave(TIPO, cotacao, material))) continue;

            if (enviadas >= MAX_MENSAGENS_POR_CICLO) {
                LOG.info("Alerta de variação de preço para " + nome + ": limite de "
                        + MAX_MENSAGENS_POR_CICLO + " mensagens por ciclo atingido, o resto vai no próximo.");
                break;
            }

            EvolutionApiUtil.enviarTexto(telefone, montarMensagem(item));
            // Só marca depois do envio dar certo: se a Evolution API falhar, o
            // item continua "não avisado" e entra de novo no próximo ciclo.
            controle.registrarEnviado(idUsuario, TIPO, cotacao, material);
            enviadas++;
        }

        if (enviadas > 0) {
            LOG.info("Alerta de variação de preço: " + enviadas + " item(ns) avisado(s) para " + nome);
        }
        return enviadas;
    }

    /** Mesmo formato da aplicação que rodava fora do sistema. */
    private static String montarMensagem(Map<String, Object> item) {
        return "🚨 ALERTA DE VARIAÇÃO DE PREÇO 🚨\n\n"
             + "📦 *Material:* " + str(item.get("cod_material")) + "\n"
             + "📝 *Descrição:* " + str(item.get("descricao")) + "\n"
             + "🔢 *Cotação:* " + str(item.get("nr_cotacao")) + "\n"
             + "💲 *Preço Unitário:* R$ " + FormatoMensagem.valor(item.get("preco_unitario")) + "\n"
             + "📦 *Quantidade:* " + FormatoMensagem.quantidade(item.get("quantidade")) + "\n"
             + "🏢 *Fornecedor Atual:* " + str(item.get("nome")) + "\n"
             + "📊 *Qtde Última Compra:* " + FormatoMensagem.quantidade(item.get("qtade2")) + "\n"
             + "🗓️ *Data Última Compra:* " + FormatoMensagem.data(item.get("data_ultima_compra")) + "\n"
             + "🏭 *Fornecedor Anterior:* " + str(item.get("razao_social_ultima_compra")) + "\n"
             + "📈 *Variação:* " + FormatoMensagem.valor(item.get("variacao")) + "%\n"
             + "📝 *Observação para o aprovador:* " + str(item.get("observacaoparaaprovador"));
    }

    private static String str(Object v) {
        return FormatoMensagem.texto(v);
    }
}
