package br.com.lopes.fluxo.servlet;

import com.google.gson.*;
import java.io.*;
import java.sql.*;
import java.util.logging.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;

/**
 * Administração do Hub de Módulos: /api/hub-admin/*
 *
 * GET    /api/hub-admin/categorias              -> lista categorias
 * POST   /api/hub-admin/categorias               -> cria categoria       {nome, icone, ordem}
 * PUT    /api/hub-admin/categorias/{id}           -> edita categoria
 * DELETE /api/hub-admin/categorias/{id}           -> remove categoria
 *
 * GET    /api/hub-admin/modulos                  -> lista módulos (todos, com id_categoria)
 * POST   /api/hub-admin/modulos                   -> cria módulo          {idCategoria, nome, descricao, icone, urlDestino, ordem}
 * PUT    /api/hub-admin/modulos/{id}               -> edita módulo
 * DELETE /api/hub-admin/modulos/{id}               -> remove módulo
 *
 * GET    /api/hub-admin/permissoes?idUsuario=N    -> lista ids de módulos que o usuário N tem acesso
 * POST   /api/hub-admin/permissoes                -> define permissões    {idUsuario, idModulos:[1,2,3]} (substitui todas)
 *
 * Todas as rotas exigem sessão de administrador.
 */
@WebServlet("/api/hub-admin/*")
public class HubAdminServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(HubAdminServlet.class.getName());
    private final Gson gson = new Gson();

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    private boolean isAdmin(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute("administrador"));
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        resp.getWriter().print("{\"ok\":false,\"erro\":\"" + msg.replace("\"","'") + "\"}");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        if (!isAdmin(req)) { erro(resp, 403, "Acesso restrito a administradores"); return; }

        String path = req.getPathInfo() != null ? req.getPathInfo() : "";

        try {
            if (path.equals("/categorias")) {
                listarCategorias(resp);
            } else if (path.equals("/modulos")) {
                listarModulos(resp);
            } else if (path.equals("/permissoes")) {
                String idUsuarioStr = req.getParameter("idUsuario");
                if (idUsuarioStr == null) { erro(resp, 400, "idUsuario é obrigatório"); return; }
                listarPermissoes(resp, Integer.parseInt(idUsuarioStr));
            } else {
                erro(resp, 404, "Rota não encontrada");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro GET hub-admin", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        if (!isAdmin(req)) { erro(resp, 403, "Acesso restrito a administradores"); return; }

        String path = req.getPathInfo() != null ? req.getPathInfo() : "";
        String body = lerCorpo(req);

        try {
            if (path.equals("/categorias")) {
                criarCategoria(resp, body);
            } else if (path.equals("/modulos")) {
                criarModulo(resp, body);
            } else if (path.equals("/permissoes")) {
                salvarPermissoes(resp, body);
            } else {
                erro(resp, 404, "Rota não encontrada");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro POST hub-admin", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        if (!isAdmin(req)) { erro(resp, 403, "Acesso restrito a administradores"); return; }

        String path = req.getPathInfo() != null ? req.getPathInfo() : "";
        String body = lerCorpo(req);

        try {
            if (path.startsWith("/categorias/")) {
                int id = Integer.parseInt(path.substring("/categorias/".length()));
                editarCategoria(resp, id, body);
            } else if (path.startsWith("/modulos/")) {
                int id = Integer.parseInt(path.substring("/modulos/".length()));
                editarModulo(resp, id, body);
            } else {
                erro(resp, 404, "Rota não encontrada");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro PUT hub-admin", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        if (!isAdmin(req)) { erro(resp, 403, "Acesso restrito a administradores"); return; }

        String path = req.getPathInfo() != null ? req.getPathInfo() : "";

        try {
            if (path.startsWith("/categorias/")) {
                int id = Integer.parseInt(path.substring("/categorias/".length()));
                executarUpdate(resp, "DELETE FROM intranet_categoria WHERE id = ?", id);
            } else if (path.startsWith("/modulos/")) {
                int id = Integer.parseInt(path.substring("/modulos/".length()));
                executarUpdate(resp, "DELETE FROM intranet_modulo WHERE id = ?", id);
            } else {
                erro(resp, 404, "Rota não encontrada");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro DELETE hub-admin", e);
            erro(resp, 500, e.getMessage());
        }
    }

    // ───────────────────────────────────────────────────────────

    private String lerCorpo(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private void listarCategorias(HttpServletResponse resp) throws SQLException, IOException {
        JsonArray arr = new JsonArray();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                "SELECT id, nome, icone, ordem, ativo FROM intranet_categoria ORDER BY ordem, nome");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", rs.getInt("id"));
                o.addProperty("nome", rs.getString("nome"));
                o.addProperty("icone", rs.getString("icone"));
                o.addProperty("ordem", rs.getInt("ordem"));
                o.addProperty("ativo", rs.getBoolean("ativo"));
                arr.add(o);
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.add("data", arr);
        resp.getWriter().print(gson.toJson(result));
    }

    private void listarModulos(HttpServletResponse resp) throws SQLException, IOException {
        JsonArray arr = new JsonArray();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                "SELECT id, id_categoria, nome, descricao, icone, url_destino, ordem, ativo " +
                "FROM intranet_modulo ORDER BY id_categoria, ordem, nome");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", rs.getInt("id"));
                o.addProperty("idCategoria", rs.getInt("id_categoria"));
                o.addProperty("nome", rs.getString("nome"));
                o.addProperty("descricao", rs.getString("descricao"));
                o.addProperty("icone", rs.getString("icone"));
                o.addProperty("urlDestino", rs.getString("url_destino"));
                o.addProperty("ordem", rs.getInt("ordem"));
                o.addProperty("ativo", rs.getBoolean("ativo"));
                arr.add(o);
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.add("data", arr);
        resp.getWriter().print(gson.toJson(result));
    }

    private void listarPermissoes(HttpServletResponse resp, int idUsuario) throws SQLException, IOException {
        JsonArray arr = new JsonArray();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                "SELECT id_modulo FROM intranet_permissao_modulo WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) arr.add(rs.getInt("id_modulo"));
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.add("idModulos", arr);
        resp.getWriter().print(gson.toJson(result));
    }

    private void criarCategoria(HttpServletResponse resp, String body) throws SQLException, IOException {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO intranet_categoria (nome, icone, ordem) VALUES (?,?,?)")) {
            ps.setString(1, json.get("nome").getAsString());
            ps.setString(2, json.has("icone") && !json.get("icone").isJsonNull() ? json.get("icone").getAsString() : null);
            ps.setInt(3, json.has("ordem") ? json.get("ordem").getAsInt() : 0);
            ps.executeUpdate();
        }
        resp.getWriter().print("{\"ok\":true}");
    }

    private void editarCategoria(HttpServletResponse resp, int id, String body) throws SQLException, IOException {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                "UPDATE intranet_categoria SET nome=?, icone=?, ordem=?, ativo=? WHERE id=?")) {
            ps.setString(1, json.get("nome").getAsString());
            ps.setString(2, json.has("icone") && !json.get("icone").isJsonNull() ? json.get("icone").getAsString() : null);
            ps.setInt(3, json.has("ordem") ? json.get("ordem").getAsInt() : 0);
            ps.setBoolean(4, !json.has("ativo") || json.get("ativo").getAsBoolean());
            ps.setInt(5, id);
            ps.executeUpdate();
        }
        resp.getWriter().print("{\"ok\":true}");
    }

    private void criarModulo(HttpServletResponse resp, String body) throws SQLException, IOException {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                "INSERT INTO intranet_modulo (id_categoria, nome, descricao, icone, url_destino, ordem) " +
                "VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, json.get("idCategoria").getAsInt());
            ps.setString(2, json.get("nome").getAsString());
            ps.setString(3, json.has("descricao") && !json.get("descricao").isJsonNull() ? json.get("descricao").getAsString() : null);
            ps.setString(4, json.has("icone") && !json.get("icone").isJsonNull() ? json.get("icone").getAsString() : null);
            ps.setString(5, json.get("urlDestino").getAsString());
            ps.setInt(6, json.has("ordem") ? json.get("ordem").getAsInt() : 0);
            ps.executeUpdate();
        }
        resp.getWriter().print("{\"ok\":true}");
    }

    private void editarModulo(HttpServletResponse resp, int id, String body) throws SQLException, IOException {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                "UPDATE intranet_modulo SET id_categoria=?, nome=?, descricao=?, icone=?, url_destino=?, ordem=?, ativo=? " +
                "WHERE id=?")) {
            ps.setInt(1, json.get("idCategoria").getAsInt());
            ps.setString(2, json.get("nome").getAsString());
            ps.setString(3, json.has("descricao") && !json.get("descricao").isJsonNull() ? json.get("descricao").getAsString() : null);
            ps.setString(4, json.has("icone") && !json.get("icone").isJsonNull() ? json.get("icone").getAsString() : null);
            ps.setString(5, json.get("urlDestino").getAsString());
            ps.setInt(6, json.has("ordem") ? json.get("ordem").getAsInt() : 0);
            ps.setBoolean(7, !json.has("ativo") || json.get("ativo").getAsBoolean());
            ps.setInt(8, id);
            ps.executeUpdate();
        }
        resp.getWriter().print("{\"ok\":true}");
    }

    private void salvarPermissoes(HttpServletResponse resp, String body) throws SQLException, IOException {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        int idUsuario = json.get("idUsuario").getAsInt();
        JsonArray idModulos = json.getAsJsonArray("idModulos");

        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM intranet_permissao_modulo WHERE id_usuario = ?")) {
                    del.setInt(1, idUsuario);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO intranet_permissao_modulo (id_usuario, id_modulo) VALUES (?,?)")) {
                    for (JsonElement el : idModulos) {
                        ins.setInt(1, idUsuario);
                        ins.setInt(2, el.getAsInt());
                        ins.addBatch();
                    }
                    if (idModulos.size() > 0) ins.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
        resp.getWriter().print("{\"ok\":true}");
    }

    private void executarUpdate(HttpServletResponse resp, String sql, int id) throws SQLException, IOException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        resp.getWriter().print("{\"ok\":true}");
    }
}
