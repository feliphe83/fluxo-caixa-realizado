package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.ChatPermissaoUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abre uma sessão do assistente para quem chega pelo WhatsApp.
 *
 * O n8n chama isto assim que a Evolution API entrega uma mensagem, passando o
 * número de quem falou e o sessionId que ele vai usar nas ferramentas. Aqui o
 * número vira uma pessoa do fc_usuario, as permissões dela são carregadas e o
 * sessionId é registrado — a partir daí as ferramentas de /api/agricola/
 * funcionam exatamente como funcionam para quem está logado na intranet.
 *
 * O bloqueio continua sendo do servidor. O n8n só informa quem falou; quem
 * decide o que essa pessoa pode ver é esta classe, lendo a mesma tabela de
 * permissões da tela de administração.
 *
 * POST /api/agricola/whatsapp-sessao   (exige X-Agro-Api-Key, como as demais)
 *   { "telefone": "5582988887777", "sessionId": "...", "grupo": false }
 *
 * Respostas:
 *   200 {"ok":true, ...}       pode conversar
 *   403 {"ok":false,"motivo":} não pode — o n8n deve ficar calado
 */
@WebServlet("/api/agricola/whatsapp-sessao")
public class AgroChatWhatsappServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AgroChatWhatsappServlet.class.getName());
    private static final Gson GSON = new Gson();

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    /**
     * Traz os cadastrados com telefone e a comparação é feita em Java.
     *
     * O mesmo número aparece no cadastro como (82) 98888-7777 e chega da
     * Evolution como 5582988887777 — com 55 na frente, e às vezes sem o 9 do
     * celular. Comparar os oito finais acerta os dois casos sem depender de
     * como cada um foi digitado.
     *
     * Poderia ser um RIGHT(REGEXP_REPLACE(...), 8) no WHERE, mas
     * REGEXP_REPLACE só existe do MySQL 8 em diante, e não vale amarrar o
     * assistente à versão do banco por causa de uma tabela de algumas
     * centenas de linhas.
     */
    private static final String SQL_COM_TELEFONE = """
        SELECT id, nome, administrador, telefone
        FROM   fc_usuario
        WHERE  ativo = 'S' AND telefone IS NOT NULL AND telefone <> ''
        """;

    /** Os oito últimos dígitos, que é o que dois formatos do mesmo número têm em comum. */
    static String chave(String telefone) {
        String d = digitos(telefone);
        return d.length() < 8 ? null : d.substring(d.length() - 8);
    }

    private void json(HttpServletResponse resp, int status, String body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().print(body);
        resp.getWriter().flush();
    }

    private void negar(HttpServletResponse resp, String motivo) throws IOException {
        // 403 com o motivo para o log do n8n — a mensagem NÃO deve virar
        // resposta no WhatsApp. Responder a um número desconhecido confirma
        // que o serviço existe e convida a insistir.
        json(resp, HttpServletResponse.SC_FORBIDDEN,
             "{\"ok\":false,\"responder\":false,\"motivo\":" + GSON.toJson(motivo) + "}");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = req.getReader()) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            }
            JsonObject b = JsonParser.parseString(sb.toString()).getAsJsonObject();

            // ── Grupo nunca ────────────────────────────────────────────────
            // Numa conversa de grupo a resposta vai para todo mundo que está
            // nele. Como o assistente alcança custo de folha e contas a pagar,
            // uma pergunta feita no grupo errado publica isso para quem não
            // deveria — e não há como despublicar.
            if (ehGrupo(b)) {
                negar(resp, "Mensagem de grupo — o assistente só responde em conversa individual.");
                return;
            }

            String telefone = digitos(texto(b, "telefone"));
            String sessionId = texto(b, "sessionId");
            if (telefone.length() < 8 || sessionId == null || sessionId.isBlank()) {
                json(resp, HttpServletResponse.SC_BAD_REQUEST,
                     "{\"ok\":false,\"erro\":\"telefone e sessionId são obrigatórios\"}");
                return;
            }

            long idUsuario = 0;
            String nome = null;
            boolean administrador = false;
            int encontrados = 0;

            String procurado = chave(telefone);
            try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement ps = c.prepareStatement(SQL_COM_TELEFONE);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!procurado.equals(chave(rs.getString("telefone")))) continue;
                    encontrados++;
                    idUsuario = rs.getLong("id");
                    nome = rs.getString("nome");
                    administrador = "S".equals(rs.getString("administrador"));
                }
            }

            if (encontrados == 0) {
                LOG.info("WhatsApp de número não cadastrado tentou o assistente: " + mascarar(telefone));
                negar(resp, "Número não cadastrado.");
                return;
            }
            if (encontrados > 1) {
                // Dois usuários com o mesmo telefone: não dá para saber de
                // quem são as permissões, e chutar seria dar a alguém o acesso
                // de outra pessoa.
                LOG.warning("Telefone " + mascarar(telefone) + " está em mais de um usuário — assistente negado.");
                negar(resp, "Telefone cadastrado em mais de um usuário.");
                return;
            }

            Set<String> categorias = ChatPermissaoUtil.carregarCategorias(idUsuario, administrador);
            if (!categorias.contains(ChatPermissaoUtil.ACESSO)) {
                LOG.info("Usuário " + nome + " pediu o assistente pelo WhatsApp sem permissão de acesso.");
                negar(resp, "Usuário sem permissão de acesso ao assistente.");
                return;
            }

            ChatPermissaoUtil.registrar(sessionId, categorias);

            JsonArray cats = new JsonArray();
            categorias.stream()
                      .filter(c -> !ChatPermissaoUtil.ACESSO.equals(c))
                      .map(c -> c.replace("chat_", ""))
                      .sorted().forEach(cats::add);

            JsonObject o = new JsonObject();
            o.addProperty("ok", true);
            o.addProperty("responder", true);
            o.addProperty("idUsuario", idUsuario);
            o.addProperty("nome", nome);
            o.add("categorias", cats);
            LOG.info("Assistente liberado no WhatsApp para " + nome + " (" + cats + ")");
            json(resp, HttpServletResponse.SC_OK, GSON.toJson(o));

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao abrir sessão do assistente pelo WhatsApp", e);
            json(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                 "{\"ok\":false,\"responder\":false,\"erro\":\"Falha ao verificar o acesso\"}");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Corpo inválido na sessão do assistente pelo WhatsApp", e);
            json(resp, HttpServletResponse.SC_BAD_REQUEST,
                 "{\"ok\":false,\"responder\":false,\"erro\":\"Requisição inválida\"}");
        }
    }

    // ── Auxiliares ────────────────────────────────────────────────────────

    /**
     * Grupo pelo campo explícito ou pelo próprio JID: no WhatsApp, conversa de
     * grupo termina em "@g.us". Conferir os dois evita depender de o n8n
     * lembrar de mandar a marcação.
     */
    static boolean ehGrupo(JsonObject b) {
        if (b.has("grupo") && !b.get("grupo").isJsonNull()) {
            try { if (b.get("grupo").getAsBoolean()) return true; } catch (Exception ignore) {}
        }
        for (String campo : new String[]{ "remoteJid", "jid", "chatId", "telefone", "from" }) {
            String v = texto(b, campo);
            if (v != null && v.toLowerCase().contains("@g.us")) return true;
        }
        return false;
    }

    static String digitos(String v) {
        return v == null ? "" : v.replaceAll("\\D", "");
    }

    /** Guarda só os quatro últimos no log — o resto não precisa ficar escrito. */
    static String mascarar(String tel) {
        if (tel == null || tel.length() < 4) return "****";
        return "****" + tel.substring(tel.length() - 4);
    }

    static String texto(JsonObject o, String c) {
        if (o == null || !o.has(c) || o.get(c).isJsonNull()) return null;
        try {
            String v = o.get(c).getAsString();
            return v.isBlank() ? null : v.trim();
        } catch (Exception e) { return null; }
    }
}
