package br.com.lopes.fluxo.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Fotografias do Controle de Serviços (MySQL).
 *
 * A tela consulta o Oracle, que continua recebendo apontamentos depois do
 * fechamento. Gravar guarda o que estava na tela naquele instante; ao gerar
 * de novo o mesmo período, a tela compara o atual com o gravado e mostra o
 * que chegou (ou saiu) depois.
 *
 * Duas tabelas: o cabeçalho com o período, os filtros e quem gravou, e os
 * itens, um por linha do relatório — no mesmo grão que o Oracle devolve
 * (fornecedor/fazenda/serviço/data), para a comparação poder ser feita em
 * qualquer nível de agrupamento da tela.
 *
 * Sem FOREIGN KEY, como nas demais tabelas fc_* deste projeto.
 */
public class ServicoSnapshotDAO {

    private static final Logger LOG = Logger.getLogger(ServicoSnapshotDAO.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    /** Teto por gravação — evita que um período aberto por engano trave o banco. */
    public static final int MAX_ITENS = 200_000;

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_servico_snapshot (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  data_ini DATE NOT NULL,
                  data_fim DATE NOT NULL,
                  fazendas VARCHAR(255),
                  fornecedores VARCHAR(255),
                  descricao VARCHAR(150),
                  qtde_itens INT NOT NULL DEFAULT 0,
                  total_qtd DECIMAL(18,4) NOT NULL DEFAULT 0,
                  total_valor DECIMAL(18,2) NOT NULL DEFAULT 0,
                  id_usuario INT,
                  nome_usuario VARCHAR(120),
                  data_gravacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_periodo (data_ini, data_fim)
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_servico_snapshot_item (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  id_snapshot INT NOT NULL,
                  tipo VARCHAR(40),
                  origem VARCHAR(60),
                  cod_fazenda VARCHAR(20),
                  desc_fazenda VARCHAR(150),
                  cod_fornecedor VARCHAR(20),
                  desc_fornecedor VARCHAR(150),
                  cod_servico VARCHAR(20),
                  desc_servico VARCHAR(150),
                  cod_funcionario VARCHAR(20),
                  nome_funcionario VARCHAR(150),
                  data_movimento DATE,
                  unidade VARCHAR(20),
                  qtd_apontada DECIMAL(18,4) NOT NULL DEFAULT 0,
                  valor_total DECIMAL(18,2) NOT NULL DEFAULT 0,
                  INDEX idx_snapshot (id_snapshot)
                )
                """);
        }
        return c;
    }

    /** Cria as tabelas — chamado no start da aplicação. */
    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    // ── Gravação ──────────────────────────────────────────────────────────

    /**
     * Grava uma fotografia do que está na tela.
     *
     * @param itens linhas cruas do relatório (o mesmo objeto que a tela
     *              recebe de /api/controle-servicos)
     * @return id do snapshot criado
     */
    public int gravar(String dataIni, String dataFim, String fazendas, String fornecedores,
                      String descricao, long idUsuario, String nomeUsuario,
                      JsonArray itens) throws SQLException {

        BigDecimal totalQtd = BigDecimal.ZERO;
        BigDecimal totalValor = BigDecimal.ZERO;
        for (int i = 0; i < itens.size(); i++) {
            JsonObject o = itens.get(i).getAsJsonObject();
            totalQtd = totalQtd.add(dec(o, "qtdApontada"));
            totalValor = totalValor.add(dec(o, "valorTotal"));
        }

        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                int id;
                String sqlCab = """
                    INSERT INTO fc_servico_snapshot
                        (data_ini, data_fim, fazendas, fornecedores, descricao,
                         qtde_itens, total_qtd, total_valor, id_usuario, nome_usuario)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """;
                try (PreparedStatement ps = c.prepareStatement(sqlCab, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setDate(1, Date.valueOf(dataIni));
                    ps.setDate(2, Date.valueOf(dataFim));
                    ps.setString(3, corta(fazendas, 255));
                    ps.setString(4, corta(fornecedores, 255));
                    ps.setString(5, corta(descricao, 150));
                    ps.setInt(6, itens.size());
                    ps.setBigDecimal(7, totalQtd);
                    ps.setBigDecimal(8, totalValor);
                    ps.setLong(9, idUsuario);
                    ps.setString(10, corta(nomeUsuario, 120));
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        id = rs.getInt(1);
                    }
                }

                String sqlItem = """
                    INSERT INTO fc_servico_snapshot_item
                        (id_snapshot, tipo, origem, cod_fazenda, desc_fazenda, cod_fornecedor,
                         desc_fornecedor, cod_servico, desc_servico, cod_funcionario,
                         nome_funcionario, data_movimento, unidade, qtd_apontada, valor_total)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """;
                try (PreparedStatement ps = c.prepareStatement(sqlItem)) {
                    for (int i = 0; i < itens.size(); i++) {
                        JsonObject o = itens.get(i).getAsJsonObject();
                        ps.setInt(1, id);
                        ps.setString(2, corta(str(o, "tipo"), 40));
                        ps.setString(3, corta(str(o, "origem"), 60));
                        ps.setString(4, corta(str(o, "codFazendaOrigem"), 20));
                        ps.setString(5, corta(str(o, "descFazenda"), 150));
                        ps.setString(6, corta(str(o, "codFornecedor"), 20));
                        ps.setString(7, corta(str(o, "descFornecedor"), 150));
                        ps.setString(8, corta(str(o, "codServico"), 20));
                        ps.setString(9, corta(str(o, "descServico"), 150));
                        ps.setString(10, corta(str(o, "codFuncionario"), 20));
                        ps.setString(11, corta(str(o, "nomeFuncionario"), 150));
                        String dm = str(o, "dataMovimento");
                        ps.setDate(12, dm == null || dm.isBlank() ? null : Date.valueOf(dm.substring(0, 10)));
                        ps.setString(13, corta(str(o, "unidade"), 20));
                        ps.setBigDecimal(14, dec(o, "qtdApontada"));
                        ps.setBigDecimal(15, dec(o, "valorTotal"));
                        ps.addBatch();
                        if (i % 1000 == 999) ps.executeBatch();
                    }
                    ps.executeBatch();
                }

                c.commit();
                LOG.info("Snapshot de controle de serviços #" + id + " gravado: "
                        + itens.size() + " item(ns), " + dataIni + " a " + dataFim);
                return id;

            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ── Consulta ──────────────────────────────────────────────────────────

    /**
     * Snapshots gravados, do mais recente para o mais antigo. Com período
     * informado, só os do MESMO período — comparar recortes diferentes daria
     * uma diferença que não quer dizer nada.
     */
    public List<Map<String, Object>> listar(String dataIni, String dataFim) throws SQLException {
        boolean filtra = dataIni != null && !dataIni.isBlank() && dataFim != null && !dataFim.isBlank();
        String sql = """
            SELECT id, data_ini, data_fim, fazendas, fornecedores, descricao,
                   qtde_itens, total_qtd, total_valor, nome_usuario, data_gravacao
            FROM fc_servico_snapshot
            """ + (filtra ? " WHERE data_ini = ? AND data_fim = ? " : "")
                + " ORDER BY data_gravacao DESC, id DESC LIMIT 100";

        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (filtra) {
                ps.setDate(1, Date.valueOf(dataIni));
                ps.setDate(2, Date.valueOf(dataFim));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("dataIni", String.valueOf(rs.getDate("data_ini")));
                    m.put("dataFim", String.valueOf(rs.getDate("data_fim")));
                    m.put("fazendas", rs.getString("fazendas"));
                    m.put("fornecedores", rs.getString("fornecedores"));
                    m.put("descricao", rs.getString("descricao"));
                    m.put("qtdeItens", rs.getInt("qtde_itens"));
                    m.put("totalQtd", rs.getBigDecimal("total_qtd"));
                    m.put("totalValor", rs.getBigDecimal("total_valor"));
                    m.put("nomeUsuario", rs.getString("nome_usuario"));
                    Timestamp t = rs.getTimestamp("data_gravacao");
                    m.put("dataGravacao", t == null ? null : t.toString());
                    lista.add(m);
                }
            }
        }
        return lista;
    }

    /** Itens de um snapshot, no mesmo formato das linhas da tela. */
    public List<Map<String, Object>> itens(int idSnapshot) throws SQLException {
        String sql = """
            SELECT tipo, origem, cod_fazenda, desc_fazenda, cod_fornecedor, desc_fornecedor,
                   cod_servico, desc_servico, cod_funcionario, nome_funcionario,
                   data_movimento, unidade, qtd_apontada, valor_total
            FROM fc_servico_snapshot_item
            WHERE id_snapshot = ?
            """;
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idSnapshot);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("tipo", rs.getString("tipo"));
                    m.put("origem", rs.getString("origem"));
                    m.put("codFazendaOrigem", rs.getString("cod_fazenda"));
                    m.put("descFazenda", rs.getString("desc_fazenda"));
                    m.put("codFornecedor", rs.getString("cod_fornecedor"));
                    m.put("descFornecedor", rs.getString("desc_fornecedor"));
                    m.put("codServico", rs.getString("cod_servico"));
                    m.put("descServico", rs.getString("desc_servico"));
                    m.put("codFuncionario", rs.getString("cod_funcionario"));
                    m.put("nomeFuncionario", rs.getString("nome_funcionario"));
                    Date d = rs.getDate("data_movimento");
                    m.put("dataMovimento", d == null ? null : d.toString());
                    m.put("unidade", rs.getString("unidade"));
                    m.put("qtdApontada", rs.getBigDecimal("qtd_apontada"));
                    m.put("valorTotal", rs.getBigDecimal("valor_total"));
                    lista.add(m);
                }
            }
        }
        return lista;
    }

    public void excluir(int id) throws SQLException {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM fc_servico_snapshot_item WHERE id_snapshot=?")) {
                ps.setInt(1, id); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM fc_servico_snapshot WHERE id=?")) {
                ps.setInt(1, id); ps.executeUpdate();
            }
        }
    }

    // ── Auxiliares ────────────────────────────────────────────────────────

    private static String str(JsonObject o, String campo) {
        return o.has(campo) && !o.get(campo).isJsonNull() ? o.get(campo).getAsString() : null;
    }

    private static BigDecimal dec(JsonObject o, String campo) {
        if (!o.has(campo) || o.get(campo).isJsonNull()) return BigDecimal.ZERO;
        try {
            return o.get(campo).getAsBigDecimal();
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String corta(String v, int max) {
        if (v == null) return null;
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
