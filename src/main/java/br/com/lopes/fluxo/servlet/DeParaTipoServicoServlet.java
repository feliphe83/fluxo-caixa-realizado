package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.DeParaTipoServicoCache;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
 * Administração do de-para Tipo de Serviço (rh.tiposervico no Oracle) →
 * Processo/Subprocesso/Objeto de Custo, usado para classificar os
 * lançamentos de mão de obra no resumo do Controle de Serviços Agrícola.
 *
 * A tabela fc_depara_tiposervico é criada automaticamente no MySQL na
 * primeira chamada (CREATE TABLE IF NOT EXISTS).
 *
 * GET    /api/depara-tiposervico             -> lista tudo
 * POST   /api/depara-tiposervico/importar    -> importa em lote (upsert)
 *          Body: texto colado do Excel, 5 colunas separadas por TAB, uma
 *          linha por registro: PROCESSO / SUBPROCESSO / OBJETOCUSTO /
 *          COD_TIPOSERVICO / DESCRICAO. Linhas sem um número válido na 4ª
 *          coluna (ex.: cabeçalho) são ignoradas.
 * DELETE /api/depara-tiposervico?cod=N       -> remove um registro
 *
 * Todas as rotas exigem sessão de administrador.
 */
@WebServlet("/api/depara-tiposervico/*")
public class DeParaTipoServicoServlet extends HttpServlet {

    private final Gson gson = new Gson();

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_depara_tiposervico (
                  cod_tiposervico INT PRIMARY KEY,
                  processo VARCHAR(120),
                  subprocesso VARCHAR(150),
                  objetocusto VARCHAR(150),
                  descricao VARCHAR(200),
                  data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
        }
        return c;
    }

    private boolean isAdmin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("administrador"));
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
        if (!isAdmin(req)) { erro(resp, 403, "Acesso restrito a administradores"); return; }

        String sql = "SELECT cod_tiposervico, processo, subprocesso, objetocusto, descricao " +
                     "FROM fc_depara_tiposervico ORDER BY processo, subprocesso, objetocusto, cod_tiposervico";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            JsonArray arr = new JsonArray();
            while (rs.next()) {
                JsonObject o = new JsonObject();
                o.addProperty("codTipoServico", rs.getInt(1));
                o.addProperty("processo",       rs.getString(2));
                o.addProperty("subprocesso",    rs.getString(3));
                o.addProperty("objetoCusto",    rs.getString(4));
                o.addProperty("descricao",      rs.getString(5));
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
        if (!isAdmin(req)) { erro(resp, 403, "Acesso restrito a administradores"); return; }

        String path = req.getPathInfo();
        if (!"/importar".equals(path)) { erro(resp, 404, "Não encontrado"); return; }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = req.getReader()) {
            String l;
            while ((l = br.readLine()) != null) sb.append(l).append('\n');
        }

        String[] linhas = sb.toString().split("\\r?\\n");
        int importadas = 0, ignoradas = 0;

        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO fc_depara_tiposervico (cod_tiposervico, processo, subprocesso, objetocusto, descricao) " +
                 "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE processo=VALUES(processo), subprocesso=VALUES(subprocesso), " +
                 "objetocusto=VALUES(objetocusto), descricao=VALUES(descricao)")) {

            int noBatch = 0;
            for (String linha : linhas) {
                if (linha.isBlank()) continue;
                String[] cols = linha.split("\t", -1);
                if (cols.length < 5) { ignoradas++; continue; }

                String codStr = cols[3].trim();
                if (!codStr.matches("\\d+")) { ignoradas++; continue; } // cabeçalho ou linha inválida

                ps.setInt(1, Integer.parseInt(codStr));
                ps.setString(2, cols[0].trim());
                ps.setString(3, cols[1].trim());
                ps.setString(4, cols[2].trim());
                ps.setString(5, cols[4].trim());
                ps.addBatch();
                importadas++;
                noBatch++;
                if (noBatch >= 500) { ps.executeBatch(); noBatch = 0; }
            }
            if (noBatch > 0) ps.executeBatch();

        } catch (SQLException e) {
            erro(resp, 500, e.getMessage());
            return;
        }

        DeParaTipoServicoCache.invalidar();
        json(resp, "{\"ok\":true,\"importadas\":" + importadas + ",\"ignoradas\":" + ignoradas + "}");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso restrito a administradores"); return; }

        String cod = req.getParameter("cod");
        if (cod == null || !cod.matches("\\d+")) { erro(resp, 400, "Parâmetro cod é obrigatório e numérico"); return; }

        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("DELETE FROM fc_depara_tiposervico WHERE cod_tiposervico=?")) {
            ps.setInt(1, Integer.parseInt(cod));
            ps.executeUpdate();
            DeParaTipoServicoCache.invalidar();
            json(resp, "{\"ok\":true}");
        } catch (SQLException e) {
            erro(resp, 500, e.getMessage());
        }
    }
}
