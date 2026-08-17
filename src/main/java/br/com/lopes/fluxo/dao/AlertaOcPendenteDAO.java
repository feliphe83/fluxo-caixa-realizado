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
 * Apoio (MySQL) do alerta de ordens de compra pendentes: guarda o que já
 * foi avisado a cada pessoa.
 *
 * É esse controle que permite rodar de poucos em poucos minutos sem
 * repetir mensagem: enquanto a ordem continuar pendente de aprovação ela
 * segue aparecendo na consulta do ERP, mas só é enviada na primeira vez
 * que aparece para aquele destinatário.
 *
 * Quem recebe vem do próprio agendamento (Administração → Relatórios
 * WhatsApp), não daqui.
 */
public class AlertaOcPendenteDAO {

    private static final Logger LOG = Logger.getLogger(AlertaOcPendenteDAO.class.getName());

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
                  item VARCHAR(150) NOT NULL DEFAULT '',
                  data_envio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_usuario_oc_item (id_usuario, tipo, nr_solicitacao, item),
                  INDEX idx_data_envio (data_envio)
                )
                """);
            // O aviso é por ITEM (uma mensagem por material), então a chave de
            // controle ganhou o material. Bases criadas na primeira versão
            // tinham a chave só até nr_solicitacao — o que barraria o 2º item
            // da mesma solicitação. Cada passo roda solto porque só o primeiro
            // deploy precisa de todos.
            for (String ddl : new String[] {
                    "ALTER TABLE fc_alerta_oc_enviado ADD COLUMN item VARCHAR(150) NOT NULL DEFAULT ''",
                    "ALTER TABLE fc_alerta_oc_enviado DROP INDEX uk_usuario_oc",
                    "ALTER TABLE fc_alerta_oc_enviado ADD UNIQUE KEY uk_usuario_oc_item (id_usuario, tipo, nr_solicitacao, item)" }) {
                try {
                    st.execute(ddl);
                } catch (SQLException e) {
                    // 42S21 = coluna duplicada, 42000 = índice inexistente/duplicado: esperados a partir da 2ª vez
                    if (!"42S21".equals(e.getSQLState()) && !"42000".equals(e.getSQLState())) {
                        LOG.log(Level.WARNING, "Falha ao ajustar fc_alerta_oc_enviado: " + ddl, e);
                    }
                }
            }
            // fc_usuario já existia antes deste alerta, então as colunas do
            // aprovador entram por ALTER. MySQL não tem "ADD COLUMN IF NOT
            // EXISTS" nessa versão: rodar sempre e ignorar o erro de coluna
            // duplicada é mais simples (e igualmente seguro) do que consultar
            // o information_schema antes.
            //
            // etapa_aprovacao diz se a pessoa é 1º ou 2º aprovador — são
            // consultas diferentes no ERP (ver OrdemCompraPendenteDAO). Quem
            // já estava cadastrado vira 1º, que era o comportamento único até
            // aqui.
            for (String ddl : new String[] {
                    "ALTER TABLE fc_usuario ADD COLUMN id_logon_erp INT NULL",
                    "ALTER TABLE fc_usuario ADD COLUMN etapa_aprovacao TINYINT NOT NULL DEFAULT 1" }) {
                try {
                    st.execute(ddl);
                    LOG.info("Estrutura de aprovador ajustada: " + ddl);
                } catch (SQLException e) {
                    if (!"42S21".equals(e.getSQLState())) {  // 42S21 = coluna duplicada, esperado a partir da 2ª vez
                        LOG.log(Level.WARNING, "Não foi possível ajustar fc_usuario: " + ddl, e);
                    }
                }
            }
        }
        return c;
    }

    /**
     * Garante a tabela de controle e as colunas fc_usuario.id_logon_erp /
     * fc_usuario.etapa_aprovacao.
     * Chamado no start da aplicação porque a tela de Usuários já lê essa
     * coluna — sem isso ela quebraria na janela entre o boot e a primeira
     * varredura do alerta.
     */
    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    /** Itens que este usuário já recebeu, como "tipo|nr_solicitacao|material". */
    public Set<String> jaEnviados(int idUsuario) throws SQLException {
        String sql = """
            SELECT tipo, nr_solicitacao, item FROM fc_alerta_oc_enviado
            WHERE id_usuario = ? AND data_envio >= DATE_SUB(NOW(), INTERVAL ? DAY)
            """;
        Set<String> chaves = new HashSet<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setInt(2, DIAS_HISTORICO);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chaves.add(chave(rs.getString("tipo"), rs.getString("nr_solicitacao"), rs.getString("item")));
                }
            }
        }
        return chaves;
    }

    /**
     * Marca o item como avisado. INSERT IGNORE porque a chave única
     * (usuário + tipo + solicitação + item) é a garantia real de não
     * repetir — se duas execuções se cruzarem, a segunda não insere.
     */
    public void registrarEnviado(int idUsuario, String tipo, String nrSolicitacao, String item) throws SQLException {
        String sql = "INSERT IGNORE INTO fc_alerta_oc_enviado (id_usuario, tipo, nr_solicitacao, item) VALUES (?,?,?,?)";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setString(2, tipo);
            ps.setString(3, nrSolicitacao);
            ps.setString(4, limitarItem(item));
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

    // ── Reenvio manual (Administração → Reenviar alerta) ──────────────────
    //
    // Apagar o registro de envio é o que faz o alerta sair de novo: a consulta
    // do ERP continua trazendo o item, e é só este controle que o segura.
    //
    // A tela trabalha em dois passos — procurar e depois liberar pelos ids
    // encontrados. Não é cerimônia: o número vem do Oracle e pode estar
    // gravado como "3482" ou "3482.0", então quem pede o reenvio precisa ver
    // o que casou antes de apagar, e o DELETE por id não tem como pegar mais
    // do que foi mostrado.

    /** Tipos que existem hoje na tabela, com quanto e quando. */
    public List<Map<String, Object>> tipos() throws SQLException {
        String sql = """
            SELECT tipo, COUNT(*) qt, MAX(data_envio) ultimo
            FROM fc_alerta_oc_enviado GROUP BY tipo ORDER BY tipo
            """;
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tipo", rs.getString(1));
                m.put("qtde", rs.getInt(2));
                m.put("ultimo", rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toString());
                lista.add(m);
            }
        }
        return lista;
    }

    /**
     * Envios já registrados para estes números.
     *
     * O casamento do número é feito em Java, e não no SQL, justamente por
     * causa do "3482" × "3482.0": comparar como número quando os dois lados
     * são números resolve isso de uma vez, sem REGEXP nem CAST que precisem
     * adivinhar o formato de tipos cujo "número" nem é numérico (o alerta de
     * parcela guarda o documento aqui).
     */
    public List<Map<String, Object>> procurar(String tipo, List<String> numeros) throws SQLException {
        String sql = """
            SELECT e.id, e.id_usuario, u.nome, u.logon, e.nr_solicitacao, e.item, e.data_envio
            FROM fc_alerta_oc_enviado e
            LEFT JOIN fc_usuario u ON u.id = e.id_usuario
            WHERE e.tipo = ?
            ORDER BY e.nr_solicitacao, u.nome, e.item
            """;
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nr = rs.getString("nr_solicitacao");
                    if (!casaAlgum(nr, numeros)) continue;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("idUsuario", rs.getInt("id_usuario"));
                    m.put("usuario", rs.getString("nome") != null ? rs.getString("nome") : rs.getString("logon"));
                    m.put("numero", nr);
                    m.put("item", rs.getString("item"));
                    m.put("dataEnvio", rs.getTimestamp("data_envio") == null ? null
                            : rs.getTimestamp("data_envio").toString());
                    lista.add(m);
                }
            }
        }
        return lista;
    }

    /** Apaga os registros escolhidos. Só por id: é o que a tela mostrou. */
    public int liberar(List<Integer> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) return 0;
        StringBuilder in = new StringBuilder();
        for (Integer id : ids) {
            if (id == null) continue;
            if (in.length() > 0) in.append(',');
            in.append(id.intValue());        // int: não há como virar texto de SQL
        }
        if (in.length() == 0) return 0;
        try (Connection c = conn(); Statement st = c.createStatement()) {
            int n = st.executeUpdate("DELETE FROM fc_alerta_oc_enviado WHERE id IN (" + in + ")");
            LOG.info("Reenvio liberado: " + n + " registro(s) de envio apagado(s) — ids " + in);
            return n;
        }
    }

    private static boolean casaAlgum(String gravado, List<String> numeros) {
        for (String n : numeros) if (mesmoNumero(gravado, n)) return true;
        return false;
    }

    /** "3482", "3482.0" e " 3482 " são o mesmo contrato. "ABC" só casa com "ABC". */
    public static boolean mesmoNumero(String gravado, String pedido) {
        String a = gravado == null ? "" : gravado.trim();
        String b = pedido  == null ? "" : pedido.trim();
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equalsIgnoreCase(b)) return true;
        try {
            return new java.math.BigDecimal(a).compareTo(new java.math.BigDecimal(b)) == 0;
        } catch (NumberFormatException e) {
            return false;   // algum dos lados não é número: só valia a igualdade de texto
        }
    }

    public static String chave(String tipo, String nrSolicitacao, String item) {
        return tipo + "|" + nrSolicitacao + "|" + limitarItem(item);
    }

    /** Mesmo corte da coluna, pra chave lida do banco e chave montada em memória baterem. */
    private static String limitarItem(String item) {
        if (item == null) return "";
        String v = item.trim();
        return v.length() > 150 ? v.substring(0, 150) : v;
    }
}
