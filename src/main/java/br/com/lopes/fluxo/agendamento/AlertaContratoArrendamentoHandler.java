package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.ContratoArrendamentoDAO;
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
 * tipo_relatorio = "contrato_vencendo" — avisa por WhatsApp os contratos de
 * arrendamento que vencem nos próximos 90 dias.
 *
 * É um agendamento recorrente (intervalo em minutos): a cada ciclo consulta o
 * ERP e manda uma mensagem por contrato novo. Como nos alertas de divergência
 * de nota e de contrato no limite, não há alçada — a consulta não filtra por
 * aprovador, então todo destinatário recebe a mesma lista e ninguém precisa
 * de código de logon do ERP.
 *
 * O controle de não repetir é o mesmo dos outros alertas
 * ({@link AlertaOcPendenteDAO}), com {@link #TIPO} separando-os na tabela. A
 * chave é o número do contrato mais a data de término: o contrato avisa uma
 * vez ao entrar na janela dos 90 dias e só volta a avisar se a vigência for
 * renovada com um novo término.
 *
 * parametros: nenhum.
 */
public class AlertaContratoArrendamentoHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(AlertaContratoArrendamentoHandler.class.getName());

    /** Distingue este alerta dos demais na tabela de controle de envio. */
    public static final String TIPO = "CONTRATO ARRENDAMENTO";

    /** Teto de mensagens por destinatário em um ciclo, pra uma enxurrada não virar spam. */
    private static final int MAX_MENSAGENS_POR_CICLO = 15;

    private final AlertaOcPendenteDAO controle = new AlertaOcPendenteDAO();
    private final ContratoArrendamentoDAO erp = new ContratoArrendamentoDAO();

    @Override
    public String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        // Uma consulta só para todos: sem alçada, a lista é a mesma para
        // qualquer destinatário — o que muda é apenas o que cada um já viu.
        List<Map<String, Object>> contratos = consolidar(erp.buscarAVencer());
        if (contratos.isEmpty()) return "Nenhum contrato de arrendamento a vencer.";

        int totalAvisados = 0;
        List<String> falhas = new ArrayList<>();

        for (Map<String, Object> destinatario : destinatarios) {
            try {
                totalAvisados += avisar(destinatario, contratos);
            } catch (Exception e) {
                // Falha de um destinatário não pode travar os demais.
                falhas.add(descreverFalha(destinatario, e));
                LOG.log(Level.SEVERE, "Erro no alerta de contrato de arrendamento para " + destinatario.get("nome"), e);
            }
        }

        if (!falhas.isEmpty()) {
            throw new RuntimeException(String.join(" | ", falhas));
        }
        return totalAvisados == 0
                ? "Nenhum contrato de arrendamento novo a vencer."
                : totalAvisados + " contrato(s) avisado(s).";
    }

    /**
     * Uma linha por contrato. A consulta faz outer join com historicocontrato
     * e pode devolver o mesmo contrato várias vezes (uma por histórico
     * vigente); como nenhuma coluna do histórico entra na mensagem, as
     * repetições virariam mensagens idênticas.
     */
    private static List<Map<String, Object>> consolidar(List<Map<String, Object>> linhas) {
        Map<String, Map<String, Object>> porContrato = new LinkedHashMap<>();
        for (Map<String, Object> l : linhas) porContrato.putIfAbsent(chave(l), l);
        return new ArrayList<>(porContrato.values());
    }

    /** Contrato + término: uma vigência renovada é um aviso novo. */
    private static String chave(Map<String, Object> contrato) {
        return AlertaOcPendenteDAO.chave(TIPO, txt(contrato.get("numerocontrato")),
                                         txt(contrato.get("datatermino")));
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
                LOG.info("Alerta de contrato de arrendamento para " + nome + ": limite de "
                        + MAX_MENSAGENS_POR_CICLO + " mensagens por ciclo atingido, o resto vai no próximo.");
                break;
            }

            EvolutionApiUtil.enviarTexto(telefone, montarMensagem(contrato));
            // Só marca depois do envio dar certo: se a Evolution API falhar, o
            // contrato continua "não avisado" e entra de novo no próximo ciclo.
            controle.registrarEnviado(idUsuario, TIPO, txt(contrato.get("numerocontrato")),
                                      txt(contrato.get("datatermino")));
            enviados++;
        }

        if (enviados > 0) {
            LOG.info("Alerta de contrato de arrendamento: " + enviados + " contrato(s) avisado(s) para " + nome);
        }
        return enviados;
    }

    /** Mesmo formato da aplicação que rodava fora do sistema. */
    private static String montarMensagem(Map<String, Object> contrato) {
        return "🔹 *Contrato N°*: " + txt(contrato.get("numerocontrato")) + "\n"
             + "🏢 *Fornecedor*: " + txt(contrato.get("fornecedor")) + "\n"
             + "📝 *Descrição*: " + txt(contrato.get("descricaoresumida")) + "\n"
             + "📅 *Vencimento*: " + FormatoMensagem.data(contrato.get("datatermino")) + "\n"
             + "⏳ *Dias para vencer*: " + FormatoMensagem.quantidade(contrato.get("diasparavencer")) + "\n"
             + "--------------------------------";
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
