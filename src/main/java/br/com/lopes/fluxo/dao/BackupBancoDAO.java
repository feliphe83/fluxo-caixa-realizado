package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.ArmazenamentoBackupUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuração e histórico do backup do banco MySQL "intranet" (Administração
 * → Backup do Banco). Só existe UMA configuração de agendamento (linha
 * id=1) — não é uma lista de agendamentos como
 * {@link RelatorioAgendadoDAO}, porque aqui não há "tipo" nem "destinatário":
 * é só "quais dias, que horas, guardar quantos dias".
 *
 * dias_semana é uma lista separada por vírgula de DayOfWeek.getValue()
 * (1=segunda .. 7=domingo), ex.: "1,3,5" — mesma convenção de dia da semana já
 * usada em fc_relatorio_agendado, só que aqui pode ter mais de um por vez.
 */
public class BackupBancoDAO {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    public void garantirEstrutura() throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_backup_agendamento (
                  id INT PRIMARY KEY,
                  dias_semana VARCHAR(20) NOT NULL DEFAULT '',
                  hora_execucao TIME NULL,
                  manter_dias INT NOT NULL DEFAULT 30,
                  ativo CHAR(1) NOT NULL DEFAULT 'N',
                  data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_backup_execucao (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  data_execucao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  origem VARCHAR(20) NOT NULL,
                  status VARCHAR(20) NOT NULL,
                  detalhe TEXT,
                  caminho_arquivo VARCHAR(255),
                  nome_arquivo VARCHAR(255),
                  tamanho_bytes BIGINT,
                  id_usuario INT NULL,
                  INDEX idx_data (data_execucao)
                )
                """);
            // Uma única linha de configuração (id=1), criada preguiçosamente na
            // primeira vez — desativada por padrão até o admin escolher dias/hora.
            st.execute("INSERT IGNORE INTO fc_backup_agendamento (id, ativo) VALUES (1, 'N')");
        }
    }

    /** {diasSemana:[1,3,5], horaExecucao:"02:00", manterDias:30, ativo:true}. */
    public Map<String, Object> buscarConfig() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT dias_semana, hora_execucao, manter_dias, ativo FROM fc_backup_agendamento WHERE id=1")) {
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Object> m = new LinkedHashMap<>();
                if (rs.next()) {
                    m.put("diasSemana", csvParaLista(rs.getString("dias_semana")));
                    Time hora = rs.getTime("hora_execucao");
                    m.put("horaExecucao", hora == null ? null : hora.toLocalTime().toString().substring(0, 5));
                    m.put("manterDias", rs.getInt("manter_dias"));
                    m.put("ativo", "S".equals(rs.getString("ativo")));
                } else {
                    m.put("diasSemana", new ArrayList<Integer>());
                    m.put("horaExecucao", null);
                    m.put("manterDias", 30);
                    m.put("ativo", false);
                }
                return m;
            }
        }
    }

    public void salvarConfig(List<Integer> diasSemana, String horaExecucao, int manterDias, boolean ativo) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO fc_backup_agendamento (id, dias_semana, hora_execucao, manter_dias, ativo) " +
                 "VALUES (1, ?, ?, ?, ?) " +
                 "ON DUPLICATE KEY UPDATE dias_semana=VALUES(dias_semana), hora_execucao=VALUES(hora_execucao), " +
                 "manter_dias=VALUES(manter_dias), ativo=VALUES(ativo)")) {
            ps.setString(1, listaParaCsv(diasSemana));
            if (horaExecucao == null || horaExecucao.isBlank()) ps.setNull(2, java.sql.Types.TIME);
            else ps.setTime(2, Time.valueOf(horaExecucao.length() == 5 ? horaExecucao + ":00" : horaExecucao));
            ps.setInt(3, manterDias);
            ps.setString(4, ativo ? "S" : "N");
            ps.executeUpdate();
        }
    }

    /**
     * A configuração está ativa, o dia de hoje está marcado e já passou da
     * hora marcada — e ainda não rodou hoje (dedup por data, mesma ideia de
     * {@link RelatorioAgendadoDAO#listarPendentes}, só que aqui é uma
     * configuração só, não uma lista).
     */
    public boolean estaNaHoraDeRodarAutomatico(DayOfWeek hoje, LocalTime agora) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT a.dias_semana, a.hora_execucao FROM fc_backup_agendamento a " +
                 "WHERE a.id=1 AND a.ativo='S' AND a.hora_execucao IS NOT NULL " +
                 "AND a.hora_execucao <= ? " +
                 "AND NOT EXISTS (SELECT 1 FROM fc_backup_execucao e " +
                 "  WHERE e.origem='automatico' AND DATE(e.data_execucao) = CURDATE())")) {
            ps.setTime(1, Time.valueOf(agora.withSecond(0).withNano(0)));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                List<Integer> dias = csvParaLista(rs.getString("dias_semana"));
                return dias.contains(hoje.getValue());
            }
        }
    }

    public int registrarExecucao(String origem, String status, String detalhe,
                                  String caminhoArquivo, String nomeArquivo, Long tamanhoBytes,
                                  Integer idUsuario) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO fc_backup_execucao (origem, status, detalhe, caminho_arquivo, nome_arquivo, tamanho_bytes, id_usuario) " +
                 "VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, origem);
            ps.setString(2, status);
            ps.setString(3, detalhe);
            if (caminhoArquivo == null) ps.setNull(4, java.sql.Types.VARCHAR); else ps.setString(4, caminhoArquivo);
            if (nomeArquivo == null) ps.setNull(5, java.sql.Types.VARCHAR); else ps.setString(5, nomeArquivo);
            if (tamanhoBytes == null) ps.setNull(6, java.sql.Types.BIGINT); else ps.setLong(6, tamanhoBytes);
            if (idUsuario == null) ps.setNull(7, java.sql.Types.INTEGER); else ps.setInt(7, idUsuario);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    public List<Map<String, Object>> listarHistorico(int limite) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT e.id, e.data_execucao, e.origem, e.status, e.detalhe, e.caminho_arquivo, " +
                 "       e.nome_arquivo, e.tamanho_bytes, e.id_usuario, u.nome nome_usuario " +
                 "FROM fc_backup_execucao e " +
                 "LEFT JOIN fc_usuario u ON u.id = e.id_usuario " +
                 "ORDER BY e.data_execucao DESC LIMIT ?")) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> lista = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    Timestamp ts = rs.getTimestamp("data_execucao");
                    m.put("dataExecucao", ts == null ? null : ts.toLocalDateTime().toString());
                    m.put("origem", rs.getString("origem"));
                    m.put("status", rs.getString("status"));
                    m.put("detalhe", rs.getString("detalhe"));
                    m.put("nomeArquivo", rs.getString("nome_arquivo"));
                    m.put("temArquivo", rs.getString("caminho_arquivo") != null);
                    long tam = rs.getLong("tamanho_bytes");
                    m.put("tamanhoBytes", rs.wasNull() ? null : tam);
                    m.put("nomeUsuario", rs.getString("nome_usuario"));
                    lista.add(m);
                }
                return lista;
            }
        }
    }

    /** Caminho relativo salvo (pra resolver via ArmazenamentoBackupUtil) e o nome pra sugerir no download. */
    public Map<String, String> buscarArquivo(int idExecucao) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT caminho_arquivo, nome_arquivo FROM fc_backup_execucao WHERE id=? AND status='sucesso'")) {
            ps.setInt(1, idExecucao);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, String> m = new LinkedHashMap<>();
                m.put("caminho", rs.getString("caminho_arquivo"));
                m.put("nome", rs.getString("nome_arquivo"));
                return m;
            }
        }
    }

    /**
     * Apaga do disco e do banco os backups AUTOMÁTICOS mais antigos que
     * manterDias — só esses, nunca os manuais: um backup manual costuma ser
     * uma foto de segurança antes de algo arriscado, e sumir sozinho
     * surpreenderia quem gerou justamente para ele continuar existindo depois
     * do prazo. Chamado depois de cada execução automática bem-sucedida.
     */
    public void limparAntigos(int manterDias) throws SQLException {
        List<String> caminhos = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement sel = c.prepareStatement(
                 "SELECT caminho_arquivo FROM fc_backup_execucao " +
                 "WHERE status='sucesso' AND origem='automatico' AND caminho_arquivo IS NOT NULL " +
                 "AND data_execucao < DATE_SUB(NOW(), INTERVAL ? DAY)")) {
            sel.setInt(1, manterDias);
            try (ResultSet rs = sel.executeQuery()) {
                while (rs.next()) caminhos.add(rs.getString("caminho_arquivo"));
            }
        }
        for (String caminho : caminhos) ArmazenamentoBackupUtil.apagar(caminho);

        try (Connection c = conn();
             PreparedStatement del = c.prepareStatement(
                 "DELETE FROM fc_backup_execucao WHERE origem='automatico' AND data_execucao < DATE_SUB(NOW(), INTERVAL ? DAY)")) {
            del.setInt(1, manterDias);
            del.executeUpdate();
        }
    }

    private static List<Integer> csvParaLista(String csv) {
        List<Integer> lista = new ArrayList<>();
        if (csv == null || csv.isBlank()) return lista;
        for (String p : csv.split(",")) {
            p = p.trim();
            if (!p.isEmpty()) lista.add(Integer.parseInt(p));
        }
        return lista;
    }

    private static String listaParaCsv(List<Integer> lista) {
        if (lista == null || lista.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Integer i : lista) {
            if (i == null || i < 1 || i > 7) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(i);
        }
        return sb.toString();
    }
}
