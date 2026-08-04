package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.AgroOracleConnectionUtil;

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
 * Boletim Diário de Operação — a versão digital do papel "Movimentação de
 * Transporte" que a Divisão Operacional preenche à mão.
 *
 * O papel tem cabeçalho (data, motorista, veículo, matrícula, hora e KM do
 * dia) e uma tabela de trechos, cada um com origem, destino, código de
 * operação e quantidade transportada. O modelo aqui é esse: um boletim, N
 * trechos. Hora e KM ficam no cabeçalho porque no papel valem para o dia
 * inteiro, não para cada trecho.
 *
 * Um boletim por dia. Quando há outro, ele é marcado como extra e exige
 * justificativa — a regra não é impedir, é obrigar a explicar, que é o que
 * o papel não fazia.
 */
public class ManobraDAO {

    private static final Logger LOG = Logger.getLogger(ManobraDAO.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    /** Teto de trechos num boletim — o papel tem 14 linhas; a folga é larga. */
    public static final int MAX_TRECHOS = 60;

    /**
     * Códigos de operação impressos no rodapé do formulário. Ficam no código,
     * e não numa tabela, porque são a legenda do próprio papel: mudam quando o
     * formulário mudar, não no dia a dia.
     */
    public static final String[][] OPERACOES = {
        { "2870", "Transp. Cana/Semente" },
        { "7887", "Abastecimento Combust." },
        { "7877", "Transp. Material" },
        { "877",  "Transp. Mat. Indústria" },
        { "5892", "Bombeiro" },
        { "7882", "Socorro Mecânico" },
        { "2896", "Transp. De Torta" },
        { "7889", "Transp. Material Estrada" },
        { "995",  "Transp. Resíduos (Meio Amb.)" },
        { "7881", "Transp. Equipamento (Prancha - Entressafra)" },
        { "4824", "Transp. Água Herbicida Sc." },
        { "3891", "Transp. Água Herbicida Pl." },
        { "3871", "Transp. Adubo Pl." },
        { "4872", "Transp. Adubo Sc." },
        { "7894", "Transp. Equipamento (Prancha - Moagem)" }
    };

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_manobra (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  id_usuario_externo INT NOT NULL,
                  id_empresa INT NOT NULL,
                  data DATE NOT NULL,
                  seq INT NOT NULL DEFAULT 1,
                  extra CHAR(1) NOT NULL DEFAULT 'N',
                  justificativa VARCHAR(500),
                  cod_equipamento VARCHAR(20),
                  desc_equipamento VARCHAR(150),
                  cod_contrato VARCHAR(30),
                  nome_motorista VARCHAR(150),
                  matricula VARCHAR(20),
                  hora_inicial VARCHAR(5),
                  hora_final VARCHAR(5),
                  km_inicial INT,
                  km_final INT,
                  observacao VARCHAR(500),
                  criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_dia (id_usuario_externo, data, seq),
                  INDEX idx_empresa_data (id_empresa, data),
                  INDEX idx_data (data)
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_manobra_trecho (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  id_manobra INT NOT NULL,
                  ordem INT NOT NULL,
                  cod_operacao VARCHAR(10),
                  desc_operacao VARCHAR(80),
                  cod_fazenda_origem VARCHAR(10),
                  desc_fazenda_origem VARCHAR(80),
                  cod_fazenda_destino VARCHAR(10),
                  desc_fazenda_destino VARCHAR(80),
                  quantidade INT,
                  INDEX idx_manobra (id_manobra, ordem)
                )
                """);
        }
        return c;
    }

    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    // ── Fazendas (Oracle) ─────────────────────────────────────────────────

    private static final String SQL_FAZENDAS = """
        select fazenda.cod_fazenda, fazenda.descricao
        from   agricola.fazenda
        order  by fazenda.descricao
        """;

    /**
     * Fazendas do ERP. Lista fechada de propósito: digitado à mão, o mesmo
     * lugar vira duas fazendas diferentes por causa de uma letra, e aí não há
     * relatório que feche.
     */
    public List<Map<String, Object>> fazendas() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = AgroOracleConnectionUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(SQL_FAZENDAS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("cod", rs.getString("cod_fazenda"));
                m.put("descricao", rs.getString("descricao"));
                lista.add(m);
            }
        }
        return lista;
    }

    // ── Boletins ──────────────────────────────────────────────────────────

    /** Próxima sequência do dia: 1 é o boletim normal, daí em diante é extra. */
    public int proximaSeq(int idUsuarioExterno, String data) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COALESCE(MAX(seq),0)+1 FROM fc_manobra WHERE id_usuario_externo=? AND data=?")) {
            ps.setInt(1, idUsuarioExterno);
            ps.setString(2, data);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    /**
     * Grava o boletim e seus trechos.
     *
     * Cabeçalho e trechos na mesma transação: boletim sem trecho é um dia de
     * trabalho sem registro nenhum, e é pior que não ter gravado.
     *
     * @return id do boletim
     */
    public int salvar(Map<String, Object> cab, List<Map<String, Object>> trechos) throws SQLException {
        String sql = """
            INSERT INTO fc_manobra
                (id_usuario_externo, id_empresa, data, seq, extra, justificativa,
                 cod_equipamento, desc_equipamento, cod_contrato, nome_motorista, matricula,
                 hora_inicial, hora_final, km_inicial, km_final, observacao)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1,    (Integer) cab.get("idUsuarioExterno"));
                    ps.setInt(2,    (Integer) cab.get("idEmpresa"));
                    ps.setString(3, (String)  cab.get("data"));
                    ps.setInt(4,    (Integer) cab.get("seq"));
                    ps.setString(5, (String)  cab.get("extra"));
                    ps.setString(6, (String)  cab.get("justificativa"));
                    ps.setString(7, (String)  cab.get("codEquipamento"));
                    ps.setString(8, (String)  cab.get("descEquipamento"));
                    ps.setString(9, (String)  cab.get("codContrato"));
                    ps.setString(10,(String)  cab.get("nomeMotorista"));
                    ps.setString(11,(String)  cab.get("matricula"));
                    ps.setString(12,(String)  cab.get("horaInicial"));
                    ps.setString(13,(String)  cab.get("horaFinal"));
                    setInt(ps, 14, cab.get("kmInicial"));
                    setInt(ps, 15, cab.get("kmFinal"));
                    ps.setString(16,(String)  cab.get("observacao"));
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        id = rs.next() ? rs.getInt(1) : 0;
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO fc_manobra_trecho
                            (id_manobra, ordem, cod_operacao, desc_operacao,
                             cod_fazenda_origem, desc_fazenda_origem,
                             cod_fazenda_destino, desc_fazenda_destino, quantidade)
                        VALUES (?,?,?,?,?,?,?,?,?)
                        """)) {
                    int ordem = 1;
                    for (Map<String, Object> t : trechos) {
                        ps.setInt(1, id);
                        ps.setInt(2, ordem++);
                        ps.setString(3, (String) t.get("codOperacao"));
                        ps.setString(4, (String) t.get("descOperacao"));
                        ps.setString(5, (String) t.get("codOrigem"));
                        ps.setString(6, (String) t.get("descOrigem"));
                        ps.setString(7, (String) t.get("codDestino"));
                        ps.setString(8, (String) t.get("descDestino"));
                        setInt(ps, 9, t.get("quantidade"));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
                LOG.info("Boletim de manobra #" + id + " gravado ("
                        + trechos.size() + " trechos, usuário " + cab.get("idUsuarioExterno") + ")");
                return id;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static void setInt(PreparedStatement ps, int pos, Object v) throws SQLException {
        if (v == null) ps.setNull(pos, java.sql.Types.INTEGER);
        else ps.setInt(pos, ((Number) v).intValue());
    }

    /**
     * Boletins, do mais recente para o mais antigo.
     *
     * @param idUsuarioExterno quando informado, só os dessa pessoa — é assim
     *        que a empresa de fora só enxerga o que é dela, com o filtro no
     *        SQL e não numa checagem que alguém possa esquecer
     */
    public List<Map<String, Object>> listar(Integer idUsuarioExterno, String de, String ate, int limite)
            throws SQLException {
        String sql = """
            SELECT m.*, e.razao_social,
                   (SELECT COUNT(*) FROM fc_manobra_trecho t WHERE t.id_manobra = m.id) trechos
            FROM   fc_manobra m
            JOIN   fc_empresa_externa e ON e.id = m.id_empresa
            WHERE  (? IS NULL OR m.id_usuario_externo = ?)
            AND    (? IS NULL OR m.data >= ?)
            AND    (? IS NULL OR m.data <= ?)
            ORDER BY m.data DESC, m.seq DESC
            LIMIT ?
            """;
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (idUsuarioExterno == null) { ps.setNull(1, java.sql.Types.INTEGER); ps.setNull(2, java.sql.Types.INTEGER); }
            else { ps.setInt(1, idUsuarioExterno); ps.setInt(2, idUsuarioExterno); }
            ps.setString(3, de);  ps.setString(4, de);
            ps.setString(5, ate); ps.setString(6, ate);
            ps.setInt(7, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(cabecalhoDe(rs));
            }
        }
        return lista;
    }

    /** Um boletim com os trechos, respeitando o dono quando informado. */
    public Map<String, Object> buscar(int id, Integer idUsuarioExterno) throws SQLException {
        Map<String, Object> m = null;
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT m.*, e.razao_social, 0 trechos
                    FROM   fc_manobra m
                    JOIN   fc_empresa_externa e ON e.id = m.id_empresa
                    WHERE  m.id = ? AND (? IS NULL OR m.id_usuario_externo = ?)
                    """)) {
                ps.setInt(1, id);
                if (idUsuarioExterno == null) { ps.setNull(2, java.sql.Types.INTEGER); ps.setNull(3, java.sql.Types.INTEGER); }
                else { ps.setInt(2, idUsuarioExterno); ps.setInt(3, idUsuarioExterno); }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) m = cabecalhoDe(rs);
                }
            }
            if (m == null) return null;
            List<Map<String, Object>> trechos = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM fc_manobra_trecho WHERE id_manobra = ? ORDER BY ordem")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("ordem", rs.getInt("ordem"));
                        t.put("codOperacao", rs.getString("cod_operacao"));
                        t.put("descOperacao", rs.getString("desc_operacao"));
                        t.put("codOrigem", rs.getString("cod_fazenda_origem"));
                        t.put("descOrigem", rs.getString("desc_fazenda_origem"));
                        t.put("codDestino", rs.getString("cod_fazenda_destino"));
                        t.put("descDestino", rs.getString("desc_fazenda_destino"));
                        t.put("quantidade", rs.getObject("quantidade"));
                        trechos.add(t);
                    }
                }
            }
            m.put("trechos", trechos);
        }
        return m;
    }

    private static Map<String, Object> cabecalhoDe(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getInt("id"));
        m.put("data", String.valueOf(rs.getDate("data")));
        m.put("seq", rs.getInt("seq"));
        m.put("extra", rs.getString("extra"));
        m.put("justificativa", rs.getString("justificativa"));
        m.put("codEquipamento", rs.getString("cod_equipamento"));
        m.put("descEquipamento", rs.getString("desc_equipamento"));
        m.put("codContrato", rs.getString("cod_contrato"));
        m.put("nomeMotorista", rs.getString("nome_motorista"));
        m.put("matricula", rs.getString("matricula"));
        m.put("horaInicial", rs.getString("hora_inicial"));
        m.put("horaFinal", rs.getString("hora_final"));
        m.put("kmInicial", rs.getObject("km_inicial"));
        m.put("kmFinal", rs.getObject("km_final"));
        m.put("observacao", rs.getString("observacao"));
        m.put("empresa", rs.getString("razao_social"));
        m.put("qtdeTrechos", rs.getInt("trechos"));
        return m;
    }
}
