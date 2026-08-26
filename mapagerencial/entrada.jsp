<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="javax.crypto.Mac, javax.crypto.spec.SecretKeySpec, java.nio.charset.StandardCharsets, java.util.Base64" %>
<%!
    // Precisa ser IDÊNTICO ao SEGREDO_PADRAO de MapaGerencialEntradaServlet.
    static final String SEGREDO_PADRAO = "USC-MapaGerencial-Ponte-2026";
    // Tela inicial após entrar (o "mapa gerencial" / entrada de cana).
    static final String HOME = "boletim/index.jsp";

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
    // Comparação em tempo constante para não vazar o segredo por timing.
    static boolean igual(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
%>
<%
    boolean ok = false;
    String tk = request.getParameter("tk");
    if (tk != null && tk.indexOf('.') > 0) {
        try {
            int p = tk.lastIndexOf('.');
            String payloadB64 = tk.substring(0, p);
            String sig = tk.substring(p + 1);
            String payload = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
            if (igual(sig, assinar(payload, segredo()))) {
                int barra = payload.lastIndexOf('|');
                long expira = Long.parseLong(payload.substring(barra + 1));
                if (System.currentTimeMillis() <= expira) {
                    session.setAttribute("autenticado", Boolean.TRUE);
                    session.setAttribute("idUsuarioIntranet", payload.substring(0, barra));
                    ok = true;
                }
            }
        } catch (Exception e) {
            ok = false;
        }
    }

    if (ok) {
        response.sendRedirect(HOME);
    } else {
        // Sem token válido (acesso direto ou expirado): volta pela ponte da
        // intranet, que exige o nosso login.
        response.sendRedirect("/ir-mapa-gerencial");
    }
%>
