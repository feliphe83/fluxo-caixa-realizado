package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.PagamentoCanaDAO;
import br.com.lopes.fluxo.util.DataParamUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
 * API da tela de Controle de Pagamento a Fornecedor de Cana (pagamento-cana.html).
 *
 * GET /api/pagamento-cana?safra=74&entIni=yyyy-MM-dd&entFim=yyyy-MM-dd
 *        &consecana=yyyy-MM-dd&pagIni=yyyy-MM-dd&pagFim=yyyy-MM-dd
 *
 * Todos os parâmetros têm padrão (os valores da consulta original), então a
 * chamada sem parâmetros já devolve o resumo da safra 74.
 */
@WebServlet("/api/pagamento-cana")
public class PagamentoCanaServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(PagamentoCanaServlet.class.getName());
    private final Gson gson = new Gson();
    private final PagamentoCanaDAO dao = new PagamentoCanaDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            int safra = parseInt(req.getParameter("safra"), 74);
            String entIni    = ou(DataParamUtil.normalizar(req.getParameter("entIni")),    "2025-09-01");
            String entFim    = ou(DataParamUtil.normalizar(req.getParameter("entFim")),    "2026-03-01");
            String consecana = ou(DataParamUtil.normalizar(req.getParameter("consecana")), "2026-02-28");
            String pagIni    = ou(DataParamUtil.normalizar(req.getParameter("pagIni")),    entIni);
            String pagFim    = ou(DataParamUtil.normalizar(req.getParameter("pagFim")),    hoje());

            List<Map<String, Object>> linhas = dao.resumo(safra, entIni, entFim, consecana, pagIni, pagFim);

            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.addProperty("safra", safra);
            r.addProperty("entIni", entIni);
            r.addProperty("entFim", entFim);
            r.addProperty("consecana", consecana);
            r.addProperty("pagIni", pagIni);
            r.addProperty("pagFim", pagFim);
            r.add("fornecedores", gson.toJsonTree(linhas));
            out.print(gson.toJson(r));

        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"ok\":false,\"erro\":" + gson.toJson(e.getMessage()) + "}");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no pagamento de cana: " + e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":" + gson.toJson("Falha ao consultar: " + e.getMessage()) + "}");
        }
    }

    private static String ou(String v, String padrao) { return (v == null || v.isBlank()) ? padrao : v; }
    private static String hoje() { return java.time.LocalDate.now().toString(); }
    private static int parseInt(String v, int padrao) {
        try { return (v == null || v.isBlank()) ? padrao : Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return padrao; }
    }
}
