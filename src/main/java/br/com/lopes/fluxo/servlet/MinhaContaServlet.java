package br.com.lopes.fluxo.servlet;

import com.google.gson.*;
import java.io.*;
import java.sql.*;
import java.util.logging.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;

/**
 * POST /api/minha-conta/senha
 *
 * Permite que o usuário logado altere a própria senha, sem exigir a senha
 * atual (conforme definido). Usa o idUsuario da sessão — nunca aceita um
 * id vindo do corpo da requisição, para impedir que um usuário altere a
 * senha de outro.
 *
 * Body esperado: { "novaSenha": "..." }
 */
@WebServlet("/api/minha-conta/senha")
public class MinhaContaServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(MinhaContaServlet.class.getName());
    private final Gson gson = new Gson();

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("idUsuario") == null) {
            resp.setStatus(401);
            out.print("{\"ok\":false,\"erro\":\"Não autenticado\"}");
            out.flush(); return;
        }

        int idUsuario = ((Number) session.getAttribute("idUsuario")).intValue();

        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            String novaSenha = body.has("novaSenha") ? body.get("novaSenha").getAsString() : null;

            if (novaSenha == null || novaSenha.length() < 6) {
                resp.setStatus(400);
                out.print("{\"ok\":false,\"erro\":\"A senha deve ter ao menos 6 caracteres\"}");
                out.flush(); return;
            }

            // Hash da senha calculado pelo MySQL via SHA2 — mesmo padrão usado
            // no cadastro/edição de usuários (AdminServlet) e na validação
            // de login (LoginServlet).
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement ps = conn.prepareStatement(
                     "UPDATE fc_usuario SET senha_hash = SHA2(?, 256) WHERE id = ?")) {
                ps.setString(1, novaSenha);
                ps.setInt(2, idUsuario);
                int linhas = ps.executeUpdate();

                if (linhas == 0) {
                    resp.setStatus(404);
                    out.print("{\"ok\":false,\"erro\":\"Usuário não encontrado\"}");
                    out.flush(); return;
                }
            }

            out.print("{\"ok\":true}");

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao alterar senha", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"","'").replace("\n"," ")
                    : e.getClass().getName();
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }
}
