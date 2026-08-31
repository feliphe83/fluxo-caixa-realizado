package br.com.lopes.fluxo.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Configuração (MySQL, linha única) do alerta de NF sem entrada — a caixa de
 * e-mail de Compras a ler, o prazo em dias e a janela de varredura.
 *
 * Fica numa tabela própria, separada de {@link ParametroDAO}, por causa da
 * senha do IMAP: {@code GET /api/parametros} responde para qualquer usuário
 * logado (as telas normais precisam ler a safra padrão), então guardar a
 * senha ali a exporia para todo mundo. Aqui o servlet
 * ({@code NfEmailConfigServlet}) exige administrador tanto para ler quanto
 * para gravar, e a leitura para a TELA nunca devolve a senha em texto puro —
 * só se ela está configurada ou não ({@link Config#senhaConfigurada}).
 */
public class NfEmailConfigDAO {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private static final String HOST_PADRAO = "email-ssl.com.br";
    private static final String PASTA_PADRAO = "INBOX";
    private static final int PRAZO_DIAS_PADRAO = 5;
    private static final int DIAS_VARREDURA_PADRAO = 20;

    /** %s/%d preenchidos por .formatted() — evita a cilada de misturar aspas simples/duplas concatenando texto na mão. */
    private static final String CREATE_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS fc_config_nf_email (
          id INT PRIMARY KEY,
          imap_host VARCHAR(120) NOT NULL DEFAULT '%s',
          imap_pasta VARCHAR(60) NOT NULL DEFAULT '%s',
          imap_usuario VARCHAR(180) NOT NULL DEFAULT '',
          imap_senha VARCHAR(255) NOT NULL DEFAULT '',
          prazo_dias INT NOT NULL DEFAULT %d,
          dias_varredura INT NOT NULL DEFAULT %d,
          atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          atualizado_por VARCHAR(120)
        )
        """;

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute(CREATE_TABLE_SQL.formatted(HOST_PADRAO, PASTA_PADRAO, PRAZO_DIAS_PADRAO, DIAS_VARREDURA_PADRAO));
            st.execute("INSERT IGNORE INTO fc_config_nf_email (id) VALUES (1)");
        }
        return c;
    }

    /** Cria a tabela e semeia a linha única — chamado no start da aplicação. */
    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    /** O que a tela pode mostrar — nunca a senha em texto puro. */
    public static final class Config {
        public String host, pasta, usuario, atualizadoEm, atualizadoPor;
        public boolean senhaConfigurada;
        public int prazoDias, diasVarredura;
    }

    /** Config para a tela de administração — {@link Config#senhaConfigurada} no lugar da senha. */
    public Config obter() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT imap_host, imap_pasta, imap_usuario, imap_senha, prazo_dias, dias_varredura, "
               + "atualizado_em, atualizado_por FROM fc_config_nf_email WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            Config cfg = new Config();
            if (rs.next()) {
                cfg.host = rs.getString("imap_host");
                cfg.pasta = rs.getString("imap_pasta");
                cfg.usuario = rs.getString("imap_usuario");
                cfg.senhaConfigurada = rs.getString("imap_senha") != null && !rs.getString("imap_senha").isBlank();
                cfg.prazoDias = rs.getInt("prazo_dias");
                cfg.diasVarredura = rs.getInt("dias_varredura");
                cfg.atualizadoEm = rs.getTimestamp("atualizado_em") == null ? null : rs.getTimestamp("atualizado_em").toString();
                cfg.atualizadoPor = rs.getString("atualizado_por");
            }
            return cfg;
        }
    }

    /**
     * Config completa, COM a senha em texto puro — só para uso interno de
     * {@code ImapComprasUtil} ao conectar. Nunca sai daqui em direção a uma
     * resposta HTTP.
     */
    public static final class ConfigComSenha {
        public String host, pasta, usuario, senha;
        public int prazoDias, diasVarredura;
    }

    public ConfigComSenha obterComSenha() throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT imap_host, imap_pasta, imap_usuario, imap_senha, prazo_dias, dias_varredura "
               + "FROM fc_config_nf_email WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            ConfigComSenha cfg = new ConfigComSenha();
            if (rs.next()) {
                cfg.host = rs.getString("imap_host");
                cfg.pasta = rs.getString("imap_pasta");
                cfg.usuario = rs.getString("imap_usuario");
                cfg.senha = rs.getString("imap_senha");
                cfg.prazoDias = rs.getInt("prazo_dias");
                cfg.diasVarredura = rs.getInt("dias_varredura");
            }
            return cfg;
        }
    }

    /**
     * Grava a configuração. {@code senha} nula ou em branco MANTÉM a senha
     * já gravada — é assim que a tela evita reenviar (e reexibir) a senha
     * atual só para não trocar nada.
     */
    public void salvar(String host, String pasta, String usuario, String senha,
                        int prazoDias, int diasVarredura, String quem) throws SQLException {
        String sql = senha == null || senha.isBlank()
            ? "UPDATE fc_config_nf_email SET imap_host=?, imap_pasta=?, imap_usuario=?, "
            + "prazo_dias=?, dias_varredura=?, atualizado_por=? WHERE id = 1"
            : "UPDATE fc_config_nf_email SET imap_host=?, imap_pasta=?, imap_usuario=?, imap_senha=?, "
            + "prazo_dias=?, dias_varredura=?, atualizado_por=? WHERE id = 1";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, host);
            ps.setString(i++, pasta);
            ps.setString(i++, usuario);
            if (senha != null && !senha.isBlank()) ps.setString(i++, senha);
            ps.setInt(i++, prazoDias);
            ps.setInt(i++, diasVarredura);
            ps.setString(i++, quem);
            ps.executeUpdate();
        }
    }
}
