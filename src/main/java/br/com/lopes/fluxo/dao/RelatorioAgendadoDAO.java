package br.com.lopes.fluxo.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Agendamentos de envio de relatório por WhatsApp (tela Administração →
 * Relatórios WhatsApp) — "tipo_relatorio" identifica qual gerador/handler
 * cuida do envio (hoje só "combustivel"); "parametros" é um JSON livre,
 * específico de cada tipo (ex.: {"semanas":26,"combustivel":"Diesel"}),
 * interpretado por quem gera o relatório, não pelo DAO.
 *
 * "dia_semana" segue {@link DayOfWeek#getValue()} (1=segunda...7=domingo).
 * O scheduler ({@code RelatorioAgendadoScheduler}) consulta
 * {@link #listarPendentes} periodicamente e chama {@link #registrarExecucao}
 * depois de cada tentativa de envio — assim um agendamento não dispara duas
 * vezes na mesma janela mesmo que o scheduler rode a cada poucos minutos.
 *
 * Sem FOREIGN KEY (mesmo padrão de fc_lancamento_manual/fc_permissao neste
 * projeto — id_usuario é só um INT solto).
 */
public class RelatorioAgendadoDAO {

    private static final Logger LOG = Logger.getLogger(RelatorioAgendadoDAO.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_relatorio_agendado (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  tipo_relatorio VARCHAR(50) NOT NULL,
                  nome VARCHAR(150) NOT NULL,
                  dia_semana TINYINT NOT NULL,
                  hora_envio TIME NOT NULL,
                  parametros TEXT,
                  ativo CHAR(1) NOT NULL DEFAULT 'S',
                  id_usuario_criacao INT,
                  data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_relatorio_agendado_destinatario (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  id_agendamento INT NOT NULL,
                  id_usuario INT NOT NULL,
                  copia CHAR(1) NOT NULL DEFAULT 'N',
                  UNIQUE KEY uk_agendamento_usuario (id_agendamento, id_usuario),
                  INDEX idx_agendamento (id_agendamento)
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_relatorio_agendado_execucao (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  id_agendamento INT NOT NULL,
                  data_execucao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  status VARCHAR(20) NOT NULL,
                  detalhe TEXT,
                  INDEX idx_agendamento (id_agendamento)
                )
                """);
            // Recorrência por intervalo (ex.: alerta de ordem de compra a cada
            // 10 minutos) chegou depois do agendamento semanal: dia_semana e
            // hora_envio passam a aceitar nulo, e quem tem intervalo_minutos
            // preenchido é recorrente. MySQL não tem "ADD COLUMN IF NOT
            // EXISTS" aqui, então roda sempre ignorando o erro de duplicada.
            for (String ddl : new String[] {
                    "ALTER TABLE fc_relatorio_agendado ADD COLUMN intervalo_minutos INT NULL",
                    "ALTER TABLE fc_relatorio_agendado MODIFY dia_semana TINYINT NULL",
                    "ALTER TABLE fc_relatorio_agendado MODIFY hora_envio TIME NULL",
                    // Destinatário "em cópia": recebe tudo do agendamento, não só
                    // o que é da alçada dele (ver AlertaOcPendenteHandler).
                    "ALTER TABLE fc_relatorio_agendado_destinatario ADD COLUMN copia CHAR(1) NOT NULL DEFAULT 'N'" }) {
                try {
                    st.execute(ddl);
                } catch (SQLException e) {
                    if (!"42S21".equals(e.getSQLState())) {  // 42S21 = coluna duplicada, esperado a partir da 2ª vez
                        LOG.log(Level.WARNING, "Falha ao ajustar as tabelas de agendamento: " + ddl, e);
                    }
                }
            }
        }
        return c;
    }

    /** Cria as tabelas/colunas do agendamento — chamado no start da aplicação. */
    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    // ── Listagem (tela de administração) ─────────────────────────────────

    /** Lista todos os agendamentos com contagem de destinatários e status da última execução. */
    public List<Map<String, Object>> listar() throws SQLException {
        String sql = """
            SELECT a.id, a.tipo_relatorio, a.nome, a.dia_semana, a.hora_envio, a.intervalo_minutos, a.parametros,
                   a.ativo, a.data_criacao,
                   (SELECT COUNT(*) FROM fc_relatorio_agendado_destinatario d WHERE d.id_agendamento = a.id) qtde_destinatarios,
                   (SELECT e.data_execucao FROM fc_relatorio_agendado_execucao e
                     WHERE e.id_agendamento = a.id ORDER BY e.data_execucao DESC LIMIT 1) ultima_execucao,
                   (SELECT e.status FROM fc_relatorio_agendado_execucao e
                     WHERE e.id_agendamento = a.id ORDER BY e.data_execucao DESC LIMIT 1) ultimo_status,
                   (SELECT e.detalhe FROM fc_relatorio_agendado_execucao e
                     WHERE e.id_agendamento = a.id ORDER BY e.data_execucao DESC LIMIT 1) ultimo_detalhe
            FROM fc_relatorio_agendado a
            ORDER BY a.dia_semana, a.hora_envio, a.nome
            """;
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapAgendamento(rs));
        }
        return lista;
    }

    public Map<String, Object> buscarPorId(int id) throws SQLException {
        String sql = """
            SELECT id, tipo_relatorio, nome, dia_semana, hora_envio, intervalo_minutos, parametros, ativo, data_criacao,
                   id_usuario_criacao
            FROM fc_relatorio_agendado WHERE id = ?
            """;
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> m = mapAgendamento(rs);
                m.put("destinatarios", listarDestinatarios(id));
                return m;
            }
        }
    }

    private Map<String, Object> mapAgendamento(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getInt("id"));
        m.put("tipoRelatorio", rs.getString("tipo_relatorio"));
        m.put("nome", rs.getString("nome"));
        // Semanal usa diaSemana/horaEnvio; recorrente usa intervaloMinutos —
        // os campos do outro modo ficam nulos.
        Integer diaSemana = rs.getInt("dia_semana");
        if (rs.wasNull()) diaSemana = null;
        m.put("diaSemana", diaSemana);
        String horaEnvio = rs.getString("hora_envio");
        m.put("horaEnvio", horaEnvio == null ? null : horaEnvio.substring(0, 5));
        Integer intervalo = rs.getInt("intervalo_minutos");
        if (rs.wasNull()) intervalo = null;
        m.put("intervaloMinutos", intervalo);
        m.put("parametros", rs.getString("parametros"));
        m.put("ativo", "S".equals(rs.getString("ativo")));
        Timestamp criacao = rs.getTimestamp("data_criacao");
        m.put("dataCriacao", criacao == null ? null : criacao.toString());
        try {
            // Só a consulta de buscarPorId traz essa coluna — é ela que
            // alimenta a execução manual ("Executar agora").
            m.put("idUsuarioCriacao", rs.getLong("id_usuario_criacao"));
        } catch (SQLException ignorado) {
            // listar() não seleciona a coluna
        }
        try {
            m.put("qtdeDestinatarios", rs.getInt("qtde_destinatarios"));
            Timestamp ultima = rs.getTimestamp("ultima_execucao");
            m.put("ultimaExecucao", ultima == null ? null : ultima.toString());
            m.put("ultimoStatus", rs.getString("ultimo_status"));
            // O que deu errado (ou o resumo do que foi enviado): sem isso a
            // tela só mostra "(erro)" e não há como saber o motivo sem ir no
            // banco ou no log do Tomcat.
            m.put("ultimoDetalhe", rs.getString("ultimo_detalhe"));
        } catch (SQLException ignorado) {
            // colunas extras só existem na consulta de listar(); buscarPorId não as tem
        }
        return m;
    }

    // ── Destinatários ─────────────────────────────────────────────────────

    /** Destinatários de um agendamento, já com nome/telefone (join em fc_usuario). */
    public List<Map<String, Object>> listarDestinatarios(int idAgendamento) throws SQLException {
        String sql = """
            SELECT u.id, u.nome, u.telefone, u.id_logon_erp, u.etapa_aprovacao, d.copia
            FROM fc_relatorio_agendado_destinatario d
            JOIN fc_usuario u ON u.id = d.id_usuario
            WHERE d.id_agendamento = ?
            ORDER BY u.nome
            """;
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idAgendamento);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("nome", rs.getString("nome"));
                    m.put("telefone", rs.getString("telefone"));
                    // Só o alerta de ordem de compra usa: identifica o aprovador no ERP.
                    Integer idLogonErp = rs.getInt("id_logon_erp");
                    if (rs.wasNull()) idLogonErp = null;
                    m.put("idLogonErp", idLogonErp);
                    // 1º ou 2º aprovador: decide qual consulta do ERP roda.
                    m.put("etapaAprovacao", rs.getInt("etapa_aprovacao"));
                    m.put("copia", "S".equals(rs.getString("copia")));
                    lista.add(m);
                }
            }
        }
        return lista;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    /**
     * @param diaSemana        1-7, ou null quando a recorrência é por intervalo
     * @param horaEnvio        "HH:mm:ss", ou null quando a recorrência é por intervalo
     * @param intervaloMinutos de quantos em quantos minutos repete, ou null no agendamento semanal
     */
    public int criar(String tipoRelatorio, String nome, Integer diaSemana, String horaEnvio, Integer intervaloMinutos,
                      String parametros, long idUsuarioCriacao, List<Integer> destinatarios,
                      List<Integer> copias) throws SQLException {
        String sql = """
            INSERT INTO fc_relatorio_agendado
                (tipo_relatorio, nome, dia_semana, hora_envio, intervalo_minutos, parametros, ativo, id_usuario_criacao)
            VALUES (?,?,?,?,?,?, 'S', ?)
            """;
        try (Connection c = conn()) {
            int id;
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, tipoRelatorio);
                ps.setString(2, nome);
                ps.setObject(3, diaSemana);
                ps.setString(4, horaEnvio);
                ps.setObject(5, intervaloMinutos);
                ps.setString(6, parametros);
                ps.setLong(7, idUsuarioCriacao);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    id = rs.getInt(1);
                }
            }
            salvarDestinatarios(c, id, destinatarios, copias);
            return id;
        }
    }

    public void atualizar(int id, String nome, Integer diaSemana, String horaEnvio, Integer intervaloMinutos,
                          String parametros, List<Integer> destinatarios, List<Integer> copias) throws SQLException {
        String sql = """
            UPDATE fc_relatorio_agendado
               SET nome=?, dia_semana=?, hora_envio=?, intervalo_minutos=?, parametros=?
             WHERE id=?
            """;
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, nome);
                ps.setObject(2, diaSemana);
                ps.setString(3, horaEnvio);
                ps.setObject(4, intervaloMinutos);
                ps.setString(5, parametros);
                ps.setInt(6, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM fc_relatorio_agendado_destinatario WHERE id_agendamento=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            salvarDestinatarios(c, id, destinatarios, copias);
        }
    }

    /**
     * @param copias subconjunto de {@code destinatarios} que recebe tudo do
     *               agendamento, e não só o que é da alçada da pessoa
     */
    private void salvarDestinatarios(Connection c, int idAgendamento, List<Integer> destinatarios,
                                     List<Integer> copias) throws SQLException {
        if (destinatarios == null || destinatarios.isEmpty()) return;
        String sql = "INSERT INTO fc_relatorio_agendado_destinatario (id_agendamento, id_usuario, copia) VALUES (?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Integer idUsuario : destinatarios) {
                ps.setInt(1, idAgendamento);
                ps.setInt(2, idUsuario);
                ps.setString(3, copias != null && copias.contains(idUsuario) ? "S" : "N");
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void alternarAtivo(int id, boolean ativo) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("UPDATE fc_relatorio_agendado SET ativo=? WHERE id=?")) {
            ps.setString(1, ativo ? "S" : "N");
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void excluir(int id) throws SQLException {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM fc_relatorio_agendado_execucao WHERE id_agendamento=?")) {
                ps.setInt(1, id); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM fc_relatorio_agendado_destinatario WHERE id_agendamento=?")) {
                ps.setInt(1, id); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM fc_relatorio_agendado WHERE id=?")) {
                ps.setInt(1, id); ps.executeUpdate();
            }
        }
    }

    // ── Scheduler ─────────────────────────────────────────────────────────

    /**
     * Agendamentos ativos que devem rodar agora, nos dois modos:
     *
     * - semanal (intervalo_minutos nulo): dia_semana bate com {@code hoje},
     *   hora_envio já passou e ainda não houve execução hoje — assim não
     *   reenvia se o scheduler rodar de novo antes de virar o dia;
     * - recorrente (intervalo_minutos preenchido): passou pelo menos esse
     *   tanto de minutos desde a última execução (ou nunca executou).
     */
    public List<Map<String, Object>> listarPendentes(DayOfWeek hoje, LocalTime agora) throws SQLException {
        String sql = """
            SELECT a.id, a.tipo_relatorio, a.nome, a.parametros, a.id_usuario_criacao
            FROM fc_relatorio_agendado a
            WHERE a.ativo = 'S'
              AND (
                    (a.intervalo_minutos IS NULL
                     AND a.dia_semana = ?
                     AND a.hora_envio <= ?
                     AND NOT EXISTS (
                           SELECT 1 FROM fc_relatorio_agendado_execucao e
                           WHERE e.id_agendamento = a.id
                             AND DATE(e.data_execucao) = CURDATE()
                     ))
                 OR (a.intervalo_minutos IS NOT NULL
                     AND NOT EXISTS (
                           SELECT 1 FROM fc_relatorio_agendado_execucao e
                           WHERE e.id_agendamento = a.id
                             AND e.data_execucao > DATE_SUB(NOW(), INTERVAL a.intervalo_minutos MINUTE)
                     ))
              )
            """;
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, hoje.getValue());
            ps.setString(2, agora.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("tipoRelatorio", rs.getString("tipo_relatorio"));
                    m.put("nome", rs.getString("nome"));
                    m.put("parametros", rs.getString("parametros"));
                    m.put("idUsuarioCriacao", rs.getLong("id_usuario_criacao"));
                    lista.add(m);
                }
            }
        }
        return lista;
    }

    /**
     * Descarta execuções antigas — sem isso a tabela cresce rápido com os
     * agendamentos recorrentes (um registro a cada intervalo).
     */
    public void limparExecucoesAntigas(int dias) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "DELETE FROM fc_relatorio_agendado_execucao WHERE data_execucao < DATE_SUB(NOW(), INTERVAL ? DAY)")) {
            ps.setInt(1, dias);
            ps.executeUpdate();
        }
    }

    public void registrarExecucao(int idAgendamento, String status, String detalhe) throws SQLException {
        String sql = "INSERT INTO fc_relatorio_agendado_execucao (id_agendamento, status, detalhe) VALUES (?,?,?)";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idAgendamento);
            ps.setString(2, status);
            ps.setString(3, detalhe == null ? null : detalhe.substring(0, Math.min(detalhe.length(), 2000)));
            ps.executeUpdate();
        }
    }

    // ── Usuários (pra montar o multi-select de destinatários no admin) ────

    public List<Map<String, Object>> listarUsuariosAtivos() throws SQLException {
        String sql = "SELECT id, nome, telefone, id_logon_erp, etapa_aprovacao FROM fc_usuario WHERE ativo='S' ORDER BY nome";
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("nome", rs.getString("nome"));
                m.put("telefone", rs.getString("telefone"));
                Integer idLogonErp = rs.getInt("id_logon_erp");
                if (rs.wasNull()) idLogonErp = null;
                m.put("idLogonErp", idLogonErp);
                m.put("etapaAprovacao", rs.getInt("etapa_aprovacao"));
                lista.add(m);
            }
        }
        return lista;
    }
}
