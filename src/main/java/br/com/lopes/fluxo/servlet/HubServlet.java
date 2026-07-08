package br.com.lopes.fluxo.servlet;

import com.google.gson.*;
import java.io.*;
import java.sql.*;
import java.util.logging.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;

/**
 * GET /api/hub
 *
 * Retorna as categorias e módulos que o usuário logado tem permissão
 * de acessar, já estruturados em árvore (categoria -> módulos).
 *
 * Usuários administradores veem todos os módulos ativos automaticamente.
 */
@WebServlet("/api/hub")
public class HubServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(HubServlet.class.getName());
    private final Gson gson = new Gson();

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("idUsuario") == null) {
            resp.setStatus(401);
            out.print("{\"ok\":false,\"erro\":\"Não autenticado\"}");
            return;
        }

        int idUsuario = ((Number) session.getAttribute("idUsuario")).intValue();
        boolean isAdmin = Boolean.TRUE.equals(session.getAttribute("administrador"));

        String sql;
        if (isAdmin) {
            // Admin vê todos os módulos ativos, sem checar permissão individual
            sql = """
                SELECT c.id AS cat_id, c.nome AS cat_nome, c.icone AS cat_icone, c.ordem AS cat_ordem,
                       m.id AS mod_id, m.nome AS mod_nome, m.descricao AS mod_descricao,
                       m.icone AS mod_icone, m.url_destino, m.ordem AS mod_ordem
                FROM intranet_categoria c
                JOIN intranet_modulo m ON m.id_categoria = c.id
                WHERE c.ativo = 1 AND m.ativo = 1
                ORDER BY c.ordem, c.nome, m.ordem, m.nome
                """;
        } else {
            sql = """
                SELECT c.id AS cat_id, c.nome AS cat_nome, c.icone AS cat_icone, c.ordem AS cat_ordem,
                       m.id AS mod_id, m.nome AS mod_nome, m.descricao AS mod_descricao,
                       m.icone AS mod_icone, m.url_destino, m.ordem AS mod_ordem
                FROM intranet_categoria c
                JOIN intranet_modulo m ON m.id_categoria = c.id
                JOIN intranet_permissao_modulo p ON p.id_modulo = m.id
                WHERE c.ativo = 1 AND m.ativo = 1 AND p.id_usuario = ?
                ORDER BY c.ordem, c.nome, m.ordem, m.nome
                """;
        }

        // Estrutura: Map ordenado por categoria -> lista de módulos
        java.util.LinkedHashMap<Integer, JsonObject> categorias = new java.util.LinkedHashMap<>();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (!isAdmin) ps.setInt(1, idUsuario);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int catId = rs.getInt("cat_id");

                    JsonObject cat = categorias.get(catId);
                    if (cat == null) {
                        cat = new JsonObject();
                        cat.addProperty("id", catId);
                        cat.addProperty("nome", rs.getString("cat_nome"));
                        cat.addProperty("icone", rs.getString("cat_icone"));
                        cat.add("modulos", new JsonArray());
                        categorias.put(catId, cat);
                    }

                    JsonObject mod = new JsonObject();
                    mod.addProperty("id", rs.getInt("mod_id"));
                    mod.addProperty("nome", rs.getString("mod_nome"));
                    mod.addProperty("descricao", rs.getString("mod_descricao"));
                    mod.addProperty("icone", rs.getString("mod_icone"));
                    mod.addProperty("urlDestino", rs.getString("url_destino"));

                    cat.getAsJsonArray("modulos").add(mod);
                }
            }

            JsonArray categoriasArr = new JsonArray();
            categorias.values().forEach(categoriasArr::add);

            JsonObject resultado = new JsonObject();
            resultado.addProperty("ok", true);
            resultado.add("categorias", categoriasArr);
            out.print(gson.toJson(resultado));

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar hub de módulos", e);
            out.print("{\"ok\":false,\"erro\":\"" + e.getMessage().replace("\"","'") + "\"}");
        } finally {
            out.flush();
        }
    }
}
