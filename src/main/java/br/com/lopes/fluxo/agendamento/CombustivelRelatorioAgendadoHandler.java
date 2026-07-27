package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.util.ChromiumPdfUtil;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static String baseUrlInterno() {
        String v = System.getenv("APP_BASE_URL_INTERNO");
        return (v == null || v.isBlank()) ? "http://127.0.0.1:8080/fluxo-caixa" : v;
    }

    @Override
    public void executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        String combustivel = parametros.has("combustivel") ? parametros.get("combustivel").getAsString() : "Diesel";

        LocalDate dataFim = LocalDate.now().minusDays(1);
        LocalDate dataIni = dataInicial(parametros, dataFim);

        String jsessionid = obterSessao(idUsuarioCriacao);

        String query = "dataIni=" + dataIni.format(ISO) + "&dataFim=" + dataFim.format(ISO)
                + "&combustivel=" + URLEncoder.encode(combustivel, StandardCharsets.UTF_8);
        String url = baseUrlInterno() + "/combustivel-dashboard.html;jsessionid=" + jsessionid + "?" + query;

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

    /** Cria uma sessão válida (via SessaoRelatorioServlet, só acessível em localhost) pro Chromium abrir a página autenticado. */
    private String obterSessao(long idUsuarioCriacao) throws Exception {
        String url = baseUrlInterno() + "/api/interno/sessao-relatorio?idUsuario=" + idUsuarioCriacao;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (resp.statusCode() != 200 || !body.get("ok").getAsBoolean()) {
            throw new RuntimeException("Não foi possível abrir sessão interna pro relatório: " + resp.body());
        }
        return body.get("jsessionid").getAsString();
    }
}
