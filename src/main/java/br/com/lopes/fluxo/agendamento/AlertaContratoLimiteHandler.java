package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.ContratoLimiteDAO;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * tipo_relatorio = "contrato_limite" — avisa por WhatsApp os contratos que já
 * consumiram boa parte do valor contratado (por padrão, 70%).
 *
 * É um agendamento recorrente (intervalo em minutos): a cada ciclo consulta o
 * ERP e manda uma mensagem por contrato novo. Como no alerta de divergência
 * de nota, não há alçada — a consulta não filtra por aprovador, então todo
 * destinatário do agendamento recebe a mesma lista e ninguém precisa de
 * código de logon do ERP.
 *
 * O controle de não repetir é o mesmo dos outros alertas
 * ({@link AlertaOcPendenteDAO}), com {@link #TIPO} separando-os na tabela. A
 * chave é o número do contrato mais a FAIXA de consumo em que ele está
 * (70, 80, 90, 100): o contrato avisa ao cruzar os 70% e volta a avisar a
 * cada dez pontos daí em diante, em vez de silenciar para sempre depois do
 * primeiro aviso. São no máximo quatro mensagens por contrato.
 *
 * parametros: {"percentualMinimo": 70, "dataVcto": "2026-03-01"} — o
 * percentual a partir do qual alerta e a data mínima de vencimento das
 * parcelas consideradas.
 */
public class AlertaContratoLimiteHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(AlertaContratoLimiteHandler.class.getName());

    /** Distingue este alerta dos demais na tabela de controle de envio. */
    public static final String TIPO = "CONTRATO LIMITE";

    /** Usados quando o agendamento não define nada — os mesmos da consulta original. */
    private static final double PERCENTUAL_MINIMO_PADRAO = 70;
    private static final LocalDate DATA_VCTO_PADRAO = LocalDate.of(2026, 3, 1);

    /** Teto de mensagens por destinatário em um ciclo, pra uma enxurrada não virar spam. */
    private static final int MAX_MENSAGENS_POR_CICLO = 15;

    private final AlertaOcPendenteDAO controle = new AlertaOcPendenteDAO();
    private final ContratoLimiteDAO erp = new ContratoLimiteDAO();

    @Override
    public String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        double percentualMinimo = percentualMinimo(parametros);
        LocalDate dataVcto = dataVcto(parametros);

        // Uma consulta só para todos: sem alçada, a lista é a mesma para
        // qualquer destinatário — o que muda é apenas o que cada um já viu.
        List<Map<String, Object>> contratos = erp.buscarNoLimite(dataVcto, percentualMinimo);
        if (contratos.isEmpty()) {
            return "Nenhum contrato acima de " + FormatoMensagem.valor(percentualMinimo) + "%.";
        }

        int totalAvisados = 0;
        List<String> falhas = new ArrayList<>();

        for (Map<String, Object> destinatario : destinatarios) {
            try {
                totalAvisados += avisar(destinatario, contratos, percentualMinimo);
            } catch (Exception e) {
                // Falha de um destinatário não pode travar os demais.
                falhas.add(descreverFalha(destinatario, e));
                LOG.log(Level.SEVERE, "Erro no alerta de contrato para " + destinatario.get("nome"), e);
            }
        }

        if (!falhas.isEmpty()) {
            throw new RuntimeException(String.join(" | ", falhas));
        }
        return totalAvisados == 0
                ? "Nenhum contrato novo acima de " + FormatoMensagem.valor(percentualMinimo) + "%."
                : totalAvisados + " contrato(s) avisado(s).";
    }

    private static double percentualMinimo(JsonObject parametros) {
        if (parametros == null || !parametros.has("percentualMinimo") || parametros.get("percentualMinimo").isJsonNull()) {
            return PERCENTUAL_MINIMO_PADRAO;
        }
        try {
            return parametros.get("percentualMinimo").getAsDouble();
        } catch (Exception e) {
            LOG.warning("percentualMinimo inválido no agendamento, usando " + PERCENTUAL_MINIMO_PADRAO + "%: " + e.getMessage());
            return PERCENTUAL_MINIMO_PADRAO;
        }
    }

    private static LocalDate dataVcto(JsonObject parametros) {
        if (parametros == null || !parametros.has("dataVcto") || parametros.get("dataVcto").isJsonNull()) {
            return DATA_VCTO_PADRAO;
        }
        try {
            return LocalDate.parse(parametros.get("dataVcto").getAsString());
        } catch (Exception e) {
            LOG.warning("dataVcto inválida no agendamento, usando " + DATA_VCTO_PADRAO + ": " + e.getMessage());
            return DATA_VCTO_PADRAO;
        }
    }

    /**
     * Faixa de dez em dez a partir do mínimo configurado: 70-79 → 70,
     * 80-89 → 80, e tudo acima de 100 cai em 100. É o que entra na chave de
     * controle, para o contrato voltar a avisar quando avança de faixa.
     */
    private static int faixa(double percentual, double percentualMinimo) {
        if (percentual >= 100) return 100;
        int f = (int) (percentual / 10) * 10;
        return Math.max(f, (int) percentualMinimo);
    }

    /** @return quantos contratos foram avisados a este destinatário */
    private int avisar(Map<String, Object> destinatario, List<Map<String, Object>> contratos,
                       double percentualMinimo) throws Exception {
        int idUsuario = ((Number) destinatario.get("id")).intValue();
        String nome = String.valueOf(destinatario.get("nome"));
        String telefone = String.valueOf(destinatario.get("telefone"));

        Set<String> jaEnviados = controle.jaEnviados(idUsuario);

        int enviados = 0;
        for (Map<String, Object> contrato : contratos) {
            String numero = txt(contrato.get("numerocontrato"));
            String faixa = String.valueOf(faixa(FormatoMensagem.numero(contrato.get("percentual_restante")), percentualMinimo));

            if (jaEnviados.contains(AlertaOcPendenteDAO.chave(TIPO, numero, faixa))) continue;

            if (enviados >= MAX_MENSAGENS_POR_CICLO) {
                LOG.info("Alerta de contrato para " + nome + ": limite de "
                        + MAX_MENSAGENS_POR_CICLO + " mensagens por ciclo atingido, o resto vai no próximo.");
                break;
            }

            EvolutionApiUtil.enviarTexto(telefone, montarMensagem(contrato, percentualMinimo));
            // Só marca depois do envio dar certo: se a Evolution API falhar, o
            // contrato continua "não avisado" e entra de novo no próximo ciclo.
            controle.registrarEnviado(idUsuario, TIPO, numero, faixa);
            enviados++;
        }

        if (enviados > 0) {
            LOG.info("Alerta de contrato: " + enviados + " contrato(s) avisado(s) para " + nome);
        }
        return enviados;
    }

    /** Mesmo formato da aplicação que rodava fora do sistema. */
    private static String montarMensagem(Map<String, Object> contrato, double percentualMinimo) {
        return "🚨 *ALERTA DE CONTRATO*\n\n"
             + "Contrato: *" + txt(contrato.get("numerocontrato")) + "*\n"
             + "Fornecedor: " + txt(contrato.get("nome")) + "\n"
             + "Descrição: " + txt(contrato.get("descricaoresumida")) + "\n\n"
             + "Início do contrato: " + FormatoMensagem.data(contrato.get("datainicio")) + "\n"
             + "Término do contrato: " + FormatoMensagem.data(contrato.get("datatermino")) + "\n\n"
             + "Total Pago: R$ " + FormatoMensagem.valor(contrato.get("total_pago")) + "\n"
             + "Valor Total: R$ " + FormatoMensagem.valor(contrato.get("valor_total")) + "\n"
             + "Falta: R$ " + FormatoMensagem.valor(contrato.get("diferenca")) + "\n"
             + "*Percentual já utilizado:* " + FormatoMensagem.valor(contrato.get("percentual_restante")) + "%\n\n"
             + "⚠ O valor do contrato já ultrapassou " + (int) percentualMinimo + "% do valor total!";
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
