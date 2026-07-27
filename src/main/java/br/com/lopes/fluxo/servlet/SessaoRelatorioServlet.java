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
 * GET /api/interno/sessao-relatorio?idUsuario=N&redirect=pagina.html?a=1
 *   -> 302 para a página, com Set-Cookie da sessão recém-criada
 *
 * A forma com "redirect" é a que o Chromium headless usa: ele abre ESTA
 * URL, guarda o cookie JSESSIONID da resposta e só então segue pra página —
 * assim os fetch() que a página faz pra API vão autenticados pelo mesmo
 * cookie. Passar ";jsessionid=" na URL da página não resolve: identifica no
 * máximo o HTML, e as chamadas de API subsequentes saem sem credencial
 * nenhuma, caindo no redirect de login (o PDF saía com a tela de login).
 *
 * Segurança: liberado no AuthFilter (path público), mas só responde se a
 * requisição vier do próprio loopback (127.0.0.1/::1) — não alcançável de
 * fora, já que o Tomcat só é exposto externamente através do nginx (porta
 * 8080 não tem rota da internet até aqui). Sem token opaco na URL porque
 * quem chama já É o servidor. O destino do redirect é restrito a um caminho
 * relativo dentro do próprio contexto.
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
                // Tempo de sobra pro Chromium carregar a página, esperar as
                // consultas do relatório (podem levar minutos) e imprimir.
                session.setMaxInactiveInterval(15 * 60);

                String redirect = req.getParameter("redirect");
                if (redirect != null && !redirect.isBlank()) {
                    if (!destinoInterno(redirect)) {
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        out.print("{\"ok\":false,\"erro\":\"redirect deve ser um caminho relativo dentro da aplicação\"}");
                        out.flush();
                        return;
                    }
                    resp.sendRedirect(req.getContextPath() + "/" + redirect);
                    return;
                }

                out.print("{\"ok\":true,\"jsessionid\":\"" + session.getId() + "\"}");
            }
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        } finally {
            out.flush();
        }
    }

    /**
     * Só aceita caminho relativo dentro do próprio contexto — barra URL
     * absoluta ("http://…"), protocol-relative ("//host") e qualquer tentativa
     * de subir de diretório, pra este endpoint não virar um open redirect.
     */
    private static boolean destinoInterno(String redirect) {
        return !redirect.startsWith("/")
            && !redirect.contains("://")
            && !redirect.contains("..");
    }
}
