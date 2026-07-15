package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.ChatPermissaoUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;

/**
 * Informa se o usuário logado tem acesso ao assistente IA (permissão
 * chat_acesso; administradores sempre têm). Usado pela página do agro-chat
 * (bloqueio de entrada) e pelo botão flutuante nas demais telas (só aparece
 * para quem tem acesso). Autenticação por sessão de login (AuthFilter).
 *
 * GET /api/ia/agro-chat-acesso
 *   Resposta: { "ok": true, "acesso": bool }
 */
@WebServlet("/api/ia/agro-chat-acesso")
public class AgroChatAcessoServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        long idUsuario = 0;
        boolean administrador = false;
        HttpSession session = req.getSession(false);
        if (session != null) {
            Object idAttr = session.getAttribute("idUsuario");
            if (idAttr instanceof Number n) idUsuario = n.longValue();
            administrador = Boolean.TRUE.equals(session.getAttribute("administrador"));
        }

        Set<String> categorias = ChatPermissaoUtil.carregarCategorias(idUsuario, administrador);
        boolean acesso = categorias.contains(ChatPermissaoUtil.ACESSO);

        resp.getWriter().print("{\"ok\":true,\"acesso\":" + acesso + "}");
        resp.getWriter().flush();
    }
}
