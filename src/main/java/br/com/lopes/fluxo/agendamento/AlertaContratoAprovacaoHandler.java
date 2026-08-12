package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.ContratoAprovacaoDAO;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * tipo_relatorio = "contrato_aprovacao" — avisa por WhatsApp os contratos que
 * ainda não passaram por nenhuma aprovação.
 *
 * Diferente dos alertas de contrato a vencer e de divergência, este é POR
 * PESSOA: a consulta pergunta ao ERP o que aquele usuário pode aprovar, e por
 * isso cada destinatário recebe a sua lista. Quem não tem id_logon_erp
 * cadastrado não tem como ser consultado e é pulado, com o motivo no log.
 *
 * Cada contrato é avisado uma única vez, por destinatário, usando a mesma
 * tabela de controle dos demais alertas ({@link AlertaOcPendenteDAO}), com
 * {@link #TIPO} separando-os. A chave é contrato + data de início: uma
 * vigência nova do mesmo contrato é um aviso novo, porque é outra aprovação.
 *
 * parametros: {"diasCriacao": 30, "funcaprovacao": 0}
 */
public class AlertaContratoAprovacaoHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(AlertaContratoAprovacaoHandler.class.getName());

    public static final String TIPO = "CONTRATO APROVACAO";

    /** Janela da data de criação. Sem ela, a primeira execução varre o histórico inteiro. */
    private static final int DIAS_CRIACAO_PADRAO = 30;

    /** Teto de mensagens por destinatário em um ciclo, pra uma enxurrada não virar spam. */
    private static final int MAX_MENSAGENS_POR_CICLO = 15;

    private final AlertaOcPendenteDAO controle = new AlertaOcPendenteDAO();
    private final ContratoAprovacaoDAO erp = new ContratoAprovacaoDAO();

    @Override
    public String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        int diasCriacao   = inteiro(parametros, "diasCriacao", DIAS_CRIACAO_PADRAO, 1);
        int funcaprovacao = inteiro(parametros, "funcaprovacao", 0, 0);

        int totalAvisados = 0, semLogon = 0;
        List<String> falhas = new ArrayList<>();

        for (Map<String, Object> destinatario : destinatarios) {
            Object idLogon = destinatario.get("idLogonErp");
            if (!(idLogon instanceof Number) || ((Number) idLogon).intValue() <= 0) {
                // Sem o id do ERP não há como perguntar o que essa pessoa
                // aprova. Avisar todos os contratos seria pior que não avisar.
                LOG.info("Alerta de contrato para aprovação: " + destinatario.get("nome")
                        + " está sem id_logon_erp cadastrado e foi pulado.");
                semLogon++;
                continue;
            }
            try {
                List<Map<String, Object>> contratos =
                        erp.buscarSemAprovacao(((Number) idLogon).intValue(), diasCriacao, funcaprovacao);
                totalAvisados += avisar(destinatario, contratos);
            } catch (Exception e) {
                // Falha de um destinatário não pode travar os demais.
                falhas.add(descreverFalha(destinatario, e));
                LOG.log(Level.SEVERE, "Erro no alerta de contrato para aprovação de " + destinatario.get("nome"), e);
            }
        }

        if (!falhas.isEmpty()) {
            throw new RuntimeException(String.join(" | ", falhas));
        }
        if (totalAvisados > 0) return totalAvisados + " contrato(s) avisado(s).";
        return semLogon > 0
                ? "Nenhum contrato novo para aprovação (" + semLogon + " destinatário(s) sem id_logon_erp)."
                : "Nenhum contrato novo para aprovação.";
    }

    /** Contrato + início da vigência: vigência nova é aprovação nova. */
    private static String chave(Map<String, Object> c) {
        return AlertaOcPendenteDAO.chave(TIPO, txt(c.get("numerocontrato")), txt(c.get("datainicio")));
    }

    /** @return quantos contratos foram avisados a este destinatário */
    private int avisar(Map<String, Object> destinatario, List<Map<String, Object>> contratos) throws Exception {
        int idUsuario = ((Number) destinatario.get("id")).intValue();
        String nome = String.valueOf(destinatario.get("nome"));
        String telefone = String.valueOf(destinatario.get("telefone"));

        Set<String> jaEnviados = controle.jaEnviados(idUsuario);

        int enviados = 0;
        for (Map<String, Object> contrato : contratos) {
            if (jaEnviados.contains(chave(contrato))) continue;

            if (enviados >= MAX_MENSAGENS_POR_CICLO) {
                LOG.info("Alerta de contrato para aprovação de " + nome + ": limite de "
                        + MAX_MENSAGENS_POR_CICLO + " mensagens por ciclo atingido, o resto vai no próximo.");
                break;
            }

            EvolutionApiUtil.enviarTexto(telefone, montarMensagem(contrato));
            // Só marca depois do envio dar certo: se a Evolution API falhar, o
            // contrato continua "não avisado" e entra de novo no próximo ciclo.
            controle.registrarEnviado(idUsuario, TIPO, txt(contrato.get("numerocontrato")),
                                      txt(contrato.get("datainicio")));
            enviados++;
        }

        if (enviados > 0) {
            LOG.info("Alerta de contrato para aprovação: " + enviados + " contrato(s) avisado(s) para " + nome);
        }
        return enviados;
    }

    private static String montarMensagem(Map<String, Object> c) {
        return "📄 *CONTRATO AGUARDANDO APROVAÇÃO* 📄\n\n"
             + "🔢 *Contrato:* " + txt(c.get("numerocontrato")) + "\n"
             + "🏢 *Fornecedor:* " + txt(c.get("nome_fornecedor")) + "\n"
             + "📝 *Descrição:* " + txt(c.get("descricaoresumida")) + "\n"
             + "📁 *Tipo:* " + txt(c.get("desc_tipocontrato")) + "\n"
             + "📅 *Vigência:* " + FormatoMensagem.data(c.get("datainicio"))
             + " a " + FormatoMensagem.data(c.get("datatermino")) + "\n"
             + "💰 *Valor total:* R$ " + FormatoMensagem.valor(c.get("valor_total_ctr")) + "\n"
             + "🔁 *Parcelas:* " + txt(c.get("qtdeparcelas")) + " (" + txt(c.get("fixovariavel")) + ")\n"
             + "🎯 *Objeto de custo:* " + txt(c.get("desc_objetocusto")) + "\n"
             + "📌 *Empenho:* " + txt(c.get("desc_empenho")) + "\n"
             + "🧑‍💼 *Autorizante:* " + txt(c.get("nome_autorizante")) + "\n\n"
             + "⚠️ Este contrato ainda não passou por nenhuma aprovação.";
    }

    private static String descreverFalha(Map<String, Object> destinatario, Exception e) {
        String nome = txt(destinatario.get("nome"));
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return nome + ": " + msg;
    }

    /** Lê um inteiro dos parâmetros, recusando valor abaixo do mínimo aceitável. */
    private static int inteiro(JsonObject parametros, String campo, int padrao, int minimo) {
        if (parametros == null || !parametros.has(campo) || parametros.get(campo).isJsonNull()) {
            return padrao;
        }
        try {
            int v = parametros.get(campo).getAsInt();
            if (v < minimo) {
                LOG.warning(campo + "=" + v + " no agendamento é inválido; usando " + padrao + ".");
                return padrao;
            }
            return v;
        } catch (Exception e) {
            LOG.warning(campo + " inválido no agendamento, usando " + padrao + ": " + e.getMessage());
            return padrao;
        }
    }

    private static String txt(Object v) {
        return v == null ? "-" : String.valueOf(v).trim();
    }
}
