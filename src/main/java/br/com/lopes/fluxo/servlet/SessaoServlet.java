package br.com.lopes.fluxo.servlet;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;
import java.io.IOException;

@WebServlet("/api/sessao")
public class SessaoServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("logon") == null) {
            resp.setStatus(401);
            resp.getWriter().print("{\"ok\":false}");
            return;
        }
        String  logon = (String)  session.getAttribute("logon");
        String  nome  = (String)  session.getAttribute("nome");
        Object  adm   = session.getAttribute("administrador");
        boolean admin = Boolean.TRUE.equals(adm);
        resp.getWriter().print(
            "{\"ok\":true,\"logon\":\"" + logon + "\",\"nome\":\"" + nome + "\",\"administrador\":" + admin + "}"
        );
        resp.getWriter().flush();
    }
}
