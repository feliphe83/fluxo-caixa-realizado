package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AgricolaTalhaoDAO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API interna consumida pelo agente de IA (via n8n) — não é usada pelo
 * front-end web. Autenticação por header X-Agro-Api-Key (ver AuthFilter),
 * não por sessão de login.
 *
 * GET /api/agricola/talhoes?safra=NNNN
 *
 * Resposta: { "ok": true, "data": [ { ...colunas da consulta... }, ... ] }
 */
@WebServlet("/api/agricola/talhoes")
public class AgricolaTalhaoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AgricolaTalhaoServlet.class.getName());

    private final Gson gson = new Gson();
    private final AgricolaTalhaoDAO dao = new AgricolaTalhaoDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            String safra = req.getParameter("safra");
            if (safra == null || safra.isBlank()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Parâmetro safra é obrigatório\"}");
                return;
            }

            List<Map<String, Object>> lista = dao.buscar(safra.trim());

            JsonObject resultado = new JsonObject();
            resultado.addProperty("ok", true);
            resultado.add("data", gson.toJsonTree(lista));
            out.print(gson.toJson(resultado));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no servlet agricola-talhoes", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }
}
