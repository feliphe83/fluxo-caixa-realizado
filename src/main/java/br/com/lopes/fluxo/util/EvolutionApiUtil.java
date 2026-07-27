package br.com.lopes.fluxo.util;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Envio de mensagens via WhatsApp usando a Evolution API (self-hosted,
 * conectada por QR Code — ver documentação da instância da empresa).
 *
 * Configuração necessária (variáveis de ambiente, no setenv.sh do Tomcat):
 *   - EVOLUTION_API_URL:      URL base da instância (ex.: http://localhost:8080)
 *   - EVOLUTION_API_KEY:      apikey configurada na Evolution API
 *   - EVOLUTION_API_INSTANCE: nome da instância conectada (ex.: "usina")
 *
 * Endpoints usados (contrato padrão da Evolution API v2 — se a versão
 * instalada divergir, ajuste os paths/campos do corpo aqui, é o único lugar
 * que fala com a API):
 *   POST {url}/message/sendText/{instance}   { number, text }
 *   POST {url}/message/sendMedia/{instance}  { number, mediatype:"document", media (base64), fileName, caption }
 *
 * Não testado contra uma instância real (sem acesso de rede a partir daqui)
 * — validar com um envio de teste depois do deploy.
 */
public final class EvolutionApiUtil {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private EvolutionApiUtil() {}

    private static String baseUrl() {
        return exigir("EVOLUTION_API_URL");
    }

    private static String apiKey() {
        return exigir("EVOLUTION_API_KEY");
    }

    private static String instancia() {
        return exigir("EVOLUTION_API_INSTANCE");
    }

    private static String exigir(String var) {
        String v = System.getenv(var);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                "Variável de ambiente " + var + " não configurada. "
                + "Configure no setenv.sh do Tomcat (ver EvolutionApiUtil).");
        }
        return v;
    }

    /**
     * Normaliza um telefone brasileiro pro formato que a Evolution API espera
     * (só dígitos, com código do país): remove tudo que não é dígito; se não
     * começar com "55", prefixa. Ex.: "(82) 99999-8888" -> "5582999998888".
     */
    public static String normalizarNumero(String telefone) {
        if (telefone == null) return null;
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.isBlank()) return null;
        if (!digitos.startsWith("55")) digitos = "55" + digitos;
        return digitos;
    }

    public static void enviarTexto(String telefone, String texto) throws Exception {
        String numero = normalizarNumero(telefone);
        if (numero == null) throw new IllegalArgumentException("Telefone vazio/inválido");

        JsonObject body = new JsonObject();
        body.addProperty("number", numero);
        body.addProperty("text", texto);

        chamar("/message/sendText/" + instancia(), body);
    }

    public static void enviarDocumento(String telefone, byte[] pdf, String nomeArquivo, String legenda) throws Exception {
        String numero = normalizarNumero(telefone);
        if (numero == null) throw new IllegalArgumentException("Telefone vazio/inválido");

        JsonObject body = new JsonObject();
        body.addProperty("number", numero);
        body.addProperty("mediatype", "document");
        body.addProperty("mimetype", "application/pdf");
        body.addProperty("fileName", nomeArquivo);
        body.addProperty("caption", legenda);
        body.addProperty("media", Base64.getEncoder().encodeToString(pdf));

        chamar("/message/sendMedia/" + instancia(), body);
    }

    private static void chamar(String caminho, JsonObject body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + caminho))
                .header("Content-Type", "application/json")
                .header("apikey", apiKey())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("Evolution API retornou HTTP " + resp.statusCode() + ": " + resp.body());
        }
    }
}
