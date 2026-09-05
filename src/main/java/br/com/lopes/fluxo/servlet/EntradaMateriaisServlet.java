package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.EntradaMateriaisDAO;
import br.com.lopes.fluxo.util.DataParamUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API da tela de Entrada de Materiais (entrada-materiais.html).
 *
 * GET /api/entrada-materiais?dataIni=yyyy-MM-dd&dataFim=yyyy-MM-dd
 *
 * Devolve uma linha por item de nota fiscal de entrada no período, com todas
 * as colunas que material.tmp_rel_entradasperiodo trouxer (o front-end monta
 * o Excel genericamente a partir das chaves de cada linha — ver
 * EntradaMateriaisDAO para o porquê de não haver uma lista fixa de colunas
 * aqui no servidor).
 *
 * serializeNulls() é obrigatório aqui: o Gson padrão OMITE do JSON as chaves
 * cujo valor é null, e o front-end usa as chaves da PRIMEIRA linha para
 * montar o cabeçalho do Excel — se essa linha tivesse uma coluna nula (comum
 * num relatório com dezenas de colunas), o cabeçalho sairia sem ela, e as
 * colunas desalinhariam nas linhas seguintes que não têm aquela coluna nula.
 */
@WebServlet("/api/entrada-materiais")
public class EntradaMateriaisServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(EntradaMateriaisServlet.class.getName());
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final EntradaMateriaisDAO dao = new EntradaMateriaisDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        String dataIni = DataParamUtil.normalizar(req.getParameter("dataIni"));
        String dataFim = DataParamUtil.normalizar(req.getParameter("dataFim"));
        if (dataIni == null || dataFim == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"ok\":false,\"erro\":\"Informe o período (dataIni e dataFim)\"}");
            return;
        }

        try {
            List<Map<String, Object>> linhas =
                    dao.buscar(LocalDate.parse(dataIni), LocalDate.parse(dataFim));

            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.addProperty("dataIni", dataIni);
            r.addProperty("dataFim", dataFim);
            r.add("linhas", gson.toJsonTree(linhas));
            out.print(gson.toJson(r));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro na entrada de materiais: " + e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":" + gson.toJson("Falha ao consultar: " + e.getMessage()) + "}");
        }
    }
}
