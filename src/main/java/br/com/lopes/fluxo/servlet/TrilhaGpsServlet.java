package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.TrilhaGpsDAO;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Trilhas de GPS gravadas no campo. Cada usuário só enxerga e só mexe nas
 * suas: o id vem da sessão, nunca do corpo da requisição.
 *
 * GET    /api/trilha-gps            -> minhas trilhas (sem os pontos)
 * GET    /api/trilha-gps/{id}       -> os pontos de uma trilha minha
 * POST   /api/trilha-gps            -> envia uma trilha gravada no aparelho
 *          Body: { idLocal, nome, inicio, fim, duracaoS, distanciaM, pontos:[[lat,lng,t],...] }
 * DELETE /api/trilha-gps?id=N       -> exclui uma trilha minha
 *
 * O envio é idempotente pelo idLocal: a trilha fica no aparelho esperando
 * rede e pode ser mandada várias vezes sem duplicar.
 */
@WebServlet("/api/trilha-gps/*")
public class TrilhaGpsServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(TrilhaGpsServlet.class.getName());
    private static final Gson GSON = new Gson();
    private static final int LIMITE_LISTA = 200;

    private final TrilhaGpsDAO dao = new TrilhaGpsDAO();

    /** @return id do usuário logado, ou 0 quando não há sessão */
    private long idUsuario(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        Object v = s == null ? null : s.getAttribute("idUsuario");
        return v instanceof Number n ? n.longValue() : 0;
    }

    private String nomeUsuario(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        Object v = s == null ? null : s.getAttribute("nome");
        return v == null ? null : String.valueOf(v);
    }

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

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        long usuario = idUsuario(req);
        if (usuario == 0) { erro(resp, 401, "Sessão expirada"); return; }
        String path = req.getPathInfo();
        try {
            if (path != null && path.length() > 1) {
                String id = path.substring(1);
                if (!id.matches("\\d+")) { erro(resp, 400, "Id inválido"); return; }
                String pontos = dao.pontos(usuario, Integer.parseInt(id));
                if (pontos == null) { erro(resp, 404, "Trilha não encontrada"); return; }
                json(resp, "{\"ok\":true,\"pontos\":" + pontos + "}");
                return;
            }
            json(resp, "{\"ok\":true,\"data\":" + GSON.toJson(dao.listar(usuario, LIMITE_LISTA)) + "}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao consultar trilhas de GPS", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        long usuario = idUsuario(req);
        if (usuario == 0) { erro(resp, 401, "Sessão expirada"); return; }
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = req.getReader()) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            }
            JsonObject b = JsonParser.parseString(sb.toString()).getAsJsonObject();

            String idLocal = texto(b, "idLocal");
            if (idLocal == null || idLocal.isBlank()) { erro(resp, 400, "idLocal é obrigatório"); return; }

            if (!b.has("pontos") || !b.get("pontos").isJsonArray()) { erro(resp, 400, "Trilha sem pontos"); return; }
            JsonArray pontos = b.getAsJsonArray("pontos");
            if (pontos.isEmpty()) { erro(resp, 400, "Trilha sem pontos"); return; }
            if (pontos.size() > TrilhaGpsDAO.MAX_PONTOS) {
                erro(resp, 400, "Trilha longa demais (" + pontos.size() + " pontos)");
                return;
            }

            int id = dao.salvar(usuario, nomeUsuario(req), texto(b, "nome"), idLocal,
                    texto(b, "inicio"), texto(b, "fim"),
                    inteiro(b, "duracaoS"), inteiro(b, "distanciaM"),
                    pontos.size(), pontos.toString());

            json(resp, "{\"ok\":true,\"id\":" + id + "}");

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao gravar trilha de GPS", e);
            erro(resp, 500, e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Corpo inválido ao gravar trilha de GPS", e);
            erro(resp, 400, "Requisição inválida: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        long usuario = idUsuario(req);
        if (usuario == 0) { erro(resp, 401, "Sessão expirada"); return; }
        String id = req.getParameter("id");
        if (id == null || !id.matches("\\d+")) { erro(resp, 400, "Parâmetro id é obrigatório e numérico"); return; }
        try {
            boolean apagou = dao.excluir(usuario, Integer.parseInt(id));
            if (!apagou) { erro(resp, 404, "Trilha não encontrada"); return; }
            json(resp, "{\"ok\":true}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao excluir trilha de GPS", e);
            erro(resp, 500, e.getMessage());
        }
    }

    private static String texto(JsonObject o, String campo) {
        return o.has(campo) && !o.get(campo).isJsonNull() ? o.get(campo).getAsString() : null;
    }

    private static int inteiro(JsonObject o, String campo) {
        try {
            return o.has(campo) && !o.get(campo).isJsonNull() ? o.get(campo).getAsInt() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
