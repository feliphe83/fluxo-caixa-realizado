package br.com.lopes.fluxo.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A cotação do açúcar NY nº 11, guardada no MySQL.
 *
 * POR QUE NO BANCO. O painel de indicadores lia o açúcar de um serviço que
 * só responde de dentro da rede da usina (o 179.97.38.58) — de fora, e às
 * vezes de dentro, a tabela do açúcar ficava vazia. Agora um coletor busca
 * os vencimentos na bolsa e GRAVA aqui; o painel só lê o banco. Assim a
 * fonte externa cair não deixa a tela sem número: fica o último gravado,
 * com a hora em que foi coletado à vista.
 *
 * DUAS TABELAS. Uma guarda o retrato atual — uma linha por vencimento, que é
 * apagada e regravada inteira a cada coleta (um retrato pela metade sumiria
 * com vencimentos sem deixar rastro). A outra guarda o fechamento de cada
 * dia do primeiro vencimento, um registro por dia, para o gráfico dos
 * últimos quinze dias.
 *
 * A IDADE VEM DAQUI. Quem lê recebe também quando a coleta aconteceu, não
 * quando a página bateu no banco — numa parede de mercado, cotação de ontem
 * mostrada como se fosse de agora é pior do que tela apagada.
 */
public class CotacaoAcucarDAO {

    private static final Logger LOG = Logger.getLogger(CotacaoAcucarDAO.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    /** Um vencimento do contrato, como veio da bolsa. */
    public static final class Vencimento {
        public String symbol;      // ICEUS:SBV2026
        public String rotulo;      // Out26
        public String descricao;   // Sugar No. 11 Futures (Oct 2026)
        public LocalDate expiracao;
        public double ultimo, abertura, alta, baixa, variacao;
    }

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_cotacao_acucar (
                  symbol      VARCHAR(24)  NOT NULL PRIMARY KEY,
                  rotulo      VARCHAR(12)  NOT NULL,
                  descricao   VARCHAR(140),
                  expiracao   DATE,
                  ordem       INT          NOT NULL,
                  ultimo      DECIMAL(12,4),
                  abertura    DECIMAL(12,4),
                  alta        DECIMAL(12,4),
                  baixa       DECIMAL(12,4),
                  variacao    DECIMAL(12,4),
                  coletado_em TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                ) DEFAULT CHARSET=utf8mb4
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_cotacao_acucar_hist (
                  dia           DATE         NOT NULL PRIMARY KEY,
                  fechamento    DECIMAL(12,4) NOT NULL,
                  atualizado_em TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) DEFAULT CHARSET=utf8mb4
                """);
        }
        return c;
    }

    public void garantirEstrutura() {
        try (Connection c = conn()) {
            // conn() já cria as tabelas.
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Não foi possível preparar as tabelas da cotação do açúcar", e);
        }
    }

    /**
     * Regrava o retrato inteiro dos vencimentos e anota o fechamento do
     * primeiro deles no histórico do dia. Tudo numa transação: nunca existe
     * um instante em que o retrato está pela metade para quem consultar.
     *
     * @param dia o pregão a que os dados se referem (para o histórico)
     * @return quantos vencimentos foram gravados
     */
    public int gravar(List<Vencimento> vencimentos, LocalDate dia) throws SQLException {
        if (vencimentos == null || vencimentos.isEmpty()) {
            throw new SQLException("nenhum vencimento para gravar — a bolsa não devolveu nada");
        }
        try (Connection c = conn()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (Statement st = c.createStatement()) {
                    st.execute("DELETE FROM fc_cotacao_acucar");
                }
                String ins = """
                    INSERT INTO fc_cotacao_acucar
                      (symbol, rotulo, descricao, expiracao, ordem,
                       ultimo, abertura, alta, baixa, variacao, coletado_em)
                    VALUES (?,?,?,?,?,?,?,?,?,?, CURRENT_TIMESTAMP)
                    """;
                int ordem = 0;
                try (PreparedStatement ps = c.prepareStatement(ins)) {
                    for (Vencimento v : vencimentos) {
                        ps.setString(1, v.symbol);
                        ps.setString(2, v.rotulo);
                        ps.setString(3, v.descricao);
                        if (v.expiracao != null) ps.setDate(4, java.sql.Date.valueOf(v.expiracao));
                        else ps.setNull(4, java.sql.Types.DATE);
                        ps.setInt(5, ordem++);
                        ps.setDouble(6, v.ultimo);
                        ps.setDouble(7, v.abertura);
                        ps.setDouble(8, v.alta);
                        ps.setDouble(9, v.baixa);
                        ps.setDouble(10, v.variacao);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                // Fechamento do primeiro vencimento no histórico do dia.
                double frontMonth = vencimentos.get(0).ultimo;
                if (frontMonth > 0 && dia != null) {
                    try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO fc_cotacao_acucar_hist (dia, fechamento)
                            VALUES (?, ?)
                            ON DUPLICATE KEY UPDATE fechamento = VALUES(fechamento)
                            """)) {
                        ps.setDate(1, java.sql.Date.valueOf(dia));
                        ps.setDouble(2, frontMonth);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        }
        return vencimentos.size();
    }

    /** O retrato atual, no formato que o painel espera, ou null se nunca coletou. */
    public Retrato lerVencimentos() throws SQLException {
        JsonArray arr = new JsonArray();
        Timestamp coletado = null;
        try (Connection c = conn();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                 SELECT rotulo, ultimo, abertura, alta, baixa, variacao, coletado_em
                 FROM fc_cotacao_acucar ORDER BY ordem
                 """)) {
            while (rs.next()) {
                JsonObject o = new JsonObject();
                o.addProperty("mes", rs.getString("rotulo"));
                o.addProperty("ultimo", rs.getDouble("ultimo"));
                o.addProperty("abertura", rs.getDouble("abertura"));
                o.addProperty("maxima", rs.getDouble("alta"));
                o.addProperty("minima", rs.getDouble("baixa"));
                o.addProperty("variacao", rs.getDouble("variacao"));
                arr.add(o);
                coletado = rs.getTimestamp("coletado_em");
            }
        }
        if (arr.size() == 0) return null;
        return new Retrato(arr, coletado);
    }

    /** O fechamento diário do primeiro vencimento, últimos {@code dias} pregões. */
    public JsonArray ler15Dias(int dias) throws SQLException {
        List<JsonObject> pilha = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("""
                 SELECT dia, fechamento FROM fc_cotacao_acucar_hist
                 ORDER BY dia DESC LIMIT ?
                 """)) {
            ps.setInt(1, Math.max(1, dias));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate dia = rs.getDate("dia").toLocalDate();
                    JsonObject o = new JsonObject();
                    o.addProperty("data", dia.toString());
                    // Rótulo curto para o eixo do gráfico (dd/MM), como no
                    // gráfico do dólar; o serieAcucar do painel usa este.
                    o.addProperty("mes", String.format("%02d/%02d",
                            dia.getDayOfMonth(), dia.getMonthValue()));
                    o.addProperty("ultimo", rs.getDouble("fechamento"));
                    pilha.add(o);
                }
            }
        }
        // Veio do mais novo para o mais velho; o gráfico quer cronológico.
        JsonArray arr = new JsonArray();
        for (int i = pilha.size() - 1; i >= 0; i--) arr.add(pilha.get(i));
        return arr;
    }

    /** O retrato atual e a hora em que foi coletado. */
    public record Retrato(JsonArray dado, Timestamp coletadoEm) {
        public long idadeMinutos() {
            if (coletadoEm == null) return 0;
            long ms = System.currentTimeMillis() - coletadoEm.getTime();
            return Math.max(0, Math.round(ms / 60000.0));
        }
    }
}
