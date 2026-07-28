package br.com.lopes.fluxo.servlet;

import com.google.gson.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;
import java.sql.*;
import java.util.logging.*;

@WebServlet("/api/admin/*")
public class AdminServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AdminServlet.class.getName());
    private static final Gson   GSON = new GsonBuilder().serializeNulls().create();

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private boolean isAdmin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("administrador"));
    }

    private void json(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().print(body); resp.getWriter().flush();
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        json(resp, "{\"ok\":false,\"erro\":\"" + msg.replace("\"","'") + "\"}");
    }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        String path = req.getPathInfo();
        if ("/usuarios".equals(path))        listarUsuarios(resp);
        else if ("/permissoes".equals(path)) listarPermissoes(resp, Long.parseLong(req.getParameter("idUsuario")));
        else erro(resp, 404, "Não encontrado");
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        String path = req.getPathInfo(); JsonObject b = lerBody(req);
        if ("/usuarios".equals(path))        criarUsuario(resp, b);
        else if ("/permissoes".equals(path)) salvarPermissao(resp, b);
        else erro(resp, 404, "Não encontrado");
    }

    @Override protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        editarUsuario(resp, lerBody(req));
    }

    @Override protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        excluirUsuario(resp, Long.parseLong(req.getParameter("id")));
    }

    private void listarUsuarios(HttpServletResponse resp) throws IOException {
        String sql = "SELECT id, logon, nome, email, telefone, ativo, administrador, data_criacao, id_logon_erp, etapa_aprovacao FROM fc_usuario ORDER BY logon";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            JsonArray arr = new JsonArray();
            while (rs.next()) {
                JsonObject o = new JsonObject();
                o.addProperty("id",            rs.getLong("id"));
                o.addProperty("logon",         rs.getString("logon"));
                o.addProperty("nome",          rs.getString("nome"));
                o.addProperty("email",         rs.getString("email"));
                o.addProperty("telefone",      rs.getString("telefone"));
                o.addProperty("ativo",         rs.getString("ativo"));
                o.addProperty("administrador", rs.getString("administrador"));
                o.addProperty("dataCriacao",   rs.getString("data_criacao"));
                Integer idLogonErp = rs.getInt("id_logon_erp");
                if (rs.wasNull()) idLogonErp = null;
                o.addProperty("idLogonErp",    idLogonErp);
                o.addProperty("etapaAprovacao", rs.getInt("etapa_aprovacao"));
                arr.add(o);
            }
            JsonObject r = new JsonObject(); r.addProperty("ok", true); r.add("data", arr);
            json(resp, GSON.toJson(r));
        } catch (SQLException e) { erro(resp, 500, e.getMessage()); }
    }

    private void criarUsuario(HttpServletResponse resp, JsonObject b) throws IOException {
        String logon    = b.get("logon").getAsString().toUpperCase().trim();
        String nome     = b.get("nome").getAsString().trim();
        String senha    = b.get("senha").getAsString();
        String ativo    = b.has("ativo")         ? b.get("ativo").getAsString()         : "S";
        String admin    = b.has("administrador") ? b.get("administrador").getAsString() : "N";
        String email    = b.has("email")    && !b.get("email").isJsonNull()    ? b.get("email").getAsString().trim()    : null;
        String telefone = b.has("telefone") && !b.get("telefone").isJsonNull() ? b.get("telefone").getAsString().trim() : null;

        Integer idLogonErp = lerIdLogonErp(b);
        int etapaAprovacao = lerEtapaAprovacao(b);

        if (logon.isEmpty() || nome.isEmpty() || senha.isEmpty()) { erro(resp, 400, "logon, nome e senha são obrigatórios"); return; }

        String sql = "INSERT INTO fc_usuario (logon, nome, senha_hash, ativo, administrador, email, telefone, id_logon_erp, etapa_aprovacao) VALUES (UPPER(?),?,SHA2(?,256),?,?,?,?,?,?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, logon); ps.setString(2, nome); ps.setString(3, senha);
            ps.setString(4, ativo); ps.setString(5, admin);
            ps.setString(6, email); ps.setString(7, telefone);
            ps.setObject(8, idLogonErp);
            ps.setInt(9, etapaAprovacao);
            ps.executeUpdate();
            json(resp, "{\"ok\":true,\"msg\":\"Usuário criado\"}");
        } catch (SQLException e) {
            String msg = e.getMessage().contains("Duplicate") ? "Logon já existe" : e.getMessage();
            erro(resp, 500, msg);
        }
    }

    private void editarUsuario(HttpServletResponse resp, JsonObject b) throws IOException {
        long   id       = b.get("id").getAsLong();
        String nome     = b.get("nome").getAsString().trim();
        String ativo    = b.has("ativo")         ? b.get("ativo").getAsString()         : "S";
        String admin    = b.has("administrador") ? b.get("administrador").getAsString() : "N";
        String email    = b.has("email")    && !b.get("email").isJsonNull()    ? b.get("email").getAsString().trim()    : null;
        String telefone = b.has("telefone") && !b.get("telefone").isJsonNull() ? b.get("telefone").getAsString().trim() : null;
        String senha    = b.has("senha") && !b.get("senha").isJsonNull() && !b.get("senha").getAsString().isEmpty() ? b.get("senha").getAsString() : null;
        Integer idLogonErp = lerIdLogonErp(b);
        int etapaAprovacao = lerEtapaAprovacao(b);

        try (Connection c = conn()) {
            String sql = senha != null
                ? "UPDATE fc_usuario SET nome=?,ativo=?,administrador=?,email=?,telefone=?,id_logon_erp=?,etapa_aprovacao=?,senha_hash=SHA2(?,256) WHERE id=?"
                : "UPDATE fc_usuario SET nome=?,ativo=?,administrador=?,email=?,telefone=?,id_logon_erp=?,etapa_aprovacao=? WHERE id=?";
            PreparedStatement ps = c.prepareStatement(sql);
            ps.setString(1, nome); ps.setString(2, ativo); ps.setString(3, admin);
            ps.setString(4, email); ps.setString(5, telefone);
            ps.setObject(6, idLogonErp);
            ps.setInt(7, etapaAprovacao);
            if (senha != null) { ps.setString(8, senha); ps.setLong(9, id); }
            else                { ps.setLong(8, id); }
            ps.executeUpdate();
            json(resp, "{\"ok\":true,\"msg\":\"Usuário atualizado\"}");
        } catch (SQLException e) { erro(resp, 500, e.getMessage()); }
    }

    /**
     * Código de logon do usuário no ERP (segurancanovo), usado pelo alerta de
     * ordens de compra pendentes pra saber quais ordens são de cada aprovador.
     * Campo opcional: em branco vira null.
     */
    private Integer lerIdLogonErp(JsonObject b) {
        if (!b.has("idLogonErp") || b.get("idLogonErp").isJsonNull()) return null;
        String v = b.get("idLogonErp").getAsString().trim();
        return v.isEmpty() ? null : Integer.valueOf(v);
    }

    /**
     * Em que etapa da alçada a pessoa aprova: 1º ou 2º aprovador. São
     * consultas diferentes no ERP (ver OrdemCompraPendenteDAO) — o 2º vê o
     * que já passou pela aprovação intermediária. Só o alerta de ordens de
     * compra usa; qualquer coisa fora de 1/2 vira 1.
     */
    private int lerEtapaAprovacao(JsonObject b) {
        if (!b.has("etapaAprovacao") || b.get("etapaAprovacao").isJsonNull()) return 1;
        String v = b.get("etapaAprovacao").getAsString().trim();
        return "2".equals(v) ? 2 : 1;
    }

    private void excluirUsuario(HttpServletResponse resp, long id) throws IOException {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM fc_permissao WHERE id_usuario=?")) { ps.setLong(1, id); ps.executeUpdate(); }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM fc_usuario WHERE id=?"))           { ps.setLong(1, id); ps.executeUpdate(); }
            json(resp, "{\"ok\":true,\"msg\":\"Usuário excluído\"}");
        } catch (SQLException e) { erro(resp, 500, e.getMessage()); }
    }

    private void listarPermissoes(HttpServletResponse resp, long idUsuario) throws IOException {
        String sql = "SELECT relatorio FROM fc_permissao WHERE id_usuario=? AND ativo='S'";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                JsonArray arr = new JsonArray();
                while (rs.next()) arr.add(rs.getString("relatorio"));
                JsonObject r = new JsonObject(); r.addProperty("ok", true); r.add("data", arr);
                json(resp, GSON.toJson(r));
            }
        } catch (SQLException e) { erro(resp, 500, e.getMessage()); }
    }

    private void salvarPermissao(HttpServletResponse resp, JsonObject b) throws IOException {
        long   id  = b.get("idUsuario").getAsLong();
        String rel = b.get("relatorio").getAsString();
        String at  = b.get("ativo").getAsBoolean() ? "S" : "N";
        String sql = "INSERT INTO fc_permissao (id_usuario,relatorio,ativo) VALUES (?,?,?) ON DUPLICATE KEY UPDATE ativo=?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1,id); ps.setString(2,rel); ps.setString(3,at); ps.setString(4,at);
            ps.executeUpdate(); json(resp, "{\"ok\":true}");
        } catch (SQLException e) { erro(resp, 500, e.getMessage()); }
    }

    private Connection conn() throws SQLException { return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS); }

    private JsonObject lerBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = req.getReader()) { String l; while ((l=br.readLine())!=null) sb.append(l); }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }
}
