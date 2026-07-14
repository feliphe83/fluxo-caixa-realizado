package br.com.lopes.fluxo.servlet;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.*;
import java.io.IOException;

/**
 * Substitui o EncodingFilter.
 * - Aplica UTF-8 em todos os requests/responses
 * - Protege todas as rotas exceto login.html e POST /api/login
 * - Redireciona para login.html se sessão inativa
 * - Retorna 401 JSON para chamadas AJAX sem sessão
 */
@WebFilter("/*")
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        req.setCharacterEncoding("UTF-8");
        res.setCharacterEncoding("UTF-8");

        HttpServletRequest  hreq  = (HttpServletRequest)  req;
        HttpServletResponse hresp = (HttpServletResponse) res;

        String uri = hreq.getRequestURI();
        String ctx = hreq.getContextPath();

        // Rotas de ferramentas do chatbot (chamadas pelo n8n, sem sessão de
        // navegador) — autenticadas por chave de API própria em vez de login.
        if (uri.startsWith(ctx + "/api/agricola/") || uri.startsWith(ctx + "/api/financeiro/")) {
            String chave = hreq.getHeader("X-Agro-Api-Key");
            String esperada = System.getenv("AGRO_API_KEY");
            if (esperada != null && !esperada.isBlank() && esperada.equals(chave)) {
                chain.doFilter(req, res);
            } else {
                hresp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                hresp.setContentType("application/json;charset=UTF-8");
                hresp.getWriter().print("{\"ok\":false,\"erro\":\"Chave de API inválida ou não configurada\"}");
                hresp.getWriter().flush();
            }
            return;
        }

        // Recursos liberados sem autenticação
        boolean liberado =
            uri.equals(ctx + "/login.html")              ||
            uri.equals(ctx + "/esqueci-senha.html")       ||
            uri.equals(ctx + "/redefinir-senha.html")     ||
            uri.startsWith(ctx + "/api/login")            ||
            uri.startsWith(ctx + "/api/esqueci-senha")     ||
            uri.startsWith(ctx + "/api/redefinir-senha")   ||
            uri.startsWith(ctx + "/css/")                 ||
            uri.startsWith(ctx + "/js/")                  ||
            uri.startsWith(ctx + "/img/");

        if (liberado) {
            chain.doFilter(req, res);
            return;
        }

        // Verificar sessão
        HttpSession session    = hreq.getSession(false);
        boolean     autenticado = session != null && session.getAttribute("logon") != null;

        if (!autenticado) {
            String accept = hreq.getHeader("Accept");
            if (accept != null && accept.contains("application/json")) {
                hresp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                hresp.setContentType("application/json;charset=UTF-8");
                hresp.getWriter().print("{\"ok\":false,\"erro\":\"Sessão expirada\",\"redirect\":true}");
                hresp.getWriter().flush();
                return;
            }
            hresp.sendRedirect(ctx + "/login.html");
            return;
        }

        chain.doFilter(req, res);
    }
}
