package br.com.lopes.fluxo.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Previsão do tempo (chuva + umidade) via Open-Meteo — API pública, gratuita
 * e sem chave (https://open-meteo.com), usada pelo Dr. Alfredo pra responder
 * sobre previsão dos próximos dias. Diferente da consulta de chuva já
 * existente (pontos de coleta históricos, dados internos no Oracle), esta
 * é sobre o FUTURO e vem de fonte externa.
 *
 * Fluxo: geocodifica o nome do local (padrão: Rio Largo/AL, sede da usina,
 * se nada for informado) para lat/lon, depois busca a previsão diária.
 */
public final class PrevisaoTempoUtil {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private static final double LAT_PADRAO = -9.47833;
    private static final double LON_PADRAO = -35.85333;
    private static final String LOCAL_PADRAO_DESCRICAO = "Rio Largo, Alagoas, Brasil";

    private PrevisaoTempoUtil() {}

    public static final class Coordenada {
        public final double latitude;
        public final double longitude;
        public final String nomeResolvido;

        Coordenada(double latitude, double longitude, String nomeResolvido) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.nomeResolvido = nomeResolvido;
        }
    }

    /**
     * Geocodifica um nome de local (município, com ou sem estado) dentro do
     * Brasil. Sem "local" (null/vazio), cai no padrão (Rio Largo/AL).
     *
     * @return coordenada resolvida, ou null se nenhum local foi encontrado
     */
    public static Coordenada geocodificar(String local) throws IOException, InterruptedException {
        if (local == null || local.isBlank()) {
            return new Coordenada(LAT_PADRAO, LON_PADRAO, LOCAL_PADRAO_DESCRICAO);
        }

        String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                + URLEncoder.encode(local.trim(), StandardCharsets.UTF_8)
                + "&count=1&language=pt&format=json&countryCode=BR";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (!json.has("results") || json.getAsJsonArray("results").isEmpty()) {
            return null;
        }

        JsonObject r = json.getAsJsonArray("results").get(0).getAsJsonObject();
        double lat = r.get("latitude").getAsDouble();
        double lon = r.get("longitude").getAsDouble();
        StringBuilder nome = new StringBuilder(r.get("name").getAsString());
        if (r.has("admin1")) nome.append(", ").append(r.get("admin1").getAsString());
        if (r.has("country")) nome.append(", ").append(r.get("country").getAsString());
        return new Coordenada(lat, lon, nome.toString());
    }

    /** Previsão diária (chuva + umidade + temperatura) a partir de hoje, {@code dias} dias. */
    public static List<Map<String, Object>> buscarPrevisao(double lat, double lon, int dias)
            throws IOException, InterruptedException {

        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&daily=precipitation_sum,precipitation_probability_max,relative_humidity_2m_mean,"
                + "relative_humidity_2m_max,relative_humidity_2m_min,temperature_2m_max,temperature_2m_min"
                + "&timezone=America%2FMaceio&forecast_days=" + dias;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject daily = json.getAsJsonObject("daily");

        JsonArray datas = daily.getAsJsonArray("time");
        JsonArray precip = daily.getAsJsonArray("precipitation_sum");
        JsonArray probChuva = daily.getAsJsonArray("precipitation_probability_max");
        JsonArray umidMedia = daily.getAsJsonArray("relative_humidity_2m_mean");
        JsonArray umidMax = daily.getAsJsonArray("relative_humidity_2m_max");
        JsonArray umidMin = daily.getAsJsonArray("relative_humidity_2m_min");
        JsonArray tempMax = daily.getAsJsonArray("temperature_2m_max");
        JsonArray tempMin = daily.getAsJsonArray("temperature_2m_min");

        List<Map<String, Object>> lista = new ArrayList<>();
        for (int i = 0; i < datas.size(); i++) {
            Map<String, Object> dia = new LinkedHashMap<>();
            dia.put("data", datas.get(i).getAsString());
            dia.put("precipitacaoMm", precip.get(i).getAsDouble());
            dia.put("probabilidadeChuvaPct", probChuva.get(i).getAsInt());
            dia.put("umidadeMediaPct", umidMedia.get(i).getAsInt());
            dia.put("umidadeMaxPct", umidMax.get(i).getAsInt());
            dia.put("umidadeMinPct", umidMin.get(i).getAsInt());
            dia.put("temperaturaMaxC", tempMax.get(i).getAsDouble());
            dia.put("temperaturaMinC", tempMin.get(i).getAsDouble());
            lista.add(dia);
        }
        return lista;
    }
}
