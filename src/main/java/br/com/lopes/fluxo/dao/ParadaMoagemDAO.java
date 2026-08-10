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
import java.util.logging.Logger;

/**
 * Paradas da moagem.
 *
 * Uma parada nasce aberta — hora de início, motivo, parte e previsão — e é
 * fechada quando a moagem volta. Enquanto está aberta, é ela que responde
 * "estamos parados desde quando", que é a pergunta que o WhatsApp faz o dia
 * inteiro quando não há sistema.
 *
 * Só pode haver uma parada aberta por vez: a moagem é uma só. É a regra que
 * impede duas pessoas registrarem a mesma parada e o grupo receber o aviso
 * duas vezes.
 *
 * Data e hora ficam num único TIMESTAMP em vez de colunas separadas. Parada
 * que começa 23h40 e termina 00h20 é o caso comum na indústria, e com hora
 * solta o tempo parado daria negativo.
 */
public class ParadaMoagemDAO {

    private static final Logger LOG = Logger.getLogger(ParadaMoagemDAO.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_parada_moagem (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  inicio TIMESTAMP NOT NULL,
                  retorno TIMESTAMP NULL,
                  motivo VARCHAR(255) NOT NULL,
                  parte VARCHAR(150),
                  previsao VARCHAR(60),
                  id_usuario_inicio INT,
                  nome_usuario_inicio VARCHAR(120),
                  id_usuario_retorno INT,
                  nome_usuario_retorno VARCHAR(120),
                  aviso_parada VARCHAR(255),
                  aviso_retorno VARCHAR(255),
                  criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_inicio (inicio),
                  INDEX idx_aberta (retorno)
                )
                """);
            // Quem recebe os avisos. Lista própria, e não a de relatórios
            // agendados: parada é evento, não agendamento, e misturar as duas
            // faria mexer numa mexer na outra sem querer.
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_parada_destinatario (
                  id_usuario INT NOT NULL PRIMARY KEY
                )
                """);
            // Partes/setores oferecidos na tela. Ficam em tabela porque quem
            // conhece a lista é a indústria, não o código.
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_parada_parte (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  descricao VARCHAR(150) NOT NULL UNIQUE,
                  ativo CHAR(1) NOT NULL DEFAULT 'S',
                  ordem INT NOT NULL DEFAULT 0
                )
                """);
            for (String[] p : PARTES_INICIAIS) {
                st.execute("INSERT IGNORE INTO fc_parada_parte (descricao, ordem) VALUES ('"
                         + p[0].replace("'", "''") + "'," + p[1] + ")");
            }
        }
        return c;
    }

    /** Semeadas na criação da tabela; a partir daí quem manda é o cadastro. */
    private static final String[][] PARTES_INICIAIS = {
        { "Falha Operacional (Caldeira)",   "1" },
        { "Falha Operacional (Moenda)",     "2" },
        { "Falha Operacional (Extração)",   "3" },
        { "Falha Mecânica",                 "4" },
        { "Falha Elétrica",                 "5" },
        { "Falta de Cana",                  "6" },
        { "Limpeza / Manutenção Programada","7" },
        { "Chuva",                          "8" },
        { "Outros",                         "9" }
    };

    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    // ── Parada aberta ─────────────────────────────────────────────────────

    /** A parada em aberto, ou null quando a moagem está rodando. */
    public Map<String, Object> aberta() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM fc_parada_moagem WHERE retorno IS NULL ORDER BY inicio DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? linha(rs) : null;
        }
    }

    /**
     * Abre uma parada.
     *
     * @return id da parada, ou -1 quando já existe uma aberta — a moagem é
     *         uma só, e duas paradas abertas significam que alguém registrou
     *         em duplicidade e o grupo receberia dois avisos
     */
    public int abrir(String inicio, String motivo, String parte, String previsao,
                     long idUsuario, String nomeUsuario) throws SQLException {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id FROM fc_parada_moagem WHERE retorno IS NULL FOR UPDATE");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) { c.rollback(); return -1; }
                }
                int id;
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO fc_parada_moagem
                            (inicio, motivo, parte, previsao, id_usuario_inicio, nome_usuario_inicio)
                        VALUES (?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, inicio);
                    ps.setString(2, motivo);
                    ps.setString(3, parte);
                    ps.setString(4, previsao);
                    ps.setLong(5, idUsuario);
                    ps.setString(6, nomeUsuario);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        id = rs.next() ? rs.getInt(1) : 0;
                    }
                }
                c.commit();
                LOG.info("Parada de moagem aberta #" + id + " por " + nomeUsuario + " — " + motivo);
                return id;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Fecha a parada.
     *
     * @return a parada fechada, ou null quando ela já não estava aberta —
     *         dois retornos registrados mandariam dois avisos de volta
     */
    public Map<String, Object> fechar(int id, String retorno, long idUsuario, String nomeUsuario)
            throws SQLException {
        try (Connection c = conn()) {
            int n;
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE fc_parada_moagem
                    SET    retorno = ?, id_usuario_retorno = ?, nome_usuario_retorno = ?
                    WHERE  id = ? AND retorno IS NULL
                    """)) {
                ps.setString(1, retorno);
                ps.setLong(2, idUsuario);
                ps.setString(3, nomeUsuario);
                ps.setInt(4, id);
                n = ps.executeUpdate();
            }
            if (n == 0) return null;
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM fc_parada_moagem WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? linha(rs) : null;
                }
            }
        }
    }

    public Map<String, Object> buscar(int id) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM fc_parada_moagem WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? linha(rs) : null;
            }
        }
    }

    /** Registra o resultado do envio, sem impedir nada quando falha. */
    public void registrarAviso(int id, boolean retorno, String resultado) {
        String col = retorno ? "aviso_retorno" : "aviso_parada";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE fc_parada_moagem SET " + col + " = ? WHERE id = ?")) {
            ps.setString(1, resultado == null ? null : resultado.substring(0, Math.min(255, resultado.length())));
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("Não foi possível registrar o aviso da parada " + id + ": " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listar(String de, String ate, int limite) throws SQLException {
        String sql = """
            SELECT * FROM fc_parada_moagem
            WHERE  (? IS NULL OR DATE(inicio) >= ?)
            AND    (? IS NULL OR DATE(inicio) <= ?)
            ORDER BY inicio DESC
            LIMIT ?
            """;
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, de);  ps.setString(2, de);
            ps.setString(3, ate); ps.setString(4, ate);
            ps.setInt(5, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(linha(rs));
            }
        }
        return lista;
    }

    // ── Partes e destinatários ────────────────────────────────────────────

    public List<Map<String, Object>> partes(boolean somenteAtivas) throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id, descricao, ativo FROM fc_parada_parte "
               + (somenteAtivas ? "WHERE ativo = 'S' " : "") + "ORDER BY ordem, descricao");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("descricao", rs.getString("descricao"));
                m.put("ativo", rs.getString("ativo"));
                lista.add(m);
            }
        }
        return lista;
    }

    public void salvarParte(Integer id, String descricao, String ativo, int ordem) throws SQLException {
        try (Connection c = conn()) {
            if (id == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO fc_parada_parte (descricao, ativo, ordem) VALUES (?,?,?)")) {
                    ps.setString(1, descricao); ps.setString(2, ativo); ps.setInt(3, ordem);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE fc_parada_parte SET descricao=?, ativo=?, ordem=? WHERE id=?")) {
                    ps.setString(1, descricao); ps.setString(2, ativo);
                    ps.setInt(3, ordem); ps.setInt(4, id);
                    ps.executeUpdate();
                }
            }
        }
    }

    /** Quem recebe os avisos, já com o telefone do cadastro de usuários. */
    public List<Map<String, Object>> destinatarios() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        String sql = """
            SELECT u.id, u.nome, u.telefone
            FROM   fc_parada_destinatario d
            JOIN   fc_usuario u ON u.id = d.id_usuario
            WHERE  u.ativo = 'S'
            ORDER BY u.nome
            """;
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("nome", rs.getString("nome"));
                m.put("telefone", rs.getString("telefone"));
                lista.add(m);
            }
        }
        return lista;
    }

    public void gravarDestinatarios(List<Integer> ids) throws SQLException {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                try (Statement st = c.createStatement()) {
                    st.execute("DELETE FROM fc_parada_destinatario");
                }
                if (ids != null && !ids.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO fc_parada_destinatario (id_usuario) VALUES (?)")) {
                        for (Integer id : ids) { ps.setInt(1, id); ps.addBatch(); }
                        ps.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    // ── Auxiliar ──────────────────────────────────────────────────────────

    private static Map<String, Object> linha(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getInt("id"));
        m.put("inicio", texto(rs.getTimestamp("inicio")));
        m.put("retorno", texto(rs.getTimestamp("retorno")));
        m.put("motivo", rs.getString("motivo"));
        m.put("parte", rs.getString("parte"));
        m.put("previsao", rs.getString("previsao"));
        m.put("nomeUsuarioInicio", rs.getString("nome_usuario_inicio"));
        m.put("nomeUsuarioRetorno", rs.getString("nome_usuario_retorno"));
        m.put("avisoParada", rs.getString("aviso_parada"));
        m.put("avisoRetorno", rs.getString("aviso_retorno"));
        return m;
    }

    /** "yyyy-MM-dd HH:mm:ss" — o que a tela e o formatador da mensagem usam. */
    private static String texto(java.sql.Timestamp t) {
        return t == null ? null : t.toString().substring(0, 19);
    }
}
