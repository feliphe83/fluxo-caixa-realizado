package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AgricolaInsumoDAO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API interna consumida pelo agente de IA (via n8n) — não é usada pelo
 * front-end web. Autenticação por header X-Agro-Api-Key (ver AuthFilter),
 * não por sessão de login.
 *
 * GET /api/agricola/insumos?dataIni=YYYY-MM-DD&dataFim=YYYY-MM-DD&codSafra=(opcional)
 *
 * Resposta: { "ok": true, "data": [ { ...colunas da consulta... }, ... ] }
 */
@WebServlet("/api/agricola/insumos")
public class AgricolaInsumoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AgricolaInsumoServlet.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Gson gson = new Gson();
    private final AgricolaInsumoDAO dao = new AgricolaInsumoDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            LocalDate dataIni = parseDate(req.getParameter("dataIni"));
            LocalDate dataFim = parseDate(req.getParameter("dataFim"));
            String codSafra = req.getParameter("codSafra");

            if (dataIni == null || dataFim == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Parâmetros dataIni e dataFim são obrigatórios (YYYY-MM-DD)\"}");
                return;
            }
            if (dataFim.isBefore(dataIni)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"dataFim não pode ser anterior a dataIni\"}");
                return;
            }

            List<Map<String, Object>> lista = dao.buscar(dataIni, dataFim, codSafra);

            JsonObject resultado = new JsonObject();
            resultado.addProperty("ok", true);
            resultado.add("data", gson.toJsonTree(lista));
            out.print(gson.toJson(resultado));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no servlet agricola-insumos", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim(), FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
