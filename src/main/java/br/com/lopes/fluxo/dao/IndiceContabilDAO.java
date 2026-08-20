package br.com.lopes.fluxo.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * O de/para entre conta contábil e linha do demonstrativo (ou grupo do
 * balanço), guardado no MySQL.
 *
 * POR QUE NO BANCO E NÃO NO ARQUIVO. Os dois índices nasceram como CSV
 * dentro do WAR, gerados por script. Isso funciona, mas amarra a atualização
 * do mapa a um deploy — e o mapa muda por conta da controladoria, não do
 * sistema. Já aconteceu na prática: a planilha ganhou duas contas às 17:16 e
 * o índice tinha sido gerado às 16:49, então o painel rodou algumas horas
 * com duas contas a menos.
 *
 * COM QUEDA PARA O ARQUIVO. Enquanto ninguém importou nada, as consultas
 * devolvem vazio e quem chama usa o CSV embutido. Assim o sistema sobe
 * funcionando numa instalação nova, e a importação é uma melhoria, não um
 * pré-requisito.
 *
 * A GRAVAÇÃO É TUDO OU NADA. Um de/para pela metade não deixa rastro: as
 * contas que faltam somem do demonstrativo e os totais continuam fechando,
 * só que menores. Por isso apaga e regrava dentro de uma transação — nunca
 * existe um instante em que o índice está incompleto para quem consultar.
 */
public class IndiceContabilDAO {

    private static final Logger LOG = Logger.getLogger(IndiceContabilDAO.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    public static final String PAINEL_DRE     = "dre";
    public static final String PAINEL_BALANCO = "balanco";

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_indice_dre (
                  conta     VARCHAR(20)  NOT NULL PRIMARY KEY,
                  chave     VARCHAR(40)  NOT NULL,
                  descricao VARCHAR(200),
                  rotulo    VARCHAR(200)
                ) DEFAULT CHARSET=utf8mb4
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_indice_balanco (
                  conta  VARCHAR(20)  NOT NULL PRIMARY KEY,
                  tipo   VARCHAR(40)  NOT NULL,
                  grupo  VARCHAR(120) NOT NULL,
                  nivel  VARCHAR(120) NOT NULL,
                  nivel2 VARCHAR(160) NOT NULL
                ) DEFAULT CHARSET=utf8mb4
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_indice_importacao (
                  id       INT AUTO_INCREMENT PRIMARY KEY,
                  painel   VARCHAR(20)  NOT NULL,
                  arquivo  VARCHAR(255),
                  contas   INT          NOT NULL,
                  quando   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                  quem     VARCHAR(120)
                ) DEFAULT CHARSET=utf8mb4
                """);
        }
        return c;
    }

    // ── Leitura ───────────────────────────────────────────────────────────

    /** conta -> chave da linha do DRE. Vazio significa "use o CSV embutido". */
    public Map<String, String> dre() {
        Map<String, String> m = new LinkedHashMap<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT conta, chave FROM fc_indice_dre");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) m.put(rs.getString(1).trim(), rs.getString(2).trim());
        } catch (SQLException e) {
            // Banco fora do ar não pode derrubar o demonstrativo: quem chama
            // cai no CSV embutido, que é o mesmo mapa da última geração.
            LOG.log(Level.WARNING, "Não foi possível ler o índice do DRE; usando o arquivo", e);
        }
        return m;
    }

    /** conta -> {tipo, grupo, nivel, nivel2}. Vazio = use o CSV embutido. */
    public Map<String, String[]> balanco() {
        Map<String, String[]> m = new LinkedHashMap<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT conta, tipo, grupo, nivel, nivel2 FROM fc_indice_balanco");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                m.put(rs.getString(1).trim(), new String[]{
                        rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5) });
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Não foi possível ler o índice do balanço; usando o arquivo", e);
        }
        return m;
    }

    // ── Gravação ──────────────────────────────────────────────────────────

    /**
     * Troca o índice inteiro, numa transação só.
     *
     * @param painel PAINEL_DRE ou PAINEL_BALANCO
     * @param mapa   conta -> valores (1 campo no DRE, 4 no balanço)
     * @return quantas contas ficaram gravadas
     */
    public int salvar(String painel, Map<String, String[]> mapa,
                      Map<String, String> descricoes, Map<String, String> rotulos,
                      String arquivo, String quem) {
        boolean dre = PAINEL_DRE.equals(painel);
        String tabela = dre ? "fc_indice_dre" : "fc_indice_balanco";
        String insere = dre
                ? "INSERT INTO fc_indice_dre (conta, chave, descricao, rotulo) VALUES (?,?,?,?)"
                : "INSERT INTO fc_indice_balanco (conta, tipo, grupo, nivel, nivel2) VALUES (?,?,?,?,?)";

        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                try (Statement st = c.createStatement()) { st.executeUpdate("DELETE FROM " + tabela); }
                try (PreparedStatement ps = c.prepareStatement(insere)) {
                    for (Map.Entry<String, String[]> e : mapa.entrySet()) {
                        String conta = e.getKey();
                        String[] v = e.getValue();
                        ps.setString(1, conta);
                        if (dre) {
                            ps.setString(2, v[0]);
                            ps.setString(3, corta(descricoes == null ? null : descricoes.get(conta), 200));
                            ps.setString(4, corta(rotulos == null ? null : rotulos.get(conta), 200));
                        } else {
                            ps.setString(2, v[0]); ps.setString(3, v[1]);
                            ps.setString(4, v[2]); ps.setString(5, v[3]);
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO fc_indice_importacao (painel, arquivo, contas, quem) VALUES (?,?,?,?)")) {
                    ps.setString(1, painel);
                    ps.setString(2, corta(arquivo, 255));
                    ps.setInt(3, mapa.size());
                    ps.setString(4, corta(quem, 120));
                    ps.executeUpdate();
                }
                c.commit();
                LOG.info("Índice de " + painel + " trocado: " + mapa.size() + " contas, por " + quem);
                return mapa.size();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao gravar o índice de " + painel, e);
            throw new RuntimeException("Não foi possível gravar o índice: " + e.getMessage(), e);
        }
    }

    /** Volta ao índice embutido no sistema, esquecendo o que foi importado. */
    public void limpar(String painel) {
        String tabela = PAINEL_DRE.equals(painel) ? "fc_indice_dre" : "fc_indice_balanco";
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM " + tabela);
            LOG.info("Índice de " + painel + " limpo; volta a valer o arquivo do sistema");
        } catch (SQLException e) {
            throw new RuntimeException("Não foi possível limpar o índice: " + e.getMessage(), e);
        }
    }

    /** As últimas importações, da mais recente para a mais antiga. */
    public List<Map<String, Object>> historico(int limite) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT painel, arquivo, contas, quando, quem FROM fc_indice_importacao "
                   + "ORDER BY id DESC LIMIT " + Math.max(1, Math.min(50, limite)))) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("painel", rs.getString(1));
                    m.put("arquivo", rs.getString(2));
                    m.put("contas", rs.getInt(3));
                    java.sql.Timestamp t = rs.getTimestamp(4);
                    m.put("quando", t == null ? "" : new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(t));
                    m.put("quem", rs.getString(5));
                    out.add(m);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Não foi possível ler o histórico de importações", e);
        }
        return out;
    }

    private static String corta(String v, int max) {
        if (v == null) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }
}
