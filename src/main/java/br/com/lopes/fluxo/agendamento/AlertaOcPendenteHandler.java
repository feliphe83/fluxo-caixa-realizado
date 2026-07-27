package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.OrdemCompraPendenteDAO;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
import com.google.gson.JsonObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * tipo_relatorio = "oc_pendente" — avisa por WhatsApp quem tem ordem de
 * compra esperando aprovação, substituindo a aplicação que fazia isso num
 * agendador do Windows.
 *
 * É um agendamento recorrente (intervalo em minutos, não dia/hora): a cada
 * ciclo consulta as ordens pendentes de cada destinatário no ERP e manda
 * uma mensagem por ordem NOVA. "Nova" é a que ainda não foi avisada àquela
 * pessoa — enquanto a ordem seguir pendente ela continua voltando na
 * consulta, mas não é reenviada (ver {@link AlertaOcPendenteDAO}).
 *
 * Cada destinatário precisa do "Código de logon no ERP" preenchido no
 * cadastro de usuário: é ele que diz quais ordens são daquele aprovador.
 * Quem não tiver é ignorado (com aviso no log), porque não há como saber o
 * que mandar.
 *
 * parametros: nenhum — o que varia (intervalo e destinatários) está nas
 * colunas do próprio agendamento.
 */
public class AlertaOcPendenteHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(AlertaOcPendenteHandler.class.getName());

    /** Teto de mensagens por destinatário em um ciclo, pra uma enxurrada de ordens novas não virar spam. */
    private static final int MAX_MENSAGENS_POR_CICLO = 15;

    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
    private static final NumberFormat QTDE = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));

    private final AlertaOcPendenteDAO controle = new AlertaOcPendenteDAO();
    private final OrdemCompraPendenteDAO erp = new OrdemCompraPendenteDAO();

    @Override
    public String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        int totalAvisadas = 0;
        int semLogon = 0;
        Exception ultimaFalha = null;

        for (Map<String, Object> destinatario : destinatarios) {
            Object idLogon = destinatario.get("idLogonErp");
            if (!(idLogon instanceof Number)) {
                semLogon++;
                LOG.warning("Alerta de ordem de compra: " + destinatario.get("nome")
                        + " está sem o código de logon do ERP no cadastro — ignorado.");
                continue;
            }
            try {
                totalAvisadas += avisar(destinatario, ((Number) idLogon).intValue());
            } catch (Exception e) {
                // Falha de um destinatário não pode travar os demais.
                ultimaFalha = e;
                LOG.log(Level.SEVERE, "Erro no alerta de ordem de compra para " + destinatario.get("nome"), e);
            }
        }

        if (ultimaFalha != null) {
            throw new RuntimeException("Falha em ao menos um destinatário: " + ultimaFalha.getMessage(), ultimaFalha);
        }
        if (semLogon > 0 && totalAvisadas == 0) {
            throw new IllegalStateException(semLogon + " destinatário(s) sem código de logon do ERP no cadastro de usuário.");
        }

        String resumo = totalAvisadas == 0
                ? "Nenhuma ordem nova."
                : totalAvisadas + " ordem(ns) avisada(s).";
        return semLogon > 0 ? resumo + " " + semLogon + " destinatário(s) sem código de logon do ERP." : resumo;
    }

    /** @return quantas ordens foram avisadas a este destinatário */
    private int avisar(Map<String, Object> destinatario, int idLogon) throws Exception {
        int idUsuario = ((Number) destinatario.get("id")).intValue();
        String nome = String.valueOf(destinatario.get("nome"));
        String telefone = String.valueOf(destinatario.get("telefone"));

        List<Map<String, Object>> itens = erp.buscarPendentes(idLogon);
        if (itens.isEmpty()) return 0;

        Set<String> jaEnviados = controle.jaEnviados(idUsuario);

        int enviadas = 0;
        for (Map.Entry<String, List<Map<String, Object>>> ordem : agruparPorOrdem(itens).entrySet()) {
            List<Map<String, Object>> itensDaOrdem = ordem.getValue();
            String tipo = str(itensDaOrdem.get(0).get("tipo"));
            String nrSolicitacao = str(itensDaOrdem.get(0).get("nr_solicitacao"));

            if (jaEnviados.contains(AlertaOcPendenteDAO.chave(tipo, nrSolicitacao))) continue;

            if (enviadas >= MAX_MENSAGENS_POR_CICLO) {
                LOG.info("Alerta de ordem de compra para " + nome + ": limite de "
                        + MAX_MENSAGENS_POR_CICLO + " mensagens por ciclo atingido, o resto vai no próximo.");
                break;
            }

            EvolutionApiUtil.enviarTexto(telefone, montarMensagem(itensDaOrdem));
            // Só marca depois do envio dar certo: se a Evolution API falhar, a
            // ordem continua "não avisada" e entra de novo no próximo ciclo.
            controle.registrarEnviado(idUsuario, tipo, nrSolicitacao);
            enviadas++;
        }

        if (enviadas > 0) {
            LOG.info("Alerta de ordem de compra: " + enviadas + " ordem(ns) avisada(s) para " + nome);
        }
        return enviadas;
    }

    /** Uma ordem pode ter vários itens (uma linha por material) — vira uma mensagem só. */
    private static Map<String, List<Map<String, Object>>> agruparPorOrdem(List<Map<String, Object>> itens) {
        Map<String, List<Map<String, Object>>> porOrdem = new LinkedHashMap<>();
        for (Map<String, Object> item : itens) {
            String chave = AlertaOcPendenteDAO.chave(str(item.get("tipo")), str(item.get("nr_solicitacao")));
            porOrdem.computeIfAbsent(chave, k -> new ArrayList<>()).add(item);
        }
        return porOrdem;
    }

    private static String montarMensagem(List<Map<String, Object>> itens) {
        Map<String, Object> primeiro = itens.get(0);
        StringBuilder msg = new StringBuilder();

        msg.append("*").append(str(primeiro.get("tipo"))).append(" aguardando sua aprovação*\n\n");
        msg.append("*Solicitação:* ").append(str(primeiro.get("nr_solicitacao"))).append("\n");

        String fornecedor = str(primeiro.get("nome"));
        if (!fornecedor.isBlank()) msg.append("*Fornecedor:* ").append(fornecedor).append("\n");

        String objetoCusto = str(primeiro.get("desc_objetocusto"));
        if (!objetoCusto.isBlank()) msg.append("*Objeto de Custo:* ").append(objetoCusto).append("\n");

        msg.append("\n*").append(itens.size() > 1 ? "Itens:" : "Item:").append("*\n");
        double total = 0;
        for (Map<String, Object> item : itens) {
            double precoTotal = num(item.get("precototal"));
            total += precoTotal;
            msg.append("• ").append(str(item.get("cod_material")))
               .append(" — ").append(QTDE.format(num(item.get("quantidade"))))
               .append(" ").append(str(item.get("cod_unidade")))
               .append(" × ").append(MOEDA.format(num(item.get("preco_unitario"))))
               .append(" = ").append(MOEDA.format(precoTotal))
               .append("\n");
        }

        msg.append("\n*Total:* ").append(MOEDA.format(total));

        String observacao = str(primeiro.get("observacao"));
        if (!observacao.isBlank()) msg.append("\n\n_").append(observacao).append("_");

        return msg.toString();
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}
