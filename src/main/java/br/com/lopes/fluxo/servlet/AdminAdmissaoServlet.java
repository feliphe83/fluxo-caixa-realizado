package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AdmissaoDocumentoDAO;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administração dos TIPOS de documento de admissão (Administração → Admissão
 * de Funcionários). Só administradores — é aqui que se decide o que todo
 * candidato vai precisar enviar, então é configuração de sistema, não uma
 * tela de trabalho do dia a dia (essa é {@link AdmissaoControleServlet}).
 *
 * GET    /api/admin/admissao/tipos       -> lista (inclusive inativos)
 * POST   /api/admin/admissao/tipos       -> cria
 * PUT    /api/admin/admissao/tipos/{id}  -> atualiza
 * DELETE /api/admin/admissao/tipos/{id}  -> exclui (ou recusa, se já tem documento enviado — desative em vez de excluir)
 */
@WebServlet("/api/admin/admissao/*")
public class AdminAdmissaoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AdminAdmissaoServlet.class.getName());
    private final Gson gson = new Gson();
    private final AdmissaoDocumentoDAO dao = new AdmissaoDocumentoDAO();

    private boolean isAdmin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("administrador"));
    }

    private void json(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().print(body);
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        json(resp, "{\"ok\":false,\"erro\":" + gson.toJson(msg) + "}");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        try {
            if (!"/tipos".equals(req.getPathInfo())) { erro(resp, 404, "rota desconhecida"); return; }
            dao.garantirEstrutura();
            JsonArray arr = gson.toJsonTree(dao.tiposDocumento(false)).getAsJsonArray();
            json(resp, "{\"ok\":true,\"data\":" + arr + "}");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao listar tipos de documento de admissão", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        try {
            if (!"/tipos".equals(req.getPathInfo())) { erro(resp, 404, "rota desconhecida"); return; }
            JsonObject b = lerBody(req);
            String erroValidacao = validar(b);
            if (erroValidacao != null) { erro(resp, 400, erroValidacao); return; }

            int id = dao.salvarTipoDocumento(null, b.get("nome").getAsString().trim(),
                    b.get("obrigatorio").getAsBoolean(), lerOrdem(b), lerAtivo(b));
            json(resp, "{\"ok\":true,\"id\":" + id + "}");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao criar tipo de documento de admissão", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        String path = req.getPathInfo();
        if (path == null || path.equals("/tipos") || !path.startsWith("/tipos/")) {
            erro(resp, 400, "Informe o id na URL"); return;
        }
        try {
            int id = Integer.parseInt(path.substring("/tipos/".length()));
            JsonObject b = lerBody(req);
            String erroValidacao = validar(b);
            if (erroValidacao != null) { erro(resp, 400, erroValidacao); return; }

            dao.salvarTipoDocumento(id, b.get("nome").getAsString().trim(),
                    b.get("obrigatorio").getAsBoolean(), lerOrdem(b), lerAtivo(b));
            json(resp, "{\"ok\":true}");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao atualizar tipo de documento de admissão", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        String path = req.getPathInfo();
        if (path == null || !path.startsWith("/tipos/")) { erro(resp, 400, "Informe o id na URL"); return; }
        try {
            int id = Integer.parseInt(path.substring("/tipos/".length()));
            boolean apagou = dao.excluirTipoDocumento(id);
            if (!apagou) {
                erro(resp, 409, "Já existe documento enviado para este tipo — desative em vez de excluir");
                return;
            }
            json(resp, "{\"ok\":true}");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao excluir tipo de documento de admissão", e);
            erro(resp, 500, e.getMessage());
        }
    }

    private JsonObject lerBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = req.getReader()) {
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    private static int lerOrdem(JsonObject b) {
        return b.has("ordem") && !b.get("ordem").isJsonNull() ? b.get("ordem").getAsInt() : 0;
    }

    private static boolean lerAtivo(JsonObject b) {
        return !b.has("ativo") || b.get("ativo").isJsonNull() || b.get("ativo").getAsBoolean();
    }

    private static String validar(JsonObject b) {
        if (!b.has("nome") || b.get("nome").getAsString().isBlank()) return "Informe o nome do documento";
        if (!b.has("obrigatorio")) return "Informe se o documento é obrigatório";
        return null;
    }
}
