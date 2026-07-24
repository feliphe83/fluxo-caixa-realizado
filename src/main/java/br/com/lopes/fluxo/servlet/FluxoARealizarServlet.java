package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.FinanceiroContasPagarDAO;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tela "Fluxo a Realizar" — usa a MESMA consulta do Dr. Alfredo
 * (FinanceiroContasPagarDAO), mas sem o filtro de provisão: aqui deve vir
 * tudo (provisionado e não provisionado), já que a própria tela tem seu
 * filtro/agrupamento por provisão do lado do cliente (badges "Título",
 * "Provisão", "Manual").
 *
 * GET /api/fluxo-arealizar?dataIni=yyyy-MM-dd&dataFim=yyyy-MM-dd
 */
@WebServlet("/api/fluxo-arealizar")
public class FluxoARealizarServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(FluxoARealizarServlet.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Gson gson = new Gson();
    private final FinanceiroContasPagarDAO dao = new FinanceiroContasPagarDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        LocalDate dataIni = parseDate(req.getParameter("dataIni"));
        LocalDate dataFim = parseDate(req.getParameter("dataFim"));

        if (dataIni == null || dataFim == null) {
            resp.setStatus(400);
            out.print("{\"ok\":false,\"erro\":\"Parâmetros dataIni e dataFim são obrigatórios\"}");
            out.flush();
            return;
        }

        try {
            List<Map<String, Object>> lista = dao.buscar(
                    dataIni.format(FMT), dataFim.format(FMT), null, null, null, false);

            JsonArray arr = new JsonArray();
            for (Map<String, Object> l : lista) {
                JsonObject o = new JsonObject();
                o.addProperty("codContaFluxo", strOf(l.get("conta_fluxo")));
                o.addProperty("descricaoConta", strOf(l.get("desc_fluxo")));
                o.addProperty("codEmpenho", strOf(l.get("cod_empenho")));
                o.addProperty("descEmpenho", strOf(l.get("descricao_empenho")));
                o.addProperty("codFornecedor", strOf(l.get("cod_fornecedor")));
                o.addProperty("nome", strOf(l.get("nome")));
                o.addProperty("descricaoTipoConta", strOf(l.get("desc_contas_pagar")));
                o.addProperty("documento", strOf(l.get("documento")));
                o.addProperty("parcela", strOf(l.get("parcela")));
                o.addProperty("codTipoContasPagar", strOf(l.get("cod_tipocontaspagar")));
                o.addProperty("provisao", strOf(l.get("provisao")));
                o.addProperty("usuario", strOf(l.get("usuario")));
                Object valor = l.get("valor");
                if (valor instanceof Number n) o.addProperty("valor", n);
                o.addProperty("codIndiceFinanceiro", (String) null);
                o.addProperty("dataVcto", dataApenas(strOf(l.get("datavcto"))));
                o.addProperty("dataVctoOrig", dataApenas(strOf(l.get("datavcto_orig"))));
                o.addProperty("dataEntrada", dataApenas(strOf(l.get("dataentrada"))));
                arr.add(o);
            }

            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.add("data", arr);
            out.print(gson.toJson(result));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro fluxo-arealizar", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(500);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    private static String strOf(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** RowMapperUtil devolve DATE/TIMESTAMP como ISO completo (com "T...") — a tela só quer yyyy-MM-dd. */
    private static String dataApenas(String iso) {
        return iso != null && iso.length() >= 10 ? iso.substring(0, 10) : iso;
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
