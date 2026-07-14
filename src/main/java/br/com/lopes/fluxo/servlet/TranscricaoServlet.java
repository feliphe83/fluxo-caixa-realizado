package br.com.lopes.fluxo.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transcrição de áudio para texto (voz do agro-chat): recebe o áudio gravado
 * no navegador (MediaRecorder) e repassa para a API de transcrição da OpenAI
 * (Whisper). Autenticado por sessão de login normal (AuthFilter), como as
 * demais rotas /api/ia/*.
 *
 * A chave da OpenAI fica só no servidor:
 *   OPENAI_API_KEY    — obrigatória
 *   OPENAI_STT_MODEL  — (opcional) modelo de transcrição; padrão whisper-1
 *
 * POST /api/ia/transcrever
 *   Body:     bytes do áudio (Content-Type: audio/webm, audio/mp4, ...)
 *   Resposta: { "ok": true, "texto": "..." }
 */
@WebServlet("/api/ia/transcrever")
public class TranscricaoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(TranscricaoServlet.class.getName());

    /** Limite de upload (~60s de áudio comprimido fica bem abaixo disso). */
    private static final int MAX_BYTES = 15 * 1024 * 1024;

    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"ok\":false,\"erro\":\"OPENAI_API_KEY não configurada no servidor\"}");
                return;
            }

            byte[] audio = req.getInputStream().readNBytes(MAX_BYTES + 1);
            if (audio.length == 0) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Áudio vazio\"}");
                return;
            }
            if (audio.length > MAX_BYTES) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Áudio muito grande (limite 15 MB)\"}");
                return;
            }

            String contentType = req.getContentType() != null ? req.getContentType() : "audio/webm";
            String nomeArquivo = "audio" + extensaoPara(contentType);
            String modelo = System.getenv().getOrDefault("OPENAI_STT_MODEL", "whisper-1");

            // Corpo multipart/form-data montado à mão (HttpClient não tem builder)
            String boundary = "----FluxoVoz" + System.currentTimeMillis();
            ByteArrayOutputStream corpo = new ByteArrayOutputStream();
            escrever(corpo, "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"model\"\r\n\r\n" + modelo + "\r\n");
            escrever(corpo, "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"language\"\r\n\r\npt\r\n");
            escrever(corpo, "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + nomeArquivo + "\"\r\n"
                    + "Content-Type: " + contentType + "\r\n\r\n");
            corpo.write(audio);
            escrever(corpo, "\r\n--" + boundary + "--\r\n");

            HttpRequest openaiReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/audio/transcriptions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
                    .build();

            HttpResponse<String> openaiResp =
                    httpClient.send(openaiReq, HttpResponse.BodyHandlers.ofString());

            if (openaiResp.statusCode() != 200) {
                LOG.warning("OpenAI transcrição HTTP " + openaiResp.statusCode() + ": " + openaiResp.body());
                resp.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                out.print("{\"ok\":false,\"erro\":\"Falha na transcrição (HTTP " + openaiResp.statusCode() + ")\"}");
                return;
            }

            String texto = JsonParser.parseString(openaiResp.body())
                    .getAsJsonObject().get("text").getAsString().trim();

            JsonObject json = new JsonObject();
            json.addProperty("ok", true);
            json.addProperty("texto", texto);
            out.print(gson.toJson(json));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro na transcrição de áudio", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    private static void escrever(ByteArrayOutputStream os, String s) throws IOException {
        os.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String extensaoPara(String contentType) {
        String ct = contentType.toLowerCase();
        if (ct.contains("mp4"))  return ".mp4";   // Safari (AAC)
        if (ct.contains("mpeg")) return ".mp3";
        if (ct.contains("ogg"))  return ".ogg";
        if (ct.contains("wav"))  return ".wav";
        return ".webm";                            // Chrome/Edge/Firefox
    }
}
