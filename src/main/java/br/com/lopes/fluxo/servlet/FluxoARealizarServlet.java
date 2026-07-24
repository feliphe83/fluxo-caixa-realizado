package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.FinanceiroContasPagarDAO;
import com.google.gson.stream.JsonWriter;

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
                    dataIni.format(FMT), dataFim.format(FMT), null, null, null, null, false);

            // Escreve o JSON direto no PrintWriter (streaming), sem montar um
            // JsonArray/JsonObject gigante em memória primeiro — a mesma
            // causa de um OutOfMemoryError já visto em produção no
            // FluxoRealizadoServlet, e essa consulta pode devolver ainda
            // mais linhas (sem filtro de provisão).
            JsonWriter writer = new JsonWriter(out);
            writer.beginObject();
            writer.name("ok").value(true);
            writer.name("data").beginArray();
            for (Map<String, Object> l : lista) {
                writer.beginObject();
                writer.name("codContaFluxo").value(strOf(l.get("conta_fluxo")));
                writer.name("descricaoConta").value(strOf(l.get("desc_fluxo")));
                writer.name("codEmpenho").value(strOf(l.get("cod_empenho")));
                writer.name("descEmpenho").value(strOf(l.get("descricao_empenho")));
                writer.name("codFornecedor").value(strOf(l.get("cod_fornecedor")));
                writer.name("nome").value(strOf(l.get("nome")));
                writer.name("descricaoTipoConta").value(strOf(l.get("desc_contas_pagar")));
                writer.name("documento").value(strOf(l.get("documento")));
                writer.name("parcela").value(strOf(l.get("parcela")));
                writer.name("codTipoContasPagar").value(strOf(l.get("cod_tipocontaspagar")));
                writer.name("provisao").value(strOf(l.get("provisao")));
                writer.name("usuario").value(strOf(l.get("usuario")));
                Object valor = l.get("valor");
                writer.name("valor").value(valor instanceof Number n ? n : null);
                writer.name("codIndiceFinanceiro").nullValue();
                writer.name("dataVcto").value(dataApenas(strOf(l.get("datavcto"))));
                writer.name("dataVctoOrig").value(dataApenas(strOf(l.get("datavcto_orig"))));
                writer.name("dataEntrada").value(dataApenas(strOf(l.get("dataentrada"))));
                writer.endObject();
            }
            writer.endArray();
            writer.endObject();
            writer.flush();

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
