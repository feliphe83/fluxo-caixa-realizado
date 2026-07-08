package br.com.lopes.fluxo.servlet;

import com.google.gson.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * POST /api/redefinir-senha
 *
 * Recebe { "token": "...", "novaSenha": "..." }.
 * Valida se o token existe, não expirou e não foi usado; se válido,
 * atualiza a senha do usuário vinculado e marca o token como usado.
 */
@WebServlet("/api/redefinir-senha")
public class RedefinirSenhaServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(RedefinirSenhaServlet.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            String token = body.has("token") ? body.get("token").getAsString().trim() : "";
            String novaSenha = body.has("novaSenha") ? body.get("novaSenha").getAsString() : "";

            if (token.isEmpty()) {
                resp.setStatus(400);
                out.print("{\"ok\":false,\"erro\":\"Token inválido\"}");
                out.flush(); return;
            }
            if (novaSenha.length() < 6) {
                resp.setStatus(400);
                out.print("{\"ok\":false,\"erro\":\"A senha deve ter ao menos 6 caracteres\"}");
                out.flush(); return;
            }

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                conn.setAutoCommit(false);

                Integer idUsuario = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id_usuario, expira_em, usado FROM fc_reset_token WHERE token = ?")) {
                    ps.setString(1, token);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            resp.setStatus(400);
                            out.print("{\"ok\":false,\"erro\":\"Link inválido ou já utilizado\"}");
                            out.flush(); conn.rollback(); return;
                        }

                        boolean usado = rs.getBoolean("usado");
                        String expiraEmStr = rs.getString("expira_em");
                        LocalDateTime expiraEm = LocalDateTime.parse(
                            expiraEmStr.replace(" ", "T"));

                        if (usado) {
                            resp.setStatus(400);
                            out.print("{\"ok\":false,\"erro\":\"Este link já foi utilizado\"}");
                            out.flush(); conn.rollback(); return;
                        }
                        if (LocalDateTime.now().isAfter(expiraEm)) {
                            resp.setStatus(400);
                            out.print("{\"ok\":false,\"erro\":\"Este link expirou. Solicite uma nova redefinição.\"}");
                            out.flush(); conn.rollback(); return;
                        }

                        idUsuario = rs.getInt("id_usuario");
                    }
                }

                // Atualiza a senha (mesmo padrão SHA2 usado em todo o sistema)
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE fc_usuario SET senha_hash = SHA2(?, 256) WHERE id = ?")) {
                    ps.setString(1, novaSenha);
                    ps.setInt(2, idUsuario);
                    ps.executeUpdate();
                }

                // Marca o token como usado
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE fc_reset_token SET usado = 1 WHERE token = ?")) {
                    ps.setString(1, token);
                    ps.executeUpdate();
                }

                conn.commit();
                out.print("{\"ok\":true}");
            }

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao redefinir senha", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"","'").replace("\n"," ")
                    : e.getClass().getName();
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }
}
