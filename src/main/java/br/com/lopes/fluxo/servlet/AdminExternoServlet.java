package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AcessoExternoDAO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cadastro das empresas de fora, das pessoas delas e do que cada uma enxerga.
 *
 * Só administrador. Liberar acesso de terceiro à usina não é tarefa que se
 * delegue por descuido de permissão.
 *
 * GET    /api/admin/externo/empresas
 * POST   /api/admin/externo/empresas          { id?, cnpj, razaoSocial, nomeCurto, ativo }
 * GET    /api/admin/externo/usuarios[?idEmpresa=N]
 * POST   /api/admin/externo/usuarios          { id?, idEmpresa, logon, nome, cpf, senha?, ativo }
 * DELETE /api/admin/externo/usuarios?id=N
 * GET    /api/admin/externo/liberacoes?id=N
 * POST   /api/admin/externo/liberacoes        { id, equipamentos:[{cod,descricao}], contratos:[...] }
 */
@WebServlet("/api/admin/externo/*")
public class AdminExternoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AdminExternoServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final AcessoExternoDAO dao = new AcessoExternoDAO();

    private boolean admin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("administrador"));
    }

    private void json(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().print(body);
        resp.getWriter().flush();
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        json(resp, "{\"ok\":false,\"erro\":" + GSON.toJson(String.valueOf(msg)) + "}");
    }

    private String rota(HttpServletRequest req) {
        String p = req.getPathInfo();
        return p == null ? "" : p.replaceAll("^/|/$", "");
    }

    private JsonObject corpo(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = req.getReader()) {
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    private static String texto(JsonObject o, String c) {
        return o.has(c) && !o.get(c).isJsonNull() ? o.get(c).getAsString() : null;
    }

    private static Integer inteiro(JsonObject o, String c) {
        try {
            return o.has(c) && !o.get(c).isJsonNull() && !o.get(c).getAsString().isBlank()
                 ? o.get(c).getAsInt() : null;
        } catch (Exception e) { return null; }
    }

    private static String simNao(JsonObject o, String c) {
        String v = texto(o, c);
        return "N".equalsIgnoreCase(v) ? "N" : "S";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!admin(req)) { erro(resp, 403, "Apenas administradores"); return; }
        try {
            switch (rota(req)) {
                case "empresas" ->
                    json(resp, "{\"ok\":true,\"data\":" + GSON.toJson(dao.empresas()) + "}");
                case "usuarios" -> {
                    String e = req.getParameter("idEmpresa");
                    Integer id = (e == null || e.isBlank()) ? null : Integer.valueOf(e);
                    json(resp, "{\"ok\":true,\"data\":" + GSON.toJson(dao.usuarios(id)) + "}");
                }
                case "liberacoes" -> {
                    int id = Integer.parseInt(req.getParameter("id"));
                    json(resp, "{\"ok\":true,\"equipamentos\":" + GSON.toJson(dao.equipamentosDe(id))
                             + ",\"contratos\":" + GSON.toJson(dao.contratosDe(id)) + "}");
                }
                default -> erro(resp, 404, "Rota desconhecida");
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro no cadastro de acesso externo", e);
            erro(resp, 500, e.getMessage());
        } catch (Exception e) {
            erro(resp, 400, "Requisição inválida: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!admin(req)) { erro(resp, 403, "Apenas administradores"); return; }
        try {
            JsonObject b = corpo(req);
            switch (rota(req)) {
                case "empresas" -> {
                    int id = dao.salvarEmpresa(inteiro(b, "id"), texto(b, "cnpj"),
                            texto(b, "razaoSocial"), texto(b, "nomeCurto"), simNao(b, "ativo"));
                    json(resp, "{\"ok\":true,\"id\":" + id + "}");
                }
                case "usuarios" -> {
                    Integer idEmpresa = inteiro(b, "idEmpresa");
                    if (idEmpresa == null) { erro(resp, 400, "Empresa é obrigatória"); return; }
                    int id = dao.salvarUsuario(inteiro(b, "id"), idEmpresa, texto(b, "logon"),
                            texto(b, "nome"), texto(b, "cpf"), texto(b, "matricula"),
                            texto(b, "senha"), simNao(b, "ativo"));
                    json(resp, "{\"ok\":true,\"id\":" + id + "}");
                }
                case "liberacoes" -> {
                    Integer id = inteiro(b, "id");
                    if (id == null) { erro(resp, 400, "Usuário é obrigatório"); return; }
                    dao.gravarLiberacoes(id, pares(b, "equipamentos"), pares(b, "contratos"));
                    json(resp, "{\"ok\":true}");
                }
                default -> erro(resp, 404, "Rota desconhecida");
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao gravar acesso externo", e);
            // Chave duplicada tem mensagem própria: "Duplicate entry" não diz
            // nada a quem está na tela de cadastro.
            String msg = e.getMessage() != null && e.getMessage().contains("Duplicate entry")
                    ? "Já existe registro com esse CNPJ ou esse usuário nesta empresa"
                    : e.getMessage();
            erro(resp, 400, msg);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Corpo inválido no cadastro de acesso externo", e);
            erro(resp, 400, "Requisição inválida: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!admin(req)) { erro(resp, 403, "Apenas administradores"); return; }
        if (!"usuarios".equals(rota(req))) { erro(resp, 404, "Rota desconhecida"); return; }
        String id = req.getParameter("id");
        if (id == null || !id.matches("\\d+")) { erro(resp, 400, "Parâmetro id é obrigatório"); return; }
        try {
            boolean apagou = dao.excluirUsuario(Integer.parseInt(id));
            if (!apagou) { erro(resp, 404, "Usuário não encontrado"); return; }
            json(resp, "{\"ok\":true}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao excluir usuário externo", e);
            erro(resp, 500, e.getMessage());
        }
    }

    /** Lê [{cod, descricao}, …] como pares — o DAO grava sem conhecer JSON. */
    private static List<String[]> pares(JsonObject b, String campo) {
        List<String[]> lista = new ArrayList<>();
        if (!b.has(campo) || !b.get(campo).isJsonArray()) return lista;
        JsonArray arr = b.getAsJsonArray(campo);
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String cod = texto(o, "cod");
            if (cod == null || cod.isBlank()) continue;
            lista.add(new String[]{ cod, texto(o, "descricao") });
        }
        return lista;
    }
}
