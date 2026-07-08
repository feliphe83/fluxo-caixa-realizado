package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.FluxoRealizadoDAO;
import br.com.lopes.fluxo.model.FluxoRealizadoItem;
import com.google.gson.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API REST interna que retorna os dados do fluxo realizado em JSON.
 *
 * GET /api/fluxo-realizado?dataIni=YYYY-MM-DD&dataFim=YYYY-MM-DD
 *
 * Resposta de sucesso:
 *   { "ok": true, "data": [ { ... }, ... ] }
 *
 * Resposta de erro:
 *   { "ok": false, "erro": "mensagem" }
 */
@WebServlet("/api/fluxo-realizado")
public class FluxoRealizadoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(FluxoRealizadoServlet.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class,
                    (JsonSerializer<LocalDate>) (src, t, ctx) ->
                            new JsonPrimitive(src.format(FMT)))
            .serializeNulls()
            .create();

    private final FluxoRealizadoDAO dao = new FluxoRealizadoDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        PrintWriter out = resp.getWriter();
        try {
            LocalDate dataIni = parseDate(req.getParameter("dataIni"));
            LocalDate dataFim = parseDate(req.getParameter("dataFim"));

            if (dataIni == null || dataFim == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Parâmetros dataIni e dataFim são obrigatórios\"}");
                return;
            }

            if (dataFim.isBefore(dataIni)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"dataFim não pode ser anterior a dataIni\"}");
                return;
            }

            List<FluxoRealizadoItem> lista = dao.buscar(dataIni, dataFim);

            // ── Filtros opcionais do Comparativo ──────────────────────────
            // dataEntradaMin: desconsiderar entradas anteriores a essa data
            String dataEntradaMinStr = req.getParameter("dataEntradaMin");
            if (dataEntradaMinStr != null && !dataEntradaMinStr.isBlank()) {
                java.time.LocalDate dataEntradaMin = parseDate(dataEntradaMinStr);
                if (dataEntradaMin != null) {
                    final java.time.LocalDate dem = dataEntradaMin;
                    lista = lista.stream()
                        .filter(i -> i.getDataEntrada() == null || !i.getDataEntrada().isBefore(dem))
                        .collect(java.util.stream.Collectors.toList());
                }
            }

            // descCana=S: desconsiderar Fornecedor de Cana (cod_empenho = 493)
            String descCana = req.getParameter("descCana");
            if ("S".equals(descCana)) {
                lista = lista.stream()
                    .filter(i -> !"493".equals(i.getCodEmpenho()))
                    .collect(java.util.stream.Collectors.toList());
            }
            // ─────────────────────────────────────────────────────────────

            JsonObject resultado = new JsonObject();
            resultado.addProperty("ok", true);
            resultado.add("data", gson.toJsonTree(lista));
            out.print(gson.toJson(resultado));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no servlet fluxo-realizado", e);
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : e.getClass().getName();
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

    // ─── DTO de erro ─────────────────────────────────────────────────────
    private record ApiError(boolean ok, String erro) {
        ApiError(String msg) { this(false, msg); }
    }
}
