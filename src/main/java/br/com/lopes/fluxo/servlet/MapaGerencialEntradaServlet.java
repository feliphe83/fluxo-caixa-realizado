package br.com.lopes.fluxo.servlet;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Ponte de login para o sistema Mapa Gerencial (contexto separado
 * /mapagerencial, jars antigos isolados).
 *
 * O usuário clica em um único link no Hub; a intranet (já autenticada pelo
 * AuthFilter) gera um token curto assinado (HMAC) com o id do usuário e um
 * prazo de 1 minuto e redireciona para /mapagerencial/entrada.jsp?tk=...
 * A entrada.jsp valida o token com o MESMO segredo (as duas apps rodam no
 * mesmo Tomcat) e marca a sessão dela como autenticada — sem tela de login.
 *
 * O segredo vem de MAPA_GERENCIAL_SECRET (env); se não estiver definido, cai
 * num valor interno padrão — o mesmo embutido na entrada.jsp.
 */
@WebServlet("/ir-mapa-gerencial")
public class MapaGerencialEntradaServlet extends HttpServlet {

    /** Precisa ser idêntico ao de entrada.jsp. Sobreponível por env. */
    static final String SEGREDO_PADRAO = "USC-MapaGerencial-Ponte-2026";

    /** Janela de validade do token, em milissegundos. */
    private static final long VALIDADE_MS = 60_000;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        boolean json = "1".equals(req.getParameter("json"));

        HttpSession sessao = req.getSession(false);
        Object idUsuario = sessao == null ? null : sessao.getAttribute("idUsuario");
        if (idUsuario == null) {
            // AuthFilter normalmente já barra antes de chegar aqui; por garantia.
            if (json) { resp.setContentType("application/json;charset=UTF-8"); resp.getWriter().print("{\"ok\":false}"); }
            else resp.sendRedirect("login.html");
            return;
        }

        String payload = idUsuario + "|" + (System.currentTimeMillis() + VALIDADE_MS);
        String token;
        try {
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                    + "." + assinar(payload, segredo());
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Falha ao gerar token");
            return;
        }

        // Caminho absoluto do host: o Mapa Gerencial é outro contexto (/mapagerencial).
        String destino = "/mapagerencial/entrada.jsp?tk=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        if (json) {
            // Usado pela moldura (mapa-gerencial.html): ela pega a URL já com token e
            // aponta o iframe DIRETO para /mapagerencial, sem cadeia de redirect
            // cruzando contextos dentro do iframe (que quebrava o cookie de sessão).
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().print("{\"ok\":true,\"url\":\"" + destino + "\"}");
        } else {
            resp.sendRedirect(destino);
        }
    }

    static String segredo() {
        String s = System.getenv("MAPA_GERENCIAL_SECRET");
        return (s == null || s.isBlank()) ? SEGREDO_PADRAO : s;
    }

    static String assinar(String payload, String segredo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bruto = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bruto.length * 2);
        for (byte b : bruto) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
