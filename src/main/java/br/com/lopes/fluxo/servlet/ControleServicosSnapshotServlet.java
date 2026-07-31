package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.ServicoSnapshotDAO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fotografias do Controle de Serviços — gravar o que está na tela e comparar
 * depois com o que o Oracle passou a devolver para o mesmo período.
 *
 * GET    /api/controle-servicos/snapshot?dataIni=&dataFim=  -> gravações do período
 * GET    /api/controle-servicos/snapshot/{id}               -> itens de uma gravação
 * POST   /api/controle-servicos/snapshot                    -> grava
 *          Body: { dataIni, dataFim, fazendas, fornecedores, descricao, itens:[...] }
 *          "itens" são as linhas cruas de /api/controle-servicos, do jeito
 *          que a tela recebeu — a comparação depois é feita no navegador,
 *          em qualquer nível de agrupamento.
 * DELETE /api/controle-servicos/snapshot?id=N               -> exclui
 *
 * Exige sessão de login (o AuthFilter já garante).
 */
@WebServlet("/api/controle-servicos/snapshot/*")
public class ControleServicosSnapshotServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ControleServicosSnapshotServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final ServicoSnapshotDAO dao = new ServicoSnapshotDAO();

    private void json(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().print(body);
        resp.getWriter().flush();
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        json(resp, "{\"ok\":false,\"erro\":\"" + String.valueOf(msg).replace("\"", "'").replace("\n", " ") + "\"}");
    }

    private static boolean dataValida(String v) {
        if (v == null || v.isBlank()) return false;
        try {
            LocalDate.parse(v);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        try {
            if (path != null && path.length() > 1) {
                String id = path.substring(1);
                if (!id.matches("\\d+")) { erro(resp, 400, "Id inválido"); return; }
                json(resp, "{\"ok\":true,\"data\":" + GSON.toJson(dao.itens(Integer.parseInt(id))) + "}");
                return;
            }
            String dataIni = req.getParameter("dataIni");
            String dataFim = req.getParameter("dataFim");
            // Sem período válido, lista tudo — a tela só usa o modo filtrado,
            // mas deixar cair no geral evita erro se alguém abrir sem filtro.
            boolean filtra = dataValida(dataIni) && dataValida(dataFim);
            json(resp, "{\"ok\":true,\"data\":" + GSON.toJson(
                    dao.listar(filtra ? dataIni : null, filtra ? dataFim : null)) + "}");

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao consultar gravações do controle de serviços", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = req.getReader()) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            }
            JsonObject b = JsonParser.parseString(sb.toString()).getAsJsonObject();

            String dataIni = texto(b, "dataIni");
            String dataFim = texto(b, "dataFim");
            if (!dataValida(dataIni) || !dataValida(dataFim)) { erro(resp, 400, "Período inválido"); return; }
            if (dataFim.compareTo(dataIni) < 0) { erro(resp, 400, "Data fim anterior à data início"); return; }

            if (!b.has("itens") || !b.get("itens").isJsonArray()) { erro(resp, 400, "Nada para gravar"); return; }
            JsonArray itens = b.getAsJsonArray("itens");
            if (itens.isEmpty()) { erro(resp, 400, "Gere o relatório antes de gravar"); return; }
            if (itens.size() > ServicoSnapshotDAO.MAX_ITENS) {
                erro(resp, 400, "Muitas linhas para gravar de uma vez (" + itens.size()
                        + "). Reduza o período ou filtre por fazenda/fornecedor.");
                return;
            }

            HttpSession s = req.getSession(false);
            long idUsuario = s != null && s.getAttribute("idUsuario") instanceof Number n ? n.longValue() : 0;
            String nome = s != null && s.getAttribute("nome") != null ? String.valueOf(s.getAttribute("nome")) : null;

            int id = dao.gravar(dataIni, dataFim, texto(b, "fazendas"), texto(b, "fornecedores"),
                    texto(b, "descricao"), idUsuario, nome, itens);

            json(resp, "{\"ok\":true,\"id\":" + id + ",\"itens\":" + itens.size() + "}");

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao gravar o controle de serviços", e);
            erro(resp, 500, e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Corpo inválido ao gravar o controle de serviços", e);
            erro(resp, 400, "Requisição inválida: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String id = req.getParameter("id");
        if (id == null || !id.matches("\\d+")) { erro(resp, 400, "Parâmetro id é obrigatório e numérico"); return; }
        try {
            dao.excluir(Integer.parseInt(id));
            json(resp, "{\"ok\":true}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao excluir gravação do controle de serviços", e);
            erro(resp, 500, e.getMessage());
        }
    }

    private static String texto(JsonObject o, String campo) {
        return o.has(campo) && !o.get(campo).isJsonNull() ? o.get(campo).getAsString().trim() : null;
    }
}
