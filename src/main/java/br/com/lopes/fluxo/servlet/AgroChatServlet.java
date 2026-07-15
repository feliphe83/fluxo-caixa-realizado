package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.ChatPermissaoUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Proxy do chat agrícola: recebe a pergunta do widget (navegador, autenticado
 * por sessão de login normal, via AuthFilter) e repassa para o webhook do
 * workflow do n8n, que consulta o Oracle e usa o Gemini para responder.
 *
 * O navegador nunca fala diretamente com o n8n — assim a URL do webhook e o
 * segredo de autenticação com o n8n ficam só no servidor.
 *
 * Configuração via variáveis de ambiente:
 *   N8N_AGRO_WEBHOOK_URL  — URL do webhook de produção do workflow no n8n
 *   N8N_WEBHOOK_SECRET    — (opcional) valor enviado no header X-N8N-Webhook-Secret,
 *                           se o node Webhook do n8n estiver com Header Auth configurado
 *
 * POST /api/ia/agro-chat
 *   Body:     { "pergunta": "...", "sessionId": "..." }
 *   Resposta: { "ok": true, "resposta": "..." }
 */
@WebServlet("/api/ia/agro-chat")
public class AgroChatServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AgroChatServlet.class.getName());

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            String webhookUrl = System.getenv("N8N_AGRO_WEBHOOK_URL");
            if (webhookUrl == null || webhookUrl.isBlank()) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"ok\":false,\"erro\":\"N8N_AGRO_WEBHOOK_URL não configurada no servidor\"}");
                return;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            String pergunta = body.has("pergunta") ? body.get("pergunta").getAsString() : "";
            String sessionId = body.has("sessionId") ? body.get("sessionId").getAsString() : "";

            if (pergunta.isBlank()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Pergunta é obrigatória\"}");
                return;
            }

            // Categorias de consulta do usuário logado (AuthFilter garante a
            // sessão): registradas por sessionId para as ferramentas validarem,
            // e enviadas ao n8n para o agente saber o que pode usar.
            long idUsuario = 0;
            boolean administrador = false;
            HttpSession session = req.getSession(false);
            if (session != null) {
                Object idAttr = session.getAttribute("idUsuario");
                if (idAttr instanceof Number n) idUsuario = n.longValue();
                administrador = Boolean.TRUE.equals(session.getAttribute("administrador"));
            }
            var categorias = ChatPermissaoUtil.carregarCategorias(idUsuario, administrador);

            // Nível 1: sem a permissão de acesso ao assistente, o chat não responde
            if (!categorias.contains(ChatPermissaoUtil.ACESSO)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.print("{\"ok\":false,\"erro\":\"Você não tem acesso ao assistente. Solicite a liberação ao administrador da intranet.\"}");
                return;
            }

            ChatPermissaoUtil.registrar(sessionId, categorias);

            // O modelo de IA não sabe a data atual — sem isso, "próxima semana"
            // vira uma data inventada (ex.: 2023). Anexada à pergunta para
            // funcionar independentemente do system prompt configurado no n8n.
            java.time.LocalDate hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Maceio"));
            String diaSemana = hoje.getDayOfWeek().getDisplayName(
                    java.time.format.TextStyle.FULL, new java.util.Locale("pt", "BR"));
            String contextoData = "\n\n[Contexto do sistema: hoje é " + diaSemana + ", "
                    + hoje.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + ". Converta períodos relativos como 'esta semana', 'próxima semana' ou 'este mês' "
                    + "em datas concretas ao usar as ferramentas. A semana da usina começa no SÁBADO e "
                    + "termina na sexta-feira: 'esta semana' = do último sábado (inclusive hoje, se for sábado) "
                    + "até a próxima sexta-feira; 'próxima semana' = do próximo sábado até a sexta seguinte.]";

            JsonObject payload = new JsonObject();
            payload.addProperty("pergunta", pergunta + contextoData);
            payload.addProperty("sessionId", sessionId);
            JsonArray categoriasArr = new JsonArray();
            categorias.stream()
                    .filter(c -> !ChatPermissaoUtil.ACESSO.equals(c))
                    .map(c -> c.replace("chat_", "")).sorted().forEach(categoriasArr::add);
            payload.add("categorias", categoriasArr);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

            String secret = System.getenv("N8N_WEBHOOK_SECRET");
            if (secret != null && !secret.isBlank()) {
                builder.header("X-N8N-Webhook-Secret", secret);
            }

            HttpResponse<String> n8nResp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (n8nResp.statusCode() != 200) {
                LOG.warning("n8n retornou HTTP " + n8nResp.statusCode() + ": " + n8nResp.body());
                resp.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                out.print("{\"ok\":false,\"erro\":\"Falha ao consultar o assistente agrícola (HTTP "
                        + n8nResp.statusCode() + ")\"}");
                return;
            }

            // Repassa a resposta do n8n como veio (já no formato {ok, resposta})
            out.print(n8nResp.body());

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no proxy do chat agrícola", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }
}
