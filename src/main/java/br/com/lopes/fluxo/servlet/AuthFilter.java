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
            // Diagnóstico do acesso das telas de TV: só ecoa o IP e os
            // cabeçalhos da própria requisição, para achar por que uma TV
            // pede (ou não) login. Nada sensível.
            uri.equals(ctx + "/api/tv-diag")              ||
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

        // ── Telas de TV na rede interna ──────────────────────────────────
        // Parede de TV não tem quem digite login. As telas de TV (e as APIs
        // que elas consultam) abrem SEM sessão, mas SÓ para quem vem de um IP
        // da rede interna da usina — de fora continua exigindo usuário e
        // senha. Mostram dado sensível (DRE, balanço, faturamento), então a
        // rede é a fronteira. No domínio externo isto nem é alcançado: lá
        // tudo que não é o portal já virou 404 lá em cima.
        if (rotaTvInterna(uri, ctx) && clienteNaRedeInterna(hreq)) {
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

    /** As telas de TV, as APIs que elas consultam e os scripts de raiz que carregam. */
    private static boolean rotaTvInterna(String uri, String ctx) {
        return uri.equals(ctx + "/balanco-tv.html")
            || uri.equals(ctx + "/cana-tv.html")
            || uri.equals(ctx + "/demonstrativo-tv.html")
            || uri.equals(ctx + "/diesel-tv.html")
            || uri.equals(ctx + "/faturamento-tv.html")
            || uri.equals(ctx + "/indicadores-tv.html")
            || uri.equals(ctx + "/orcamento-tv.html")
            // scripts de raiz que essas telas carregam (não estão em /js/)
            || uri.equals(ctx + "/indicadores-comum.js")
            || uri.equals(ctx + "/balanco-historico.js")
            || uri.equals(ctx + "/balanco-quadro.js")
            || uri.equals(ctx + "/dre-historico.js")
            // as APIs consultadas por cada tela
            || uri.startsWith(ctx + "/api/balanco-patrimonial")
            || uri.startsWith(ctx + "/api/cana-entrada")
            || uri.startsWith(ctx + "/api/demonstrativo-financeiro")
            || uri.startsWith(ctx + "/api/diesel-recebimento")
            || uri.startsWith(ctx + "/api/faturamento-vendas")
            || uri.startsWith(ctx + "/api/indicadores")
            || uri.startsWith(ctx + "/api/orcamento-compras");
    }

    /**
     * O cliente vem de um IP da rede interna da usina?
     *
     * O Tomcat fica atrás do nginx por loopback, então quando o pedido chega
     * de 127.x/::1 o IP real do cliente está no cabeçalho que o nginx põe:
     * X-Real-IP, ou o ÚLTIMO da X-Forwarded-For — o que o nginx acrescenta, e
     * que o cliente não consegue forjar (ele só prepende os anteriores). Sem
     * esse cabeçalho atrás do proxy, NEGA: melhor a TV pedir login do que
     * liberar sem saber de onde vem. No acesso direto ao Tomcat (sem proxy) o
     * próprio IP de origem decide.
     */
    static boolean clienteNaRedeInterna(HttpServletRequest req) {
        // Se HÁ cabeçalho de proxy, é ele que diz o IP real do cliente —
        // independentemente de qual IP o Tomcat vê na conexão com o nginx.
        // Era esse o furo: quando o nginx conecta ao Tomcat por um IP que não
        // é loopback, olhar só o getRemoteAddr negava todo mundo.
        String real = ipEncaminhado(req);
        if (real != null) return ehRedeInterna(real);

        // Sem cabeçalho de proxy: ou é acesso direto (o peer é o cliente), ou
        // é um proxy que não informou nada — e aí, se o peer é loopback, não
        // dá para saber de onde vem: NEGA (melhor pedir login).
        String peer = req.getRemoteAddr();
        if (ehLoopback(peer)) return false;
        return ehRedeInterna(peer);
    }

    /**
     * O IP real do cliente informado pelo proxy.
     *
     * O site fica atrás do Cloudflare, então o IP VERDADEIRO do visitante vem
     * no CF-Connecting-IP (a Cloudflare o define e sobrescreve — não dá para
     * forjar por cabeçalho); True-Client-IP é o mesmo no plano Enterprise. O
     * X-Real-IP aqui é o IP do próprio Cloudflare, não serve. Na falta dos de
     * cima, cai no último da X-Forwarded-For.
     */
    static String ipEncaminhado(HttpServletRequest req) {
        for (String h : new String[]{ "CF-Connecting-IP", "True-Client-IP", "X-Real-IP" }) {
            String v = req.getHeader(h);
            if (v != null && !v.isBlank()) return v.trim();
        }
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] p = xff.split(",");
            return p[p.length - 1].trim();
        }
        return null;
    }

    private static boolean ehLoopback(String ip) {
        if (ip == null) return false;
        return ip.startsWith("127.") || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }

    /**
     * A rede interna da usina.
     *
     * A LAN da usina usa o bloco 123.0.0.x (endereço público usado por dentro,
     * não uma faixa RFC1918) — é essa a fronteira que libera a TV. Se a sub-rede
     * mudar, é aqui que se troca "123.0.0.".
     *
     * As faixas privadas de sempre (10/8, 172.16-31/12, 192.168/16, loopback e
     * o equivalente IPv6) ficam também porque nunca fazem mal: um IP dessas
     * faixas não trafega pela internet, então jamais aparece como o cliente
     * real vindo de fora.
     */
    static boolean ehRedeInterna(String ip) {
        if (ip == null || ip.isBlank()) return false;
        ip = ip.trim();
        if (ip.startsWith("::ffff:")) ip = ip.substring(7);   // IPv4 embutido em IPv6
        if (ehLoopback(ip)) return true;
        // Como o site passa pelo Cloudflare, quem está na usina chega com o IP
        // PÚBLICO de saída da rede (o NAT esconde o 123.0.0.x). Esse IP de
        // saída é a fronteira real da "rede interna" vista de fora.
        if (ip.equals("131.161.27.96")) return true;          // saída pública da usina
        if (ip.startsWith("123.0.0.")) return true;           // a LAN da usina (acesso direto, sem Cloudflare)
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) return true;
        if (ip.startsWith("172.")) {
            try {
                int seg = Integer.parseInt(ip.split("\\.")[1]);
                return seg >= 16 && seg <= 31;
            } catch (NumberFormatException e) { return false; }
        }
        String low = ip.toLowerCase();
        return low.startsWith("fc") || low.startsWith("fd") || low.startsWith("fe80");  // IPv6 privado/link-local
    }
}
