package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.util.ChromiumPdfUtil;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * tipo_relatorio = "combustivel" — gera o mesmo relatório de
 * combustivel-dashboard.html (agora com o layout ajustado pro PDF) e manda
 * por WhatsApp pra cada destinatário.
 *
 * parametros esperado: {"dataIni": "yyyy-MM-dd", "combustivel": "Diesel"} —
 * data inicial fixa (o início da safra, como na tela) até ONTEM. O dia
 * corrente fica de fora porque ainda está em andamento (abastecimentos
 * acontecendo), o que traria uma última semana parcial no relatório enviado.
 *
 * Agendamentos criados na versão anterior, com {"semanas": N} em vez de
 * dataIni, continuam válidos: a data inicial vira dataFim - (N*7-1) dias.
 */
public class CombustivelRelatorioAgendadoHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(CombustivelRelatorioAgendadoHandler.class.getName());
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static String baseUrlInterno() {
        String v = System.getenv("APP_BASE_URL_INTERNO");
        return (v == null || v.isBlank()) ? "http://127.0.0.1:8080/fluxo-caixa" : v;
    }

    @Override
    public void executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        String combustivel = parametros.has("combustivel") ? parametros.get("combustivel").getAsString() : "Diesel";

        LocalDate dataFim = LocalDate.now().minusDays(1);
        LocalDate dataIni = dataInicial(parametros, dataFim);

        // O Chromium abre o endpoint de sessão, guarda o cookie JSESSIONID e
        // é redirecionado pro dashboard — assim os fetch() da página também
        // saem autenticados (ver SessaoRelatorioServlet).
        String destino = "combustivel-dashboard.html?dataIni=" + dataIni.format(ISO)
                + "&dataFim=" + dataFim.format(ISO)
                + "&combustivel=" + URLEncoder.encode(combustivel, StandardCharsets.UTF_8);
        String url = baseUrlInterno() + "/api/interno/sessao-relatorio?idUsuario=" + idUsuarioCriacao
                + "&redirect=" + URLEncoder.encode(destino, StandardCharsets.UTF_8);

        LOG.info("Gerando PDF do relatório de combustível: " + url);
        byte[] pdf = ChromiumPdfUtil.gerarPdf(url);

        String legenda = "Relatório Executivo — Consumo de " + combustivel
                + " (" + dataIni.format(BR) + " a " + dataFim.format(BR) + "), gerado automaticamente.";

        Exception ultimaFalha = null;
        int falhas = 0;
        for (Map<String, Object> destinatario : destinatarios) {
            String telefone = String.valueOf(destinatario.get("telefone"));
            try {
                EvolutionApiUtil.enviarDocumento(telefone, pdf, "relatorio-combustivel.pdf", legenda);
            } catch (Exception e) {
                falhas++;
                ultimaFalha = e;
                LOG.log(Level.SEVERE, "Falha ao enviar relatório de combustível pro destinatário "
                        + destinatario.get("nome") + " (" + telefone + ")", e);
            }
        }
        if (falhas > 0 && falhas == destinatarios.size()) {
            throw new RuntimeException("Falha ao enviar para todos os " + falhas + " destinatários: "
                    + (ultimaFalha == null ? "" : ultimaFalha.getMessage()), ultimaFalha);
        }
        if (falhas > 0) {
            throw new RuntimeException(falhas + " de " + destinatarios.size()
                    + " envios falharam (os demais foram entregues): "
                    + (ultimaFalha == null ? "" : ultimaFalha.getMessage()), ultimaFalha);
        }
    }

    /**
     * Data inicial do relatório: normalmente uma data fixa configurada no
     * agendamento (parametros.dataIni, ex.: o início da safra 02/03/2026 —
     * mesmo padrão da tela), pra que o relatório enviado cubra sempre a safra
     * inteira até ontem. Agendamentos antigos, criados quando o campo era
     * "últimas N semanas", continuam funcionando pelo cálculo relativo.
     */
    private static LocalDate dataInicial(JsonObject parametros, LocalDate dataFim) {
        if (parametros.has("dataIni") && !parametros.get("dataIni").isJsonNull()) {
            String v = parametros.get("dataIni").getAsString();
            if (!v.isBlank()) return LocalDate.parse(v.trim());
        }
        int semanas = parametros.has("semanas") ? parametros.get("semanas").getAsInt() : 26;
        return dataFim.minusDays(semanas * 7L - 1);
    }

}
