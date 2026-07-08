package br.com.lopes.fluxo.servlet;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(LoginServlet.class.getName());
    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private static final String SQL_LOGIN =
        "SELECT id, logon, nome, administrador " +
        "FROM   fc_usuario " +
        "WHERE  logon     = UPPER(?) " +
        "AND    senha_hash = SHA2(?, 256) " +
        "AND    ativo      = 'S'";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        String logon = req.getParameter("logon");
        String senha = req.getParameter("senha");
        if (logon == null || logon.isBlank() || senha == null || senha.isBlank()) {
            resp.setStatus(400);
            out.print("{\"ok\":false,\"erro\":\"Usuário e senha são obrigatórios\"}");
            out.flush(); return;
        }
        logon = logon.trim().toUpperCase();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(SQL_LOGIN)) {
            ps.setString(1, logon);
            ps.setString(2, senha);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    out.print("{\"ok\":false,\"erro\":\"Usuário ou senha incorretos\"}");
                    out.flush(); return;
                }
                long    id    = rs.getLong("id");
                String  nome  = rs.getString("nome");
                boolean admin = "S".equals(rs.getString("administrador"));
                HttpSession session = req.getSession(true);
                session.setAttribute("logon",         logon);
                session.setAttribute("idUsuario",     id);
                session.setAttribute("nome",          nome);
                session.setAttribute("administrador", admin);
                session.setMaxInactiveInterval(60 * 60 * 8);
                out.print("{\"ok\":true,\"logon\":\"" + logon + "\",\"nome\":\"" + nome + "\",\"administrador\":" + admin + "}");
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro login", e);
            out.print("{\"ok\":false,\"erro\":\"" + e.getMessage().replace("\"","'") + "\"}");
        } finally { out.flush(); }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession s = req.getSession(false);
        if (s != null) s.invalidate();
        resp.sendRedirect(req.getContextPath() + "/login.html");
    }
}
