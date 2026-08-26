package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.SolicitacaoEstouradaDAO;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * tipo_relatorio = "solicitacao_estourada" — avisa por WhatsApp as SOLICITAÇÕES
 * de compra em estouro de orçamento aguardando aprovação (estágio anterior à
 * cotação, tratado por {@link AlertaOrcamentoEstouradoHandler}).
 *
 * Recorrente: a cada ciclo consulta o ERP e manda uma mensagem por solicitação
 * nova. Assim que a solicitação é aprovada (solicitacaoaprovada deixa de ser
 * 'F') ou cancelada, ela sai da consulta e para de aparecer.
 *
 * Sem alçada por destinatário: a consulta não filtra por aprovador, então todos
 * os telefones do agendamento recebem a mesma lista; o que muda é só o que cada
 * um já viu. Controle de não repetir em {@link AlertaOcPendenteDAO}, com
 * {@link #TIPO} separando na tabela e a chave sendo solicitação + material.
 */
public class AlertaSolicitacaoEstouradaHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(AlertaSolicitacaoEstouradaHandler.class.getName());

    /** Distingue este alerta dos demais na tabela de controle de envio. */
    public static final String TIPO = "SOLICITACAO ESTOURADA";

    /** Teto de mensagens por destinatário em um ciclo, pra uma enxurrada não virar spam. */
    private static final int MAX_MENSAGENS_POR_CICLO = 15;

    private final AlertaOcPendenteDAO controle = new AlertaOcPendenteDAO();
    private final SolicitacaoEstouradaDAO erp = new SolicitacaoEstouradaDAO();

    @Override
    public String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        List<Map<String, Object>> estouros = erp.buscar();
        if (estouros.isEmpty()) {
            return "Nenhuma solicitação em estouro pendente.";
        }

        int totalAvisados = 0;
        List<String> falhas = new ArrayList<>();

        for (Map<String, Object> destinatario : destinatarios) {
            try {
                totalAvisados += avisar(destinatario, estouros);
            } catch (Exception e) {
                falhas.add(descreverFalha(destinatario, e));
                LOG.log(Level.SEVERE, "Erro no alerta de solicitação em estouro para " + destinatario.get("nome"), e);
            }
        }

        if (!falhas.isEmpty()) {
            throw new RuntimeException(String.join(" | ", falhas));
        }
        return totalAvisados == 0
                ? "Nenhuma solicitação nova."
                : totalAvisados + " solicitação(ões) avisada(s).";
    }

    /** @return quantas solicitações foram avisadas a este destinatário */
    private int avisar(Map<String, Object> destinatario, List<Map<String, Object>> estouros) throws Exception {
        int idUsuario = ((Number) destinatario.get("id")).intValue();
        String nome = String.valueOf(destinatario.get("nome"));
        String telefone = String.valueOf(destinatario.get("telefone"));

        Set<String> jaEnviados = controle.jaEnviados(idUsuario);

        int enviados = 0;
        for (Map<String, Object> estouro : estouros) {
            String nrSolicitacao = txt(estouro.get("nr_solicitacao"));
            String item = txt(estouro.get("cod_material"));
            if (jaEnviados.contains(AlertaOcPendenteDAO.chave(TIPO, nrSolicitacao, item))) continue;

            if (enviados >= MAX_MENSAGENS_POR_CICLO) {
                LOG.info("Alerta de solicitação em estouro para " + nome + ": limite de "
                        + MAX_MENSAGENS_POR_CICLO + " mensagens por ciclo atingido, o resto vai no próximo.");
                break;
            }

            EvolutionApiUtil.enviarTexto(telefone, montarMensagem(estouro));
            // Só marca depois do envio dar certo: se a Evolution API falhar, a
            // solicitação continua "não avisada" e entra de novo no próximo ciclo.
            controle.registrarEnviado(idUsuario, TIPO, nrSolicitacao, item);
            enviados++;
        }

        if (enviados > 0) {
            LOG.info("Alerta de solicitação em estouro: " + enviados + " solicitação(ões) avisada(s) para " + nome);
        }
        return enviados;
    }

    private static String montarMensagem(Map<String, Object> e) {
        StringBuilder msg = new StringBuilder();
        msg.append("🚨 SOLICITAÇÃO EM ESTOURO DE ORÇAMENTO — AGUARDANDO APROVAÇÃO 🚨\n\n")
           .append("📄 Solicitação: ").append(txt(e.get("nr_solicitacao"))).append("\n")
           .append("📦 Material: ").append(txt(e.get("descmaterial")))
               .append(" (").append(txt(e.get("cod_material"))).append(")\n")
           .append("🔢 Qtde solicitada: ").append(FormatoMensagem.quantidade(e.get("qtdesolicitada")))
               .append(" ").append(txt(e.get("cod_unidade"))).append("\n");

        String prioridade = txt(e.get("prioridade"));
        if (!prioridade.isBlank()) msg.append("⭐ Prioridade: ").append(prioridade).append("\n");

        msg.append("🧑 Solicitante: ").append(txt(e.get("nome"))).append("\n");

        String classificacao = txt(e.get("classificacao"));
        if (!classificacao.isBlank()) msg.append("🏷️ Classificação: ").append(classificacao).append("\n");

        String descobjeto = txt(e.get("descobjeto"));
        if (!descobjeto.isBlank()) msg.append("🎯 Objeto de custo: ").append(descobjeto).append("\n");

        String almox = txt(e.get("descricaoalmoxarifado"));
        if (!almox.isBlank()) msg.append("🏬 Almoxarifado: ").append(almox).append("\n");

        String estouradoEm = FormatoMensagem.data(e.get("data_estouro"));
        String usuario = txt(e.get("usuario"));
        if (!estouradoEm.isBlank() || !usuario.isBlank()) {
            msg.append("📅 Estouro em: ").append(estouradoEm.isBlank() ? "—" : estouradoEm);
            if (!usuario.isBlank()) msg.append(" por ").append(usuario);
            msg.append("\n");
        }

        msg.append("======================");
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
