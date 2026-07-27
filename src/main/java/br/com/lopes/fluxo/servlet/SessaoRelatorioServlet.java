package br.com.lopes.fluxo.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Bootstrap de sessão pro Chromium headless conseguir abrir uma página
 * autenticada (ex.: combustivel-dashboard.html) sem ter feito login de
 * verdade — usado só pelo envio agendado de relatórios (ver
 * RelatorioAgendadoScheduler), que roda dentro do mesmo processo do Tomcat e
 * chama esse endpoint em localhost (nunca passa pelo nginx/domínio público).
 *
 * GET /api/interno/sessao-relatorio?idUsuario=N
 *   -> { ok:true, jsessionid: "..." }
 *
 * Segurança: liberado no AuthFilter (path público), mas só responde se a
 * requisição vier do próprio loopback (127.0.0.1/::1) — não alcançável de
 * fora, já que o Tomcat só é exposto externamente através do nginx (porta
 * 8080 não tem rota da internet até aqui). Sem token opaco na URL porque
 * quem chama já É o servidor.
 */
@WebServlet("/api/interno/sessao-relatorio")
public class SessaoRelatorioServlet extends HttpServlet {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        String remoto = req.getRemoteAddr();
        if (!"127.0.0.1".equals(remoto) && !"0:0:0:0:0:0:0:1".equals(remoto) && !"::1".equals(remoto)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"ok\":false,\"erro\":\"Endpoint interno — acessível apenas via localhost\"}");
            out.flush();
            return;
        }

        String idParam = req.getParameter("idUsuario");
        if (idParam == null || !idParam.matches("\\d+")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"ok\":false,\"erro\":\"Parâmetro idUsuario é obrigatório\"}");
            out.flush();
            return;
        }
        long idUsuario = Long.parseLong(idParam);

        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = c.prepareStatement(
                 "SELECT logon, nome, administrador FROM fc_usuario WHERE id=? AND ativo='S'")) {
            ps.setLong(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    out.print("{\"ok\":false,\"erro\":\"Usuário não encontrado ou inativo\"}");
                    out.flush();
                    return;
                }
                String logon = rs.getString("logon");
                String nome = rs.getString("nome");
                boolean administrador = "S".equals(rs.getString("administrador"));

                HttpSession session = req.getSession(true);
                session.setAttribute("logon", logon);
                session.setAttribute("idUsuario", idUsuario);
                session.setAttribute("nome", nome);
                session.setAttribute("administrador", administrador);
                // Só serve pra renderizar uma página e tirar o PDF — curta duração.
                session.setMaxInactiveInterval(5 * 60);

                out.print("{\"ok\":true,\"jsessionid\":\"" + session.getId() + "\"}");
            }
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        } finally {
            out.flush();
        }
    }
}
