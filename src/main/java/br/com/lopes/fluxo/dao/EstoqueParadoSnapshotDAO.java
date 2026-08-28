package br.com.lopes.fluxo.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Histórico (MySQL) do Alerta de Estoque Parado — um snapshot por execução,
 * pra comparar semana com semana: o que entrou na lista, o que saiu, e a
 * variação de valor.
 *
 * A chave de um item é material + almoxarifado. "Saiu da lista" cobre tanto
 * o material que voltou a movimentar quanto o que zerou o estoque — os dois
 * têm o mesmo efeito prático (deixou de ser um problema de caixa parado).
 */
public class EstoqueParadoSnapshotDAO {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_estoque_parado_execucao (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  data_execucao DATE NOT NULL,
                  qtde_itens INT NOT NULL,
                  valor_total DECIMAL(16,2) NOT NULL,
                  criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_data (data_execucao)
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_estoque_parado_item (
                  id_execucao INT NOT NULL,
                  cod_material INT NOT NULL,
                  cod_almoxarifado INT NOT NULL,
                  descricao VARCHAR(255),
                  desc_familia VARCHAR(120),
                  desc_grupomaterial VARCHAR(120),
                  localizacao VARCHAR(120),
                  qtde_estoque DECIMAL(18,4),
                  valor_total DECIMAL(16,2),
                  dias_parado INT,
                  faixa VARCHAR(20),
                  PRIMARY KEY (id_execucao, cod_material, cod_almoxarifado),
                  INDEX idx_execucao (id_execucao)
                )
                """);
        }
        return c;
    }

    /** Cria as tabelas — chamado no start da aplicação. */
    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    /**
     * Grava o snapshot do dia. Se já existir uma execução para hoje (ex.:
     * "Executar agora" chamado mais de uma vez no mesmo dia), substitui os
     * itens em vez de duplicar — o ponto da série semanal continua sendo um
     * só por dia.
     *
     * @return id da execução gravada
     */
    public int salvarSnapshot(List<Map<String, Object>> itens, LocalDate dataExecucao) throws SQLException {
        BigDecimal valorTotal = BigDecimal.ZERO;
        for (Map<String, Object> it : itens) valorTotal = valorTotal.add((BigDecimal) it.get("valorTotal"));

        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                int idExecucao;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id FROM fc_estoque_parado_execucao WHERE data_execucao = ?")) {
                    ps.setDate(1, Date.valueOf(dataExecucao));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            idExecucao = rs.getInt(1);
                            try (PreparedStatement upd = c.prepareStatement(
                                    "UPDATE fc_estoque_parado_execucao SET qtde_itens=?, valor_total=? WHERE id=?")) {
                                upd.setInt(1, itens.size());
                                upd.setBigDecimal(2, valorTotal);
                                upd.setInt(3, idExecucao);
                                upd.executeUpdate();
                            }
                            try (PreparedStatement del = c.prepareStatement(
                                    "DELETE FROM fc_estoque_parado_item WHERE id_execucao = ?")) {
                                del.setInt(1, idExecucao);
                                del.executeUpdate();
                            }
                        } else {
                            try (PreparedStatement ins = c.prepareStatement(
                                    "INSERT INTO fc_estoque_parado_execucao (data_execucao, qtde_itens, valor_total) VALUES (?,?,?)",
                                    Statement.RETURN_GENERATED_KEYS)) {
                                ins.setDate(1, Date.valueOf(dataExecucao));
                                ins.setInt(2, itens.size());
                                ins.setBigDecimal(3, valorTotal);
                                ins.executeUpdate();
                                try (ResultSet gk = ins.getGeneratedKeys()) {
                                    gk.next();
                                    idExecucao = gk.getInt(1);
                                }
                            }
                        }
                    }
                }

                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO fc_estoque_parado_item " +
                        "(id_execucao, cod_material, cod_almoxarifado, descricao, desc_familia, desc_grupomaterial, localizacao, qtde_estoque, valor_total, dias_parado, faixa) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                    for (Map<String, Object> it : itens) {
                        ins.setInt(1, idExecucao);
                        ins.setInt(2, (Integer) it.get("codMaterial"));
                        ins.setInt(3, (Integer) it.get("codAlmoxarifado"));
                        ins.setString(4, str(it.get("descricao")));
                        ins.setString(5, str(it.get("descFamilia")));
                        ins.setString(6, str(it.get("descGrupoMaterial")));
                        ins.setString(7, str(it.get("localizacao")));
                        ins.setBigDecimal(8, (BigDecimal) it.get("qtdeEstoque"));
                        ins.setBigDecimal(9, (BigDecimal) it.get("valorTotal"));
                        ins.setInt(10, (Integer) it.get("diasParado"));
                        ins.setString(11, str(it.get("faixa")));
                        ins.addBatch();
                    }
                    if (!itens.isEmpty()) ins.executeBatch();
                }

                c.commit();
                return idExecucao;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Itens da última execução gravada antes de {@code antesDe} (tipicamente hoje) — a "semana anterior". */
    public List<Map<String, Object>> buscarAnterior(LocalDate antesDe) throws SQLException {
        try (Connection c = conn()) {
            Integer idAnterior = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM fc_estoque_parado_execucao WHERE data_execucao < ? ORDER BY data_execucao DESC LIMIT 1")) {
                ps.setDate(1, Date.valueOf(antesDe));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idAnterior = rs.getInt(1);
                }
            }
            if (idAnterior == null) return new ArrayList<>();

            List<Map<String, Object>> itens = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT cod_material, cod_almoxarifado, descricao, desc_familia, desc_grupomaterial, " +
                    "localizacao, qtde_estoque, valor_total, dias_parado, faixa FROM fc_estoque_parado_item WHERE id_execucao = ?")) {
                ps.setInt(1, idAnterior);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> it = new LinkedHashMap<>();
                        it.put("codMaterial", rs.getInt("cod_material"));
                        it.put("codAlmoxarifado", rs.getInt("cod_almoxarifado"));
                        it.put("descricao", rs.getString("descricao"));
                        it.put("descFamilia", rs.getString("desc_familia"));
                        it.put("descGrupoMaterial", rs.getString("desc_grupomaterial"));
                        it.put("localizacao", rs.getString("localizacao"));
                        it.put("qtdeEstoque", rs.getBigDecimal("qtde_estoque"));
                        it.put("valorTotal", rs.getBigDecimal("valor_total"));
                        it.put("diasParado", rs.getInt("dias_parado"));
                        it.put("faixa", rs.getString("faixa"));
                        itens.add(it);
                    }
                }
            }
            return itens;
        }
    }

    /**
     * Compara a lista atual com a anterior por material+almoxarifado e monta
     * o resumo executivo: quem entrou, quem saiu, variação de valor total.
     */
    public Map<String, Object> comparar(List<Map<String, Object>> atual, List<Map<String, Object>> anterior) {
        Map<String, Map<String, Object>> porChaveAnterior = new LinkedHashMap<>();
        for (Map<String, Object> it : anterior) porChaveAnterior.put(chave(it), it);

        Map<String, Map<String, Object>> porChaveAtual = new LinkedHashMap<>();
        for (Map<String, Object> it : atual) porChaveAtual.put(chave(it), it);

        List<Map<String, Object>> entraram = new ArrayList<>();
        for (Map<String, Object> it : atual) {
            if (!porChaveAnterior.containsKey(chave(it))) entraram.add(it);
        }

        List<Map<String, Object>> sairam = new ArrayList<>();
        for (Map<String, Object> it : anterior) {
            if (!porChaveAtual.containsKey(chave(it))) sairam.add(it);
        }

        BigDecimal totalAtual = soma(atual);
        BigDecimal totalAnterior = soma(anterior);
        BigDecimal variacaoValor = totalAtual.subtract(totalAnterior);
        BigDecimal variacaoPercentual = totalAnterior.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : variacaoValor.divide(totalAnterior, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("temAnterior", !anterior.isEmpty());
        resumo.put("totalAtual", totalAtual);
        resumo.put("totalAnterior", totalAnterior);
        resumo.put("variacaoValor", variacaoValor);
        resumo.put("variacaoPercentual", variacaoPercentual);
        resumo.put("qtdeAtual", atual.size());
        resumo.put("qtdeAnterior", anterior.size());
        resumo.put("entraram", entraram);
        resumo.put("sairam", sairam);
        return resumo;
    }

    private static String chave(Map<String, Object> it) {
        return it.get("codMaterial") + "|" + it.get("codAlmoxarifado");
    }

    private static BigDecimal soma(List<Map<String, Object>> itens) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> it : itens) total = total.add((BigDecimal) it.get("valorTotal"));
        return total;
    }

    private static String str(Object v) { return v == null ? null : v.toString(); }
}
