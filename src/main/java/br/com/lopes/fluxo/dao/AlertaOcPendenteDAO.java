package br.com.lopes.fluxo.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Apoio (MySQL) do alerta de ordens de compra pendentes: quem recebe e o
 * que já foi avisado.
 *
 * O controle do que já foi avisado é o que permite rodar de dez em dez
 * minutos sem repetir mensagem: enquanto a ordem continuar pendente de
 * aprovação ela segue aparecendo na consulta do ERP, mas só é enviada na
 * primeira vez que aparece para aquele destinatário.
 */
public class AlertaOcPendenteDAO {

    private static final Logger LOG = Logger.getLogger(AlertaOcPendenteDAO.class.getName());

    /** Chave da permissão (fc_permissao) que libera o recebimento do alerta. */
    public static final String PERMISSAO = "alerta_oc_pendente";

    /** Registros de envio mais antigos que isso são descartados (a ordem já foi aprovada há muito). */
    private static final int DIAS_HISTORICO = 180;

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_alerta_oc_enviado (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  id_usuario INT NOT NULL,
                  tipo VARCHAR(40) NOT NULL,
                  nr_solicitacao VARCHAR(40) NOT NULL,
                  data_envio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_usuario_oc (id_usuario, tipo, nr_solicitacao),
                  INDEX idx_data_envio (data_envio)
                )
                """);
            // fc_usuario já existia antes deste alerta, então a coluna do
            // id_logon entra por ALTER. MySQL não tem "ADD COLUMN IF NOT
            // EXISTS" nessa versão: rodar sempre e ignorar o erro de coluna
            // duplicada é mais simples (e igualmente seguro) do que consultar
            // o information_schema antes.
            try {
                st.execute("ALTER TABLE fc_usuario ADD COLUMN id_logon_erp INT NULL");
                LOG.info("Coluna fc_usuario.id_logon_erp criada.");
            } catch (SQLException e) {
                if (!"42S21".equals(e.getSQLState())) {  // 42S21 = coluna duplicada, esperado a partir da 2ª vez
                    LOG.log(Level.WARNING, "Não foi possível garantir a coluna fc_usuario.id_logon_erp", e);
                }
            }
        }
        return c;
    }

    /**
     * Garante a tabela de controle e a coluna fc_usuario.id_logon_erp.
     * Chamado no start da aplicação porque a tela de Usuários já lê essa
     * coluna — sem isso ela quebraria na janela entre o boot e a primeira
     * varredura do alerta.
     */
    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    /**
     * Quem deve receber o alerta: usuário ativo, com a permissão
     * {@link #PERMISSAO} concedida, telefone cadastrado e id_logon do ERP
     * preenchido (sem ele não dá pra saber quais ordens são dele).
     *
     * Administrador não entra automaticamente, diferente de outras
     * permissões do sistema: receber alerta é opt-in, senão todo admin
     * passaria a receber mensagem de WhatsApp sem ter pedido.
     */
    public List<Map<String, Object>> listarDestinatarios() throws SQLException {
        String sql = """
            SELECT u.id, u.nome, u.telefone, u.id_logon_erp
            FROM fc_usuario u
            JOIN fc_permissao p ON p.id_usuario = u.id AND p.relatorio = ? AND p.ativo = 'S'
            WHERE u.ativo = 'S'
              AND u.telefone IS NOT NULL AND TRIM(u.telefone) <> ''
              AND u.id_logon_erp IS NOT NULL AND u.id_logon_erp > 0
            ORDER BY u.nome
            """;
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, PERMISSAO);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("nome", rs.getString("nome"));
                    m.put("telefone", rs.getString("telefone"));
                    m.put("idLogonErp", rs.getInt("id_logon_erp"));
                    lista.add(m);
                }
            }
        }
        return lista;
    }

    /** Ordens que este usuário já recebeu, como "tipo|nr_solicitacao". */
    public Set<String> jaEnviados(int idUsuario) throws SQLException {
        String sql = """
            SELECT tipo, nr_solicitacao FROM fc_alerta_oc_enviado
            WHERE id_usuario = ? AND data_envio >= DATE_SUB(NOW(), INTERVAL ? DAY)
            """;
        Set<String> chaves = new HashSet<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setInt(2, DIAS_HISTORICO);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) chaves.add(chave(rs.getString("tipo"), rs.getString("nr_solicitacao")));
            }
        }
        return chaves;
    }

    /**
     * Marca a ordem como avisada. INSERT IGNORE porque a chave única
     * (usuário + tipo + solicitação) é a garantia real de não repetir — se
     * duas execuções se cruzarem, a segunda simplesmente não insere.
     */
    public void registrarEnviado(int idUsuario, String tipo, String nrSolicitacao) throws SQLException {
        String sql = "INSERT IGNORE INTO fc_alerta_oc_enviado (id_usuario, tipo, nr_solicitacao) VALUES (?,?,?)";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setString(2, tipo);
            ps.setString(3, nrSolicitacao);
            ps.executeUpdate();
        }
    }

    /** Descarta o histórico antigo, pra tabela não crescer indefinidamente. */
    public void limparHistoricoAntigo() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "DELETE FROM fc_alerta_oc_enviado WHERE data_envio < DATE_SUB(NOW(), INTERVAL ? DAY)")) {
            ps.setInt(1, DIAS_HISTORICO);
            ps.executeUpdate();
        }
    }

    public static String chave(String tipo, String nrSolicitacao) {
        return tipo + "|" + nrSolicitacao;
    }
}
