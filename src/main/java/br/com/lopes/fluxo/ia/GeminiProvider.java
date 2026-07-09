package br.com.lopes.fluxo.ia;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/**
 * Implementação do IaProvider usando a API do Google Gemini.
 *
 * Configuração necessária:
 *   - Variável de ambiente GEMINI_API_KEY com a chave da API
 *     (obtida em https://aistudio.google.com)
 *
 * Para trocar de modelo, altere a constante MODEL abaixo.
 * gemini-2.0-flash-lite foi desativado pelo Google (jul/2026); modelo atual
 * recomendado da família "flash-lite": gemini-2.5-flash-lite.
 * Consulte https://ai.google.dev/gemini-api/docs/models para a lista atual.
 */
public class GeminiProvider implements IaProvider {

    private static final String MODEL = "gemini-2.5-flash-lite";
    private static final String API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";

    private final String apiKey;
    private final HttpClient httpClient;

    public GeminiProvider() {
        this.apiKey = System.getenv("GEMINI_API_KEY");
        if (this.apiKey == null || this.apiKey.isBlank()) {
            throw new IllegalStateException(
                "Variável de ambiente GEMINI_API_KEY não configurada. " +
                "Configure com: export GEMINI_API_KEY=sua_chave_aqui");
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String gerarResposta(String systemPrompt, String userPrompt) throws Exception {

        // Monta o corpo da requisição no formato esperado pela API do Gemini
        JsonObject body = new JsonObject();

        // systemInstruction define o "papel" da IA
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            JsonObject systemInstruction = new JsonObject();
            JsonArray sysParts = new JsonArray();
            JsonObject sysPart = new JsonObject();
            sysPart.addProperty("text", systemPrompt);
            sysParts.add(sysPart);
            systemInstruction.add("parts", sysParts);
            body.add("systemInstruction", systemInstruction);
        }

        // contents é a mensagem do usuário
        JsonArray contents = new JsonArray();
        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject part = new JsonObject();
        part.addProperty("text", userPrompt);
        parts.add(part);
        content.add("parts", parts);
        contents.add(content);
        body.add("contents", contents);

        // Configurações de geração (opcional, ajustável)
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.3);
        generationConfig.addProperty("maxOutputTokens", 2048);
        body.add("generationConfig", generationConfig);

        String requestBody = body.toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                "Erro na API Gemini (HTTP " + response.statusCode() + "): " + response.body());
        }

        // Parse da resposta
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

        JsonArray candidates = json.getAsJsonArray("candidates");
        if (candidates == null || candidates.size() == 0) {
            throw new RuntimeException("Resposta da IA vazia ou inesperada: " + response.body());
        }

        JsonObject candidate = candidates.get(0).getAsJsonObject();
        JsonObject contentObj = candidate.getAsJsonObject("content");
        JsonArray partsArr = contentObj.getAsJsonArray("parts");

        StringBuilder resultado = new StringBuilder();
        for (JsonElement p : partsArr) {
            JsonObject po = p.getAsJsonObject();
            if (po.has("text")) {
                resultado.append(po.get("text").getAsString());
            }
        }

        return resultado.toString();
    }
}
