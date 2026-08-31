package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.RowMapperUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Controle (MySQL) das notas fiscais detectadas em anexo de e-mail na caixa
 * de Compras — ver {@link br.com.lopes.fluxo.util.ImapComprasUtil} (quem lê
 * o e-mail) e {@link br.com.lopes.fluxo.agendamento.AlertaNfSemEntradaHandler}
 * (quem decide o que fazer com cada linha a cada ciclo).
 *
 * Cada linha é um ANEXO (não um e-mail — um e-mail pode trazer mais de uma
 * nota), identificado por Message-ID + nome do arquivo. status:
 *   PENDENTE            — nota decodificada (número/série), ainda sem entrada confirmada no Oracle
 *   ENTRADA_CONFIRMADA  — o Oracle já mostrou o item dando entrada; para de alertar
 *   SEM_CHAVE           — o anexo parece nota fiscal, mas não foi possível ler a chave de acesso
 *                         (ex.: PDF escaneado, sem camada de texto) — fica visível para conferência manual
 */
public class NfEmailDAO {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    /** A tela não precisa guardar confirmadas para sempre — passado esse tempo, some da lista (mas o registro fica no banco). */
    private static final int DIAS_HISTORICO_CONFIRMADAS = 120;

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    public void garantirEstrutura() throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_nf_email (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  message_id VARCHAR(191) NOT NULL,
                  nome_anexo VARCHAR(191) NOT NULL,
                  data_email DATETIME NULL,
                  remetente VARCHAR(180) NOT NULL DEFAULT '',
                  assunto VARCHAR(300) NOT NULL DEFAULT '',
                  chave_acesso VARCHAR(44) NULL,
                  nrnf VARCHAR(20) NULL,
                  serie VARCHAR(10) NULL,
                  cnpj_emitente VARCHAR(14) NULL,
                  status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
                  data_deteccao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  data_confirmacao DATETIME NULL,
                  UNIQUE KEY uk_msg_anexo (message_id, nome_anexo),
                  INDEX idx_status (status)
                )
                """);
        }
    }

    /** Uma linha nova a registrar — ver os campos de {@link #inserirSeNovo}. */
    public static final class Registro {
        public String messageId, nomeAnexo, remetente, assunto, chaveAcesso, nrnf, serie, cnpjEmitente, status;
        public Date dataEmail;
    }

    /** Insere se ainda não existir esta combinação de e-mail + anexo. @return true se inseriu algo novo. */
    public boolean inserirSeNovo(Registro r) throws SQLException {
        String sql = """
            INSERT IGNORE INTO fc_nf_email
                (message_id, nome_anexo, data_email, remetente, assunto, chave_acesso, nrnf, serie, cnpj_emitente, status)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, limitar(r.messageId, 191));
            ps.setString(2, limitar(r.nomeAnexo, 191));
            ps.setTimestamp(3, r.dataEmail == null ? null : new Timestamp(r.dataEmail.getTime()));
            ps.setString(4, limitar(r.remetente, 180));
            ps.setString(5, limitar(r.assunto, 300));
            ps.setString(6, r.chaveAcesso);
            ps.setString(7, r.nrnf);
            ps.setString(8, r.serie);
            ps.setString(9, r.cnpjEmitente);
            ps.setString(10, r.status);
            return ps.executeUpdate() > 0;
        }
    }

    /** Pendentes com número/série decodificados — os únicos que dá para checar contra o Oracle. */
    public List<Map<String, Object>> listarPendentesParaChecar() throws SQLException {
        String sql = "SELECT id, nrnf, serie FROM fc_nf_email "
                   + "WHERE status = 'PENDENTE' AND nrnf IS NOT NULL AND serie IS NOT NULL";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return RowMapperUtil.toList(rs);
        }
    }

    public void marcarConfirmada(int id) throws SQLException {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(
                "UPDATE fc_nf_email SET status = 'ENTRADA_CONFIRMADA', data_confirmacao = NOW() WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Pendentes (sem entrada confirmada) cujo e-mail já passou do prazo combinado — o gatilho do alerta. */
    public List<Map<String, Object>> listarPendentesAtrasados(int prazoDias) throws SQLException {
        String sql = """
            SELECT * FROM fc_nf_email
            WHERE status = 'PENDENTE' AND nrnf IS NOT NULL AND serie IS NOT NULL
              AND data_email IS NOT NULL AND data_email <= DATE_SUB(NOW(), INTERVAL ? DAY)
            ORDER BY data_email
            """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, prazoDias);
            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }
        }
    }

    /** Tudo que interessa à tela: pendentes, sem-chave e confirmadas recentes (para mostrar que já resolveu). */
    public List<Map<String, Object>> listarTudo() throws SQLException {
        String sql = """
            SELECT * FROM fc_nf_email
            WHERE status <> 'ENTRADA_CONFIRMADA' OR data_confirmacao >= DATE_SUB(NOW(), INTERVAL ? DAY)
            ORDER BY data_email DESC
            """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, DIAS_HISTORICO_CONFIRMADAS);
            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }
        }
    }

    private static String limitar(String s, int max) {
        if (s == null) return "";
        String v = s.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
