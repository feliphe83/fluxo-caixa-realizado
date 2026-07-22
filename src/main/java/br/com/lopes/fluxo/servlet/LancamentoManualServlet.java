package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import com.google.gson.*;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lançamentos manuais do Fluxo de Caixa a Realizar — itens que ainda não
 * viraram título/provisão no Oracle (ex.: uma despesa prevista, negociada por
 * fora) mas que o usuário quer ver already refletida na pauta. Guardados no
 * MySQL (tabela fc_lancamento_manual); ao listar, entram misturados com os
 * dados do Oracle (mesma forma de JSON de FluxoARealizarServlet), com
 * provisao="Manual" — o que já aciona sozinho o chip/badge "Lançamento
 * Manual" da tela (index.html), sem precisar de nenhum caso especial no
 * front-end.
 *
 * Descrição do Item, Grupo de Empenho e Empenho são escolhidos a partir de
 * listas reais do Oracle (custo.grupoempenho, custo.empenho,
 * financeiro.fluxoconta) — o lançamento manual assim cai nas mesmas
 * categorias usadas pelos dados reais, e os totais por grupo continuam
 * corretos. Fornecedor, vencimento e valor são digitados livremente.
 *
 * GET  /api/lancamento-manual/opcoes
 *        -> { ok, gruposEmpenho:[{cod,descricao}], contasFluxo:[{cod,descricao}] }
 * GET  /api/lancamento-manual/empenhos?codGrupoEmpenho=N
 *        -> { ok, data:[{cod,descricao}] }
 * GET  /api/lancamento-manual?dataIni=yyyy-MM-dd&dataFim=yyyy-MM-dd
 *        -> { ok, data:[ ...mesmas colunas do fluxo-arealizar... ] }
 * POST /api/lancamento-manual
 *        Body: { descricaoItem, codContaFluxo, codGrupoEmpenho, descGrupoEmpenho,
 *                codEmpenho, descEmpenho, fornecedor, dataVencimento, valor }
 * DELETE /api/lancamento-manual?id=N
 *
 * Exige sessão de login normal (AuthFilter já garante isso fora de
 * /api/agricola|financeiro/*).
 */
@WebServlet("/api/lancamento-manual/*")
public class LancamentoManualServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(LancamentoManualServlet.class.getName());
    private final Gson gson = new Gson();

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_lancamento_manual (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  descricao_item VARCHAR(200) NOT NULL,
                  cod_contafluxo VARCHAR(20),
                  cod_grupoempenho VARCHAR(20) NOT NULL,
                  desc_grupoempenho VARCHAR(200) NOT NULL,
                  cod_empenho VARCHAR(20) NOT NULL,
                  desc_empenho VARCHAR(200) NOT NULL,
                  fornecedor VARCHAR(200) NOT NULL,
                  data_vencimento DATE NOT NULL,
                  valor DECIMAL(15,2) NOT NULL,
                  id_usuario INT NOT NULL,
                  logon VARCHAR(50) NOT NULL,
                  data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_vencimento (data_vencimento)
                )
                """);
        }
        return c;
    }

    private void json(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().print(body);
        resp.getWriter().flush();
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        json(resp, "{\"ok\":false,\"erro\":\"" + msg.replace("\"", "'").replace("\n", " ") + "\"}");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();

        try {
            if ("/opcoes".equals(path)) {
                listarOpcoes(resp);
                return;
            }
            if ("/empenhos".equals(path)) {
                listarEmpenhos(req, resp);
                return;
            }
            listarLancamentos(req, resp);

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro em lancamento-manual (GET " + path + ")", e);
            erro(resp, 500, e.getMessage());
        }
    }

    // ── Oracle: opções para os selects do modal ─────────────────────────

    private void listarOpcoes(HttpServletResponse resp) throws SQLException, IOException {
        JsonArray grupos = new JsonArray();
        JsonArray contas = new JsonArray();

        try (Connection conn = OracleConnectionUtil.getConnection()) {

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT cod_grupoempenho, descricao FROM custo.grupoempenho ORDER BY descricao");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("cod", rs.getString("cod_grupoempenho"));
                    o.addProperty("descricao", rs.getString("descricao"));
                    grupos.add(o);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT cod_contafluxo, descricaoconta FROM financeiro.fluxoconta " +
                    "WHERE cod_planofluxo = 16 ORDER BY descricaoconta");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("cod", rs.getString("cod_contafluxo"));
                    o.addProperty("descricao", rs.getString("descricaoconta"));
                    contas.add(o);
                }
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.add("gruposEmpenho", grupos);
        result.add("contasFluxo", contas);
        json(resp, gson.toJson(result));
    }

    // ── Oracle: empenhos de um grupo (select dependente) ────────────────

    private void listarEmpenhos(HttpServletRequest req, HttpServletResponse resp) throws SQLException, IOException {
        String codGrupo = req.getParameter("codGrupoEmpenho");
        if (codGrupo == null || codGrupo.isBlank()) {
            erro(resp, 400, "Informe codGrupoEmpenho");
            return;
        }

        JsonArray arr = new JsonArray();
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT cod_empenho, descricao FROM custo.empenho " +
                     "WHERE cod_grupoempenho = ? ORDER BY descricao")) {
            ps.setString(1, codGrupo.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("cod", rs.getString("cod_empenho"));
                    o.addProperty("descricao", rs.getString("descricao"));
                    arr.add(o);
                }
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.add("data", arr);
        json(resp, gson.toJson(result));
    }

    // ── MySQL: listar lançamentos manuais no período (mesma forma do fluxo-arealizar) ──

    private void listarLancamentos(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String dataIni = req.getParameter("dataIni");
        String dataFim = req.getParameter("dataFim");
        if (dataIni == null || dataFim == null || dataIni.isBlank() || dataFim.isBlank()) {
            erro(resp, 400, "Parâmetros dataIni e dataFim são obrigatórios");
            return;
        }

        String sql = "SELECT id, descricao_item, cod_contafluxo, cod_grupoempenho, desc_grupoempenho, " +
                     "cod_empenho, desc_empenho, fornecedor, data_vencimento, valor, logon " +
                     "FROM fc_lancamento_manual " +
                     "WHERE data_vencimento BETWEEN ? AND ? " +
                     "ORDER BY data_vencimento";

        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, dataIni);
            ps.setString(2, dataFim);

            JsonArray arr = new JsonArray();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    arr.add(paraJson(rs));
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.add("data", arr);
            json(resp, gson.toJson(result));

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao listar lancamento-manual", e);
            erro(resp, 500, e.getMessage());
        }
    }

    /** Mapeia uma linha da tabela para o mesmo formato de JSON usado por /api/fluxo-arealizar. */
    private JsonObject paraJson(ResultSet rs) throws SQLException {
        JsonObject o = new JsonObject();
        String id = rs.getString("id");
        o.addProperty("codContaFluxo", rs.getString("cod_contafluxo"));
        o.addProperty("descricaoConta", rs.getString("descricao_item"));
        o.addProperty("codEmpenho", rs.getString("cod_empenho"));
        o.addProperty("descEmpenho", rs.getString("desc_empenho"));
        o.addProperty("codGrupoEmpenho", rs.getString("cod_grupoempenho"));
        o.addProperty("descGrupoEmpenho", rs.getString("desc_grupoempenho"));
        o.addProperty("codFornecedor", (String) null);
        o.addProperty("nome", rs.getString("fornecedor"));
        o.addProperty("descricaoTipoConta", "Lançamento Manual");
        o.addProperty("documento", "MANUAL-" + id);
        o.addProperty("parcela", "1");
        o.addProperty("codTipoContasPagar", "MANUAL");
        o.addProperty("provisao", "Manual");
        o.addProperty("usuario", rs.getString("logon"));
        o.addProperty("valor", rs.getBigDecimal("valor"));
        o.addProperty("codIndiceFinanceiro", (String) null);
        String dv = String.valueOf(rs.getDate("data_vencimento"));
        o.addProperty("dataVcto", dv);
        o.addProperty("dataVctoOrig", dv);
        o.addProperty("dataEntrada", dv);
        o.addProperty("idLancamentoManual", Integer.parseInt(id));
        return o;
    }

    // ── POST: criar lançamento ───────────────────────────────────────────

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

        String descricaoItem = campo(body, "descricaoItem");
        String codContaFluxo = campo(body, "codContaFluxo");
        String codGrupoEmpenho = campo(body, "codGrupoEmpenho");
        String descGrupoEmpenho = campo(body, "descGrupoEmpenho");
        String codEmpenho = campo(body, "codEmpenho");
        String descEmpenho = campo(body, "descEmpenho");
        String fornecedor = campo(body, "fornecedor");
        String dataVencimentoStr = campo(body, "dataVencimento");

        if (descricaoItem.isBlank() || codGrupoEmpenho.isBlank() || codEmpenho.isBlank()
                || fornecedor.isBlank() || dataVencimentoStr.isBlank() || !body.has("valor")) {
            erro(resp, 400, "Descrição do Item, Grupo de Empenho, Empenho, Fornecedor, Data de Vencimento e Valor são obrigatórios");
            return;
        }

        LocalDate dataVencimento;
        try {
            dataVencimento = LocalDate.parse(dataVencimentoStr);
        } catch (DateTimeParseException e) {
            erro(resp, 400, "Data de Vencimento inválida (use yyyy-MM-dd)");
            return;
        }

        BigDecimal valor;
        try {
            valor = body.get("valor").getAsBigDecimal();
        } catch (Exception e) {
            erro(resp, 400, "Valor inválido");
            return;
        }
        if (valor.signum() <= 0) {
            erro(resp, 400, "Valor deve ser maior que zero");
            return;
        }

        String sql = "INSERT INTO fc_lancamento_manual " +
                "(descricao_item, cod_contafluxo, cod_grupoempenho, desc_grupoempenho, cod_empenho, desc_empenho, " +
                " fornecedor, data_vencimento, valor, id_usuario, logon) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, descricaoItem);
            ps.setString(2, codContaFluxo.isBlank() ? null : codContaFluxo);
            ps.setString(3, codGrupoEmpenho);
            ps.setString(4, descGrupoEmpenho);
            ps.setString(5, codEmpenho);
            ps.setString(6, descEmpenho);
            ps.setString(7, fornecedor);
            ps.setString(8, dataVencimento.toString());
            ps.setBigDecimal(9, valor);
            ps.setLong(10, idUsuario);
            ps.setString(11, logon);
            ps.executeUpdate();

            long novoId = -1;
            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) novoId = gk.getLong(1);
            }

            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.addProperty("id", novoId);
            json(resp, gson.toJson(result));

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao criar lancamento-manual", e);
            erro(resp, 500, e.getMessage());
        }
    }

    // ── DELETE: remover lançamento ───────────────────────────────────────

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String idStr = req.getParameter("id");
        if (idStr == null || !idStr.matches("\\d+")) {
            erro(resp, 400, "Informe o id do lançamento");
            return;
        }

        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("DELETE FROM fc_lancamento_manual WHERE id = ?")) {
            ps.setLong(1, Long.parseLong(idStr));
            int linhas = ps.executeUpdate();
            if (linhas == 0) { erro(resp, 404, "Lançamento não encontrado"); return; }
            json(resp, "{\"ok\":true}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao excluir lancamento-manual", e);
            erro(resp, 500, e.getMessage());
        }
    }

    private static String campo(JsonObject body, String nome) {
        return body.has(nome) && !body.get(nome).isJsonNull() ? body.get(nome).getAsString().trim() : "";
    }
}
