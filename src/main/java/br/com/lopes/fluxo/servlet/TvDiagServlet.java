package br.com.lopes.fluxo.servlet;

import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Diagnóstico do acesso das telas de TV: mostra qual IP o servidor enxerga do
 * cliente e se ele cai na rede interna — para descobrir por que uma TV está (ou
 * não está) pedindo login.
 *
 * É liberado sem sessão (ver AuthFilter): só devolve o IP e os cabeçalhos da
 * PRÓPRIA requisição, nada sensível. Abra /api/tv-diag da máquina da TV.
 */
@WebServlet("/api/tv-diag")
public class TvDiagServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String encaminhado = AuthFilter.ipEncaminhado(req);
        String usado = encaminhado != null ? encaminhado : req.getRemoteAddr();

        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("remoteAddr", req.getRemoteAddr());
        o.addProperty("xRealIp", req.getHeader("X-Real-IP"));
        o.addProperty("xForwardedFor", req.getHeader("X-Forwarded-For"));
        o.addProperty("ipEncaminhado", encaminhado);
        o.addProperty("ipUsadoNaDecisao", usado);
        o.addProperty("consideradoInterno", AuthFilter.clienteNaRedeInterna(req));
        o.addProperty("dica", "consideradoInterno=true significa que a TV abre sem login. "
                + "Se for false, veja qual IP aparece em ipUsadoNaDecisao e ajuste a faixa, "
                + "ou confira se o nginx envia X-Real-IP / X-Forwarded-For.");

        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().print(o.toString());
        resp.getWriter().flush();
    }
}
