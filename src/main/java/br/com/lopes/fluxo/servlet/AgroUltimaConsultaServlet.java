package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.AgroConsultaCache;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Devolve ao front-end do chat agrícola os resultados brutos (completos) das
 * últimas consultas ao banco feitas pelo agente de IA na sessão informada —
 * usado pelos botões/pedidos de exportação em Excel e PDF. Uma pergunta pode
 * gerar mais de uma consulta (ex.: comparativo entre duas safras), por isso
 * a resposta é uma lista.
 *
 * Autenticação por sessão de login normal (AuthFilter), como as demais
 * rotas /api/ia/*. Não confundir com /api/agricola/* (chave de API, n8n).
 *
 * GET /api/ia/agro-ultima-consulta?sessionId=...&desde=<timestamp ms, opcional>
 *   Resposta: { "ok": true, "consultas": [
 *                 { "titulo": "...", "timestamp": 173..., "totalLinhas": N,
 *                   "data": [ {...} ] } ] }
 */
@WebServlet("/api/ia/agro-ultima-consulta")
public class AgroUltimaConsultaServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        String sessionId = req.getParameter("sessionId");
        long desde = 0L;
        String desdeParam = req.getParameter("desde");
        if (desdeParam != null && !desdeParam.isBlank()) {
            try {
                desde = Long.parseLong(desdeParam.trim());
            } catch (NumberFormatException ignored) { }
        }

        List<AgroConsultaCache.Entrada> entradas = AgroConsultaCache.obter(sessionId, desde);

        JsonArray consultas = new JsonArray();
        for (AgroConsultaCache.Entrada e : entradas) {
            JsonObject c = new JsonObject();
            c.addProperty("titulo", e.titulo);
            c.addProperty("timestamp", e.timestamp);
            c.addProperty("totalLinhas", e.dados.size());
            c.add("data", gson.toJsonTree(e.dados));
            consultas.add(c);
        }

        JsonObject json = new JsonObject();
        json.addProperty("ok", true);
        json.add("consultas", consultas);

        out.print(gson.toJson(json));
        out.flush();
    }
}
