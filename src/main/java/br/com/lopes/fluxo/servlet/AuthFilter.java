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

        // ── Domínio do portal de manobra ─────────────────────────────────
        // bdo.usinasclotilde.com.br e a intranet são o mesmo WAR, e é o Host
        // que separa os dois. No subdomínio só o portal responde: assim um
        // endereço interno que vaze para fora não vira porta de entrada, e a
        // separação não depende de ninguém lembrar de conferir a sessão.
        if (ehDominioExterno(hreq)) {
            if (uri.equals(ctx) || uri.equals(ctx + "/")) {
                hresp.sendRedirect(ctx + "/manobra-login.html");
                return;
            }
            if (!doPortal(uri, ctx)) {
                hresp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }

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
            // Página que embute o PDF da ordem de compra (link clicado a
            // partir do chat) — sem sessão, autenticada pelo token na URL,
            // que a própria API de PDF (/api/publico/*) valida ao carregar.
            uri.equals(ctx + "/ordem-compra-pdf.html")    ||
            // Painel de status dos envios de WhatsApp: feito para ficar aberto
            // num monitor, sem ninguém logado. Só mostra se os agendamentos
            // rodaram e com que resultado — os dados vêm de
            // /api/publico/status-envios, que já é liberado abaixo e devolve os
            // telefones mascarados.
            uri.equals(ctx + "/status-envios.html")       ||
            // Service worker e manifest do mapa de talhões: são arquivos
            // estáticos, sem dado nenhum, e precisam responder mesmo com a
            // sessão expirada — é o que mantém a tela funcionando no campo.
            // Portal das empresas de fora (bdo.usinasclotilde.com.br): a tela
            // de login e o endpoint dela precisam responder sem sessão, como
            // o login interno.
            uri.equals(ctx + "/manobra-login.html")       ||
            uri.startsWith(ctx + "/api/externo/login")    ||
            uri.equals(ctx + "/sw-mapa.js")               ||
            uri.equals(ctx + "/mapa-talhoes.webmanifest") ||
            uri.startsWith(ctx + "/api/login")            ||
            uri.startsWith(ctx + "/api/esqueci-senha")     ||
            uri.startsWith(ctx + "/api/redefinir-senha")   ||
            uri.startsWith(ctx + "/css/")                 ||
            uri.startsWith(ctx + "/js/")                  ||
            uri.startsWith(ctx + "/img/")                 ||
            // Rotas com token opaco próprio na URL (ex.: link de PDF clicado
            // a partir do chat, sem sessão de navegador nem X-Agro-Api-Key) —
            // cada servlet valida o token sozinho, por isso ficam liberadas aqui.
            uri.startsWith(ctx + "/api/publico/")         ||
            // Endpoints internos (ex.: bootstrap de sessão pro Chromium
            // headless gerar PDF de relatório agendado) — cada servlet exige
            // que a requisição venha do próprio loopback (127.0.0.1/::1),
            // inalcançável de fora já que o Tomcat só é exposto via nginx.
            uri.startsWith(ctx + "/api/interno/");

        if (liberado) {
            chain.doFilter(req, res);
            return;
        }

        HttpSession session = hreq.getSession(false);

        // ── Sessão de empresa de fora ────────────────────────────────────
        // Quem entra pelo portal de manobra tem sessão válida, mas válida
        // apenas para o módulo de manobra. Sem esta separação, "tem sessão"
        // bastaria para chegar no fluxo de caixa e na folha — uma sessão
        // válida é uma sessão válida, e a diferença precisa ser explícita.
        if (session != null && Boolean.TRUE.equals(session.getAttribute("externo"))) {
            if (permitidoAoExterno(uri, ctx)) {
                chain.doFilter(req, res);
            } else {
                negar(hresp, hreq, ctx);
            }
            return;
        }

        // Verificar sessão
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
            hresp.sendRedirect(ctx + (ehDominioExterno(hreq) ? "/manobra-login.html" : "/login.html"));
            return;
        }

        chain.doFilter(req, res);
    }

    /** Host do portal das empresas de fora. */
    private static final String HOST_PORTAL = "bdo.usinasclotilde.com.br";

    private boolean ehDominioExterno(HttpServletRequest req) {
        String host = req.getHeader("Host");
        if (host == null) return false;
        int p = host.indexOf(':');            // Host vem com a porta em teste
        if (p >= 0) host = host.substring(0, p);
        return HOST_PORTAL.equalsIgnoreCase(host.trim());
    }

    /**
     * O que o subdomínio serve: o portal e o que ele precisa para desenhar.
     * Fora disto, 404 — nem redirecionamento, que já contaria que existe
     * algo ali.
     */
    private boolean doPortal(String uri, String ctx) {
        return uri.equals(ctx + "/manobra-login.html")
            || uri.equals(ctx + "/manobra.html")
            || uri.startsWith(ctx + "/api/externo/")
            || uri.startsWith(ctx + "/api/manobra/")
            || uri.startsWith(ctx + "/css/")
            || uri.startsWith(ctx + "/js/")
            || uri.startsWith(ctx + "/img/");
    }

    /**
     * O que uma empresa de fora alcança. Lista fechada, e não uma lista de
     * proibições: o que aparecer de novo na intranet nasce fora do alcance
     * dela, que é o lado certo para errar.
     */
    private boolean permitidoAoExterno(String uri, String ctx) {
        return uri.equals(ctx + "/manobra.html")
            || uri.startsWith(ctx + "/api/manobra/")
            || uri.startsWith(ctx + "/api/externo/");
    }

    private void negar(HttpServletResponse hresp, HttpServletRequest hreq, String ctx)
            throws IOException {
        String accept = hreq.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            hresp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            hresp.setContentType("application/json;charset=UTF-8");
            hresp.getWriter().print("{\"ok\":false,\"erro\":\"Sem permissão para este recurso\"}");
            hresp.getWriter().flush();
            return;
        }
        hresp.sendRedirect(ctx + "/manobra.html");
    }
}
