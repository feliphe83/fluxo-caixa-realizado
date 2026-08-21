package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.PrecoCanaConsecanaParser.Registro;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * O preço do kg de ATR do CONSECANA-AL, guardado no MySQL.
 *
 * POR QUE NO BANCO. O painel mostrava o preço da cana de um CSV embutido no
 * WAR — para trocar de mês era preciso editar arquivo e reimplantar. Mas a
 * publicação é mensal e vem em PDF do sindicato; agora a controladoria
 * importa o PDF pela administração e o painel passa a mostrar os últimos
 * meses sozinho, sem deploy.
 *
 * O QUE GUARDA. Uma linha por mês: o preço da matéria-prima (bruto) e o
 * líquido após deduções, tanto NO MÊS quanto o ACUMULADO da safra que a
 * publicação traz. O painel usa o do mês em cada linha e o acumulado da
 * publicação mais recente na linha "Acumulado".
 *
 * SEMENTE. Sobe já com os quatro meses que a usina forneceu (abr–jul/2026),
 * para o painel não nascer vazio; importar um PDF novo só acrescenta.
 */
public class PrecoCanaDAO {

    private static final Logger LOG = Logger.getLogger(PrecoCanaDAO.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    /** Os quatro meses que a usina entregou para semear. */
    private static final double[][] SEMENTE = {
            // anomes, bruto, liquido, acumBruto, acumLiquido
            { 202604, 1.2237, 1.2053, 1.1990, 1.1810 },
            { 202605, 1.1696, 1.1521, 1.1971, 1.1791 },
            { 202606, 1.1896, 1.1718, 1.1967, 1.1787 },
            { 202607, 1.2925, 1.2731, 1.1989, 1.1809 },
    };
    private static final String[] ABREV =
            { "Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez" };

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_preco_cana (
                  anomes       INT           NOT NULL PRIMARY KEY,
                  rotulo       VARCHAR(16)   NOT NULL,
                  bruto        DECIMAL(10,4) NOT NULL,
                  liquido      DECIMAL(10,4) NOT NULL,
                  acum_bruto   DECIMAL(10,4) NOT NULL,
                  acum_liquido DECIMAL(10,4) NOT NULL,
                  importado_em TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) DEFAULT CHARSET=utf8mb4
                """);
        }
        return c;
    }

    public void garantirEstrutura() {
        try (Connection c = conn()) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM fc_preco_cana")) {
                if (rs.next() && rs.getInt(1) == 0) semear(c);
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Não foi possível preparar a tabela do preço da cana", e);
        }
    }

    private void semear(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SQL_UPSERT)) {
            for (double[] s : SEMENTE) {
                int anomes = (int) s[0];
                ps.setInt(1, anomes);
                ps.setString(2, ABREV[(anomes % 100) - 1] + "/" + (anomes / 100));
                ps.setDouble(3, s[1]); ps.setDouble(4, s[2]);
                ps.setDouble(5, s[3]); ps.setDouble(6, s[4]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        LOG.info("Preço da cana semeado com " + SEMENTE.length + " meses.");
    }

    private static final String SQL_UPSERT = """
        INSERT INTO fc_preco_cana (anomes, rotulo, bruto, liquido, acum_bruto, acum_liquido)
        VALUES (?,?,?,?,?,?)
        ON DUPLICATE KEY UPDATE rotulo=VALUES(rotulo), bruto=VALUES(bruto),
          liquido=VALUES(liquido), acum_bruto=VALUES(acum_bruto), acum_liquido=VALUES(acum_liquido)
        """;

    /** Grava (ou regrava) um mês vindo de um PDF importado. */
    public void salvar(Registro r) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(SQL_UPSERT)) {
            ps.setInt(1, r.anomes());
            ps.setString(2, r.rotulo());
            ps.setDouble(3, r.bruto());
            ps.setDouble(4, r.liquido());
            ps.setDouble(5, r.acumBruto());
            ps.setDouble(6, r.acumLiquido());
            ps.executeUpdate();
        }
    }

    /** Uma linha da tabela do painel. */
    public record Linha(String rotulo, double bruto, double liquido) {}

    /**
     * Os últimos {@code n} meses (em ordem cronológica) mais a linha
     * "Acumulado" da publicação mais recente, ou null se ainda não há dados.
     */
    public List<Linha> ultimos(int n) throws SQLException {
        List<Linha> asc = new ArrayList<>();
        double acumB = 0, acumL = 0;
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT rotulo, bruto, liquido, acum_bruto, acum_liquido
                 FROM fc_preco_cana ORDER BY anomes DESC LIMIT ?
                 """)) {
            ps.setInt(1, Math.max(1, n));
            try (ResultSet rs = ps.executeQuery()) {
                boolean primeiro = true;
                while (rs.next()) {
                    // Veio do mais novo para o mais velho; o acumulado é o do
                    // primeiro (o mês mais recente).
                    if (primeiro) { acumB = rs.getDouble("acum_bruto"); acumL = rs.getDouble("acum_liquido"); primeiro = false; }
                    asc.add(0, new Linha(rs.getString("rotulo"), rs.getDouble("bruto"), rs.getDouble("liquido")));
                }
            }
        }
        if (asc.isEmpty()) return null;
        asc.add(new Linha("Acumulado", acumB, acumL));
        return asc;
    }

    /** Tudo o que está gravado, do mais novo para o mais velho — para a administração. */
    public List<Linha> todos() throws SQLException {
        List<Linha> lista = new ArrayList<>();
        try (Connection c = conn();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT rotulo, bruto, liquido FROM fc_preco_cana ORDER BY anomes DESC")) {
            while (rs.next()) lista.add(new Linha(rs.getString("rotulo"), rs.getDouble("bruto"), rs.getDouble("liquido")));
        }
        return lista;
    }
}
