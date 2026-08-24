package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.FechamentoFreteDAO;
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
 * API da tela de Fechamento de Fretes de Transporte de Pessoal
 * (fechamento-frete.html).
 *
 * GET /api/fechamento-frete?dataIni=yyyy-MM-dd&dataFim=yyyy-MM-dd[&contrato=NNN]
 *
 * Devolve uma linha por prestador (fornecedor do apontamento de terceiro) com
 * os campos que vêm do Oracle — nº de equipamentos, diárias, kms rodados,
 * colaboradores, valor bruto, litros e valor de combustível. Os derivados e o
 * valor líquido (bruto - combustível) são calculados no navegador.
 */
@WebServlet("/api/fechamento-frete")
public class FechamentoFreteServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(FechamentoFreteServlet.class.getName());
    private final Gson gson = new Gson();
    private final FechamentoFreteDAO dao = new FechamentoFreteDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            String dataIni = DataParamUtil.normalizar(req.getParameter("dataIni"));
            String dataFim = DataParamUtil.normalizar(req.getParameter("dataFim"));
            if (dataIni == null || dataFim == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Informe o período (dataIni e dataFim)\"}");
                return;
            }

            String contrato = req.getParameter("contrato");
            List<Map<String, Object>> prestadores = dao.resumoPorPrestador(dataIni, dataFim, contrato);

            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.addProperty("dataIni", dataIni);
            r.addProperty("dataFim", dataFim);
            if (contrato != null && !contrato.isBlank()) r.addProperty("contrato", contrato.trim());
            r.add("prestadores", gson.toJsonTree(prestadores));
            out.print(gson.toJson(r));

        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"ok\":false,\"erro\":" + gson.toJson(e.getMessage()) + "}");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no fechamento de fretes: " + e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":" + gson.toJson("Falha ao consultar: " + e.getMessage()) + "}");
        }
    }
}
