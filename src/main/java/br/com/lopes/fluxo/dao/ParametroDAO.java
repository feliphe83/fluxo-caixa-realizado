package br.com.lopes.fluxo.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Parâmetros gerais da intranet — chave e valor, um lugar só para toda a
 * aplicação.
 *
 * Nasceu da safra padrão, que não é assunto do mapa de talhões: é assunto da
 * empresa. Insumos, produtividade, controle de serviços e o próprio mapa
 * precisam concordar sobre qual safra está corrente, e ter isso escrito em
 * cada tela seria garantir que um dia elas discordem.
 *
 * Chave e valor em vez de uma coluna por parâmetro porque o que vem depois
 * (limites, e-mails de aviso, dias de janela) não muda a estrutura — só
 * acrescenta uma linha, sem ALTER TABLE nem deploy de esquema.
 */
public class ParametroDAO {

    private static final Logger LOG = Logger.getLogger(ParametroDAO.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    /** Safra usada por padrão quando a tela não recebe outra. */
    public static final String SAFRA_PADRAO = "safra.padrao";
    /** Quantas safras recentes ficam à escolha (e são guardadas para uso sem sinal). */
    public static final String SAFRA_QUANTIDADE = "safra.quantidade";

    /**
     * Valores iniciais. São gravados uma única vez, na criação da tabela: se
     * fossem reaplicados a cada start, desfariam o que o administrador mudou.
     */
    private static final String[][] INICIAIS = {
        { SAFRA_PADRAO,     "76", "Safra que as telas abrem por padrão" },
        { SAFRA_QUANTIDADE, "4",  "Quantas safras recentes aparecem na lista e ficam guardadas para uso sem sinal" }
    };

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_parametro (
                  chave VARCHAR(60) PRIMARY KEY,
                  valor VARCHAR(255),
                  descricao VARCHAR(255),
                  atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  atualizado_por VARCHAR(120)
                )
                """);
            // INSERT IGNORE: semeia o que falta sem tocar no que já existe.
            for (String[] p : INICIAIS) {
                st.execute("INSERT IGNORE INTO fc_parametro (chave, valor, descricao) VALUES ("
                        + "'" + p[0] + "','" + p[1] + "','" + p[2] + "')");
            }
        }
        return c;
    }

    /** Cria a tabela e semeia os iniciais — chamado no start da aplicação. */
    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    /** Todos os parâmetros, na ordem da chave. */
    public Map<String, String> todos() throws SQLException {
        Map<String, String> m = new LinkedHashMap<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT chave, valor FROM fc_parametro ORDER BY chave");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) m.put(rs.getString("chave"), rs.getString("valor"));
        }
        return m;
    }

    /** Descrição de cada chave, para a tela de administração se explicar. */
    public Map<String, String> descricoes() throws SQLException {
        Map<String, String> m = new LinkedHashMap<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT chave, descricao FROM fc_parametro ORDER BY chave");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) m.put(rs.getString("chave"), rs.getString("descricao"));
        }
        return m;
    }

    /**
     * Valor de uma chave, ou {@code padrao} quando ela não existe ou o banco
     * não responde. Quem chama daqui está no meio de montar uma tela e não
     * pode quebrar por causa de um parâmetro.
     */
    public String valor(String chave, String padrao) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT valor FROM fc_parametro WHERE chave = ?")) {
            ps.setString(1, chave);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    if (v != null && !v.isBlank()) return v.trim();
                }
            }
        } catch (SQLException e) {
            LOG.warning("Parâmetro " + chave + " não pôde ser lido, usando " + padrao + ": " + e.getMessage());
        }
        return padrao;
    }

    /** Mesmo que {@link #valor}, já convertido — inclusive quando vier lixo. */
    public int inteiro(String chave, int padrao) {
        try {
            return Integer.parseInt(valor(chave, String.valueOf(padrao)).trim());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    /**
     * Grava os parâmetros informados. Só mexe nas chaves que já existem: um
     * corpo malformado não vira parâmetro novo e não polui a tabela.
     *
     * @return quantos foram de fato alterados
     */
    public int gravar(Map<String, String> valores, String quem) throws SQLException {
        int n = 0;
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE fc_parametro SET valor = ?, atualizado_por = ? WHERE chave = ?")) {
            for (Map.Entry<String, String> e : valores.entrySet()) {
                ps.setString(1, e.getValue() == null ? null : e.getValue().trim());
                ps.setString(2, quem);
                ps.setString(3, e.getKey());
                n += ps.executeUpdate();
            }
        }
        LOG.info("Parâmetros alterados por " + quem + ": " + valores);
        return n;
    }
}
