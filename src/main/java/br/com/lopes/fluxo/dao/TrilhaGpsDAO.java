package br.com.lopes.fluxo.dao;

import java.sql.Connection;
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
 * Trilhas de GPS gravadas no campo (MySQL), uma coleção por usuário.
 *
 * Os pontos ficam num único campo JSON em vez de uma linha por ponto: uma
 * caminhada de duas horas passa de mil pontos, e o único uso deles é
 * redesenhar a linha no mapa — não há consulta por ponto que justifique o
 * custo de inserir e indexar tudo isso. As medidas que a listagem precisa
 * (distância, duração, quantidade) ficam em colunas próprias.
 *
 * A gravação acontece no aparelho, inclusive sem rede; isto aqui é o
 * destino final, quando a trilha consegue subir.
 *
 * Sem FOREIGN KEY, como nas demais tabelas fc_* deste projeto.
 */
public class TrilhaGpsDAO {

    private static final Logger LOG = Logger.getLogger(TrilhaGpsDAO.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    /** Teto por trilha — protege contra um envio malformado encher a tabela. */
    public static final int MAX_PONTOS = 50_000;

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_trilha_gps (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  id_usuario INT NOT NULL,
                  nome_usuario VARCHAR(120),
                  nome VARCHAR(150),
                  inicio TIMESTAMP NULL,
                  fim TIMESTAMP NULL,
                  duracao_s INT NOT NULL DEFAULT 0,
                  distancia_m INT NOT NULL DEFAULT 0,
                  qtde_pontos INT NOT NULL DEFAULT 0,
                  pontos MEDIUMTEXT,
                  id_local VARCHAR(40),
                  data_envio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_usuario_local (id_usuario, id_local),
                  INDEX idx_usuario (id_usuario, inicio)
                )
                """);
        }
        return c;
    }

    /** Cria a tabela — chamado no start da aplicação. */
    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    /**
     * Grava a trilha. {@code idLocal} é o identificador que o aparelho gerou
     * ao começar a gravar: é ele que impede a mesma trilha de entrar duas
     * vezes quando o envio é repetido — e repetir é o normal aqui, já que a
     * trilha fica esperando rede para subir.
     *
     * @return id da trilha no banco
     */
    public int salvar(long idUsuario, String nomeUsuario, String nome, String idLocal,
                      String inicio, String fim, int duracaoS, int distanciaM,
                      int qtdePontos, String pontosJson) throws SQLException {
        String sql = """
            INSERT INTO fc_trilha_gps
                (id_usuario, nome_usuario, nome, inicio, fim, duracao_s, distancia_m,
                 qtde_pontos, pontos, id_local)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
                nome = VALUES(nome), fim = VALUES(fim), duracao_s = VALUES(duracao_s),
                distancia_m = VALUES(distancia_m), qtde_pontos = VALUES(qtde_pontos),
                pontos = VALUES(pontos), id = LAST_INSERT_ID(id)
            """;
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, idUsuario);
            ps.setString(2, corta(nomeUsuario, 120));
            ps.setString(3, corta(nome, 150));
            ps.setTimestamp(4, carimbo(inicio));
            ps.setTimestamp(5, carimbo(fim));
            ps.setInt(6, duracaoS);
            ps.setInt(7, distanciaM);
            ps.setInt(8, qtdePontos);
            ps.setString(9, pontosJson);
            ps.setString(10, corta(idLocal, 40));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                int id = rs.next() ? rs.getInt(1) : 0;
                LOG.info("Trilha de GPS gravada #" + id + " (usuário " + idUsuario
                        + ", " + qtdePontos + " pontos, " + distanciaM + " m)");
                return id;
            }
        }
    }

    /** Trilhas do usuário, da mais recente para a mais antiga, sem os pontos. */
    public List<Map<String, Object>> listar(long idUsuario, int limite) throws SQLException {
        String sql = """
            SELECT id, nome, inicio, fim, duracao_s, distancia_m, qtde_pontos, id_local
            FROM fc_trilha_gps
            WHERE id_usuario = ?
            ORDER BY inicio DESC, id DESC
            LIMIT ?
            """;
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, idUsuario);
            ps.setInt(2, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("nome", rs.getString("nome"));
                    m.put("inicio", texto(rs.getTimestamp("inicio")));
                    m.put("fim", texto(rs.getTimestamp("fim")));
                    m.put("duracaoS", rs.getInt("duracao_s"));
                    m.put("distanciaM", rs.getInt("distancia_m"));
                    m.put("qtdePontos", rs.getInt("qtde_pontos"));
                    m.put("idLocal", rs.getString("id_local"));
                    lista.add(m);
                }
            }
        }
        return lista;
    }

    /**
     * Os pontos de uma trilha. Recebe o usuário junto de propósito: assim o
     * filtro de dono é do banco, e não uma verificação que alguém possa
     * esquecer de fazer antes de chamar.
     */
    public String pontos(long idUsuario, int id) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT pontos FROM fc_trilha_gps WHERE id = ? AND id_usuario = ?")) {
            ps.setInt(1, id);
            ps.setLong(2, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** @return true se apagou algo — false quando a trilha é de outro usuário */
    public boolean excluir(long idUsuario, int id) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "DELETE FROM fc_trilha_gps WHERE id = ? AND id_usuario = ?")) {
            ps.setInt(1, id);
            ps.setLong(2, idUsuario);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Auxiliares ────────────────────────────────────────────────────────

    private static Timestamp carimbo(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Timestamp.from(java.time.Instant.parse(iso));
        } catch (Exception e) {
            return null;
        }
    }

    private static String texto(Timestamp t) {
        return t == null ? null : t.toInstant().toString();
    }

    private static String corta(String v, int max) {
        if (v == null) return null;
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
