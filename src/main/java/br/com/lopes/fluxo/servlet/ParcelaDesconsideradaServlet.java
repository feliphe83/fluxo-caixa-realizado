package br.com.lopes.fluxo.servlet;

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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Marca/desmarca parcelas do Fluxo de Caixa a Realizar como "desconsiderar"
 * (usuário decidiu não pagar naquele momento, mas sem alterar dado nenhum no
 * Oracle) — usado pelo botão "Desconsiderar" na tela de parcelas por
 * fornecedor, e pelos 3 modos de PDF (pauta completa / a pagar / desconsiderados).
 *
 * Chave da parcela: documento + parcela + cod_tipocontaspagar (o grupo
 * empresa é sempre 1 neste sistema, como em todas as demais consultas).
 * A tabela fc_parcela_desconsiderada é criada automaticamente no MySQL na
 * primeira chamada.
 *
 * GET  /api/parcela-desconsiderada
 *   Resposta: { "ok": true, "data": [ { documento, parcela, codTipoContasPagar,
 *               logon, dataMarcacao }, ... ] }  — todas as parcelas marcadas hoje.
 *
 * POST /api/parcela-desconsiderada
 *   Body: { documento, parcela, codTipoContasPagar, desconsiderar: true|false }
 *   Marca (upsert, grava logon/id_usuario/data) ou desmarca (remove a linha).
 *
 * Exige sessão de login normal (qualquer usuário autenticado, sem checagem
 * de administrador) — o AuthFilter já garante isso para toda rota fora de
 * /api/agricola|financeiro/* e das rotas públicas.
 */
@WebServlet("/api/parcela-desconsiderada")
public class ParcelaDesconsideradaServlet extends HttpServlet {

    private final Gson gson = new Gson();

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_parcela_desconsiderada (
                  documento VARCHAR(30) NOT NULL,
                  parcela VARCHAR(10) NOT NULL,
                  cod_tipocontaspagar VARCHAR(10) NOT NULL,
                  id_usuario INT NOT NULL,
                  logon VARCHAR(50) NOT NULL,
                  data_marcacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (documento, parcela, cod_tipocontaspagar)
                )
                """);
        }
        return c;
    }

    private void json(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().print(body);
        resp.getWriter().flush();
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        json(resp, "{\"ok\":false,\"erro\":\"" + msg.replace("\"", "'") + "\"}");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Cache-Control", "no-store");

        String sql = "SELECT documento, parcela, cod_tipocontaspagar, logon, data_marcacao FROM fc_parcela_desconsiderada";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            JsonArray arr = new JsonArray();
            while (rs.next()) {
                JsonObject o = new JsonObject();
                o.addProperty("documento",          rs.getString("documento"));
                o.addProperty("parcela",            rs.getString("parcela"));
                o.addProperty("codTipoContasPagar", rs.getString("cod_tipocontaspagar"));
                o.addProperty("logon",              rs.getString("logon"));
                o.addProperty("dataMarcacao",       String.valueOf(rs.getTimestamp("data_marcacao")));
                arr.add(o);
            }
            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.add("data", arr);
            json(resp, gson.toJson(r));
        } catch (SQLException e) {
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        Object idAttr = session != null ? session.getAttribute("idUsuario") : null;
        String logon = session != null ? (String) session.getAttribute("logon") : null;
        if (idAttr == null || logon == null) { erro(resp, 401, "Sessão expirada"); return; }
        long idUsuario = ((Number) idAttr).longValue();

        JsonObject body;
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = req.getReader()) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            }
            body = JsonParser.parseString(sb.toString()).getAsJsonObject();
        } catch (Exception e) {
            erro(resp, 400, "Body inválido");
            return;
        }

        String documento = campo(body, "documento");
        String parcela = campo(body, "parcela");
        String codTipoContasPagar = campo(body, "codTipoContasPagar");
        boolean desconsiderar = body.has("desconsiderar") && body.get("desconsiderar").getAsBoolean();

        if (documento.isBlank() || parcela.isBlank() || codTipoContasPagar.isBlank()) {
            erro(resp, 400, "documento, parcela e codTipoContasPagar são obrigatórios");
            return;
        }

        try (Connection c = conn()) {
            if (desconsiderar) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO fc_parcela_desconsiderada (documento, parcela, cod_tipocontaspagar, id_usuario, logon) " +
                        "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE id_usuario=VALUES(id_usuario), logon=VALUES(logon)")) {
                    ps.setString(1, documento);
                    ps.setString(2, parcela);
                    ps.setString(3, codTipoContasPagar);
                    ps.setLong(4, idUsuario);
                    ps.setString(5, logon);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM fc_parcela_desconsiderada WHERE documento=? AND parcela=? AND cod_tipocontaspagar=?")) {
                    ps.setString(1, documento);
                    ps.setString(2, parcela);
                    ps.setString(3, codTipoContasPagar);
                    ps.executeUpdate();
                }
            }
            json(resp, "{\"ok\":true}");
        } catch (SQLException e) {
            erro(resp, 500, e.getMessage());
        }
    }

    private static String campo(JsonObject body, String nome) {
        return body.has(nome) && !body.get(nome).isJsonNull() ? body.get(nome).getAsString().trim() : "";
    }
}
