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
 * Empresas de fora e as pessoas delas que entram pelo portal de manobra.
 *
 * É um espaço de identidade separado do fc_usuario de propósito. Gente de
 * dentro e gente de fora não são a mesma coisa com um campo a mais: quem
 * entra pelo bdo.usinasclotilde.com.br não pode alcançar folha, fluxo de
 * caixa nem nada além do módulo de manobra, e misturar as duas na mesma
 * tabela é como se chega, um dia, num acesso indevido por descuido de um
 * WHERE.
 *
 * A senha é individual, não por CNPJ. Senha compartilhada por empresa é mais
 * simples de operar e impossível de auditar — e aqui vai haver lançamento
 * operacional, então precisa dar para dizer quem fez.
 *
 * O que cada pessoa enxerga é decidido na Administração, em duas listas: os
 * equipamentos e os contratos liberados para ela.
 */
public class AcessoExternoDAO {

    private static final Logger LOG = Logger.getLogger(AcessoExternoDAO.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_empresa_externa (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  cnpj VARCHAR(14) NOT NULL UNIQUE,
                  razao_social VARCHAR(150) NOT NULL,
                  nome_curto VARCHAR(60),
                  ativo CHAR(1) NOT NULL DEFAULT 'S',
                  criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_usuario_externo (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  id_empresa INT NOT NULL,
                  logon VARCHAR(60) NOT NULL,
                  nome VARCHAR(150) NOT NULL,
                  cpf VARCHAR(11),
                  senha_hash VARCHAR(64) NOT NULL,
                  ativo CHAR(1) NOT NULL DEFAULT 'S',
                  ultimo_acesso TIMESTAMP NULL,
                  criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE KEY uk_empresa_logon (id_empresa, logon),
                  INDEX idx_empresa (id_empresa)
                )
                """);
            // Sem estas linhas a pessoa entra e não enxerga nada — a liberação
            // é explícita, e é o administrador quem a concede.
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_acesso_equipamento (
                  id_usuario_externo INT NOT NULL,
                  cod_equipamento VARCHAR(20) NOT NULL,
                  descricao VARCHAR(150),
                  PRIMARY KEY (id_usuario_externo, cod_equipamento)
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_acesso_contrato (
                  id_usuario_externo INT NOT NULL,
                  cod_contrato VARCHAR(30) NOT NULL,
                  descricao VARCHAR(150),
                  PRIMARY KEY (id_usuario_externo, cod_contrato)
                )
                """);
        }
        return c;
    }

    /** Cria as tabelas — chamado no start da aplicação. */
    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    // ── Autenticação ──────────────────────────────────────────────────────

    /** Só dígitos: o CNPJ chega da tela com ponto, barra e traço. */
    public static String soDigitos(String v) {
        return v == null ? "" : v.replaceAll("\\D", "");
    }

    /**
     * Confere CNPJ + usuário + senha.
     *
     * A empresa precisa estar ativa e a pessoa também: desligar a empresa
     * corta todo mundo dela de uma vez, que é o que se quer quando um
     * contrato encerra.
     *
     * @return dados da sessão, ou null quando não confere
     */
    public Map<String, Object> autenticar(String cnpj, String logon, String senha) throws SQLException {
        String sql = """
            SELECT u.id, u.nome, u.logon, e.id id_empresa, e.cnpj, e.razao_social, e.nome_curto
            FROM   fc_usuario_externo u
            JOIN   fc_empresa_externa e ON e.id = u.id_empresa
            WHERE  e.cnpj = ? AND u.logon = UPPER(?)
            AND    u.senha_hash = SHA2(?, 256)
            AND    u.ativo = 'S' AND e.ativo = 'S'
            """;
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, soDigitos(cnpj));
            ps.setString(2, logon == null ? "" : logon.trim());
            ps.setString(3, senha);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("nome", rs.getString("nome"));
                m.put("logon", rs.getString("logon"));
                m.put("idEmpresa", rs.getInt("id_empresa"));
                m.put("cnpj", rs.getString("cnpj"));
                m.put("razaoSocial", rs.getString("razao_social"));
                m.put("nomeCurto", rs.getString("nome_curto"));
                return m;
            }
        }
    }

    public void marcarAcesso(int idUsuarioExterno) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE fc_usuario_externo SET ultimo_acesso = CURRENT_TIMESTAMP WHERE id = ?")) {
            ps.setInt(1, idUsuarioExterno);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Registrar o acesso é secundário: falhar aqui não pode barrar
            // quem já provou quem é.
            LOG.warning("Não foi possível marcar o acesso de " + idUsuarioExterno + ": " + e.getMessage());
        }
    }

    // ── Liberações ────────────────────────────────────────────────────────

    public List<Map<String, Object>> equipamentosDe(int idUsuarioExterno) throws SQLException {
        return liberacoes("fc_acesso_equipamento", "cod_equipamento", idUsuarioExterno);
    }

    public List<Map<String, Object>> contratosDe(int idUsuarioExterno) throws SQLException {
        return liberacoes("fc_acesso_contrato", "cod_contrato", idUsuarioExterno);
    }

    private List<Map<String, Object>> liberacoes(String tabela, String coluna, int id) throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        // tabela e coluna são constantes do próprio código, nunca entrada externa
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT " + coluna + " cod, descricao FROM " + tabela
               + " WHERE id_usuario_externo = ? ORDER BY " + coluna)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("cod", rs.getString("cod"));
                    m.put("descricao", rs.getString("descricao"));
                    lista.add(m);
                }
            }
        }
        return lista;
    }

    /**
     * Troca a lista inteira de liberações da pessoa.
     *
     * Apagar e regravar dentro de uma transação, em vez de comparar item a
     * item: a tela manda o conjunto final, e no meio do caminho ninguém pode
     * enxergar um estado em que a pessoa perdeu tudo.
     */
    public void gravarLiberacoes(int idUsuarioExterno,
                                 List<String[]> equipamentos,
                                 List<String[]> contratos) throws SQLException {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                trocar(c, "fc_acesso_equipamento", "cod_equipamento", idUsuarioExterno, equipamentos);
                trocar(c, "fc_acesso_contrato", "cod_contrato", idUsuarioExterno, contratos);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private void trocar(Connection c, String tabela, String coluna, int id, List<String[]> itens)
            throws SQLException {
        try (PreparedStatement del = c.prepareStatement(
                 "DELETE FROM " + tabela + " WHERE id_usuario_externo = ?")) {
            del.setInt(1, id);
            del.executeUpdate();
        }
        if (itens == null || itens.isEmpty()) return;
        try (PreparedStatement ins = c.prepareStatement(
                 "INSERT INTO " + tabela + " (id_usuario_externo, " + coluna + ", descricao) VALUES (?,?,?)")) {
            for (String[] it : itens) {
                if (it == null || it.length == 0 || it[0] == null || it[0].isBlank()) continue;
                ins.setInt(1, id);
                ins.setString(2, it[0].trim());
                ins.setString(3, it.length > 1 ? it[1] : null);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    // ── Cadastro (Administração) ──────────────────────────────────────────

    public List<Map<String, Object>> empresas() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        String sql = """
            SELECT e.id, e.cnpj, e.razao_social, e.nome_curto, e.ativo,
                   (SELECT COUNT(*) FROM fc_usuario_externo u WHERE u.id_empresa = e.id) usuarios
            FROM   fc_empresa_externa e
            ORDER BY e.razao_social
            """;
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("cnpj", rs.getString("cnpj"));
                m.put("razaoSocial", rs.getString("razao_social"));
                m.put("nomeCurto", rs.getString("nome_curto"));
                m.put("ativo", rs.getString("ativo"));
                m.put("usuarios", rs.getInt("usuarios"));
                lista.add(m);
            }
        }
        return lista;
    }

    public int salvarEmpresa(Integer id, String cnpj, String razaoSocial, String nomeCurto, String ativo)
            throws SQLException {
        String digitos = soDigitos(cnpj);
        if (digitos.length() != 14) throw new SQLException("CNPJ deve ter 14 dígitos");
        try (Connection c = conn()) {
            if (id == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO fc_empresa_externa (cnpj, razao_social, nome_curto, ativo) VALUES (?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, digitos);
                    ps.setString(2, razaoSocial);
                    ps.setString(3, nomeCurto);
                    ps.setString(4, ativo);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        return rs.next() ? rs.getInt(1) : 0;
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE fc_empresa_externa SET cnpj=?, razao_social=?, nome_curto=?, ativo=? WHERE id=?")) {
                ps.setString(1, digitos);
                ps.setString(2, razaoSocial);
                ps.setString(3, nomeCurto);
                ps.setString(4, ativo);
                ps.setInt(5, id);
                ps.executeUpdate();
            }
            return id;
        }
    }

    public List<Map<String, Object>> usuarios(Integer idEmpresa) throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        String sql = """
            SELECT u.id, u.id_empresa, u.logon, u.nome, u.cpf, u.ativo, u.ultimo_acesso,
                   e.razao_social, e.cnpj,
                   (SELECT COUNT(*) FROM fc_acesso_equipamento a WHERE a.id_usuario_externo = u.id) equipamentos,
                   (SELECT COUNT(*) FROM fc_acesso_contrato    a WHERE a.id_usuario_externo = u.id) contratos
            FROM   fc_usuario_externo u
            JOIN   fc_empresa_externa e ON e.id = u.id_empresa
            WHERE  (? IS NULL OR u.id_empresa = ?)
            ORDER BY e.razao_social, u.nome
            """;
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (idEmpresa == null) { ps.setNull(1, java.sql.Types.INTEGER); ps.setNull(2, java.sql.Types.INTEGER); }
            else { ps.setInt(1, idEmpresa); ps.setInt(2, idEmpresa); }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("idEmpresa", rs.getInt("id_empresa"));
                    m.put("logon", rs.getString("logon"));
                    m.put("nome", rs.getString("nome"));
                    m.put("cpf", rs.getString("cpf"));
                    m.put("ativo", rs.getString("ativo"));
                    m.put("ultimoAcesso", rs.getTimestamp("ultimo_acesso") == null ? null
                            : rs.getTimestamp("ultimo_acesso").toInstant().toString());
                    m.put("razaoSocial", rs.getString("razao_social"));
                    m.put("cnpj", rs.getString("cnpj"));
                    m.put("equipamentos", rs.getInt("equipamentos"));
                    m.put("contratos", rs.getInt("contratos"));
                    lista.add(m);
                }
            }
        }
        return lista;
    }

    /** @param senha nula ou vazia mantém a senha atual (edição sem trocar senha) */
    public int salvarUsuario(Integer id, int idEmpresa, String logon, String nome,
                             String cpf, String senha, String ativo) throws SQLException {
        String log = logon == null ? "" : logon.trim().toUpperCase();
        if (log.isBlank()) throw new SQLException("Usuário é obrigatório");
        try (Connection c = conn()) {
            if (id == null) {
                if (senha == null || senha.isBlank()) throw new SQLException("Senha é obrigatória no cadastro");
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO fc_usuario_externo (id_empresa, logon, nome, cpf, senha_hash, ativo) "
                      + "VALUES (?,?,?,?,SHA2(?,256),?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, idEmpresa);
                    ps.setString(2, log);
                    ps.setString(3, nome);
                    ps.setString(4, soDigitos(cpf));
                    ps.setString(5, senha);
                    ps.setString(6, ativo);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        return rs.next() ? rs.getInt(1) : 0;
                    }
                }
            }
            boolean trocaSenha = senha != null && !senha.isBlank();
            String sql = trocaSenha
                ? "UPDATE fc_usuario_externo SET id_empresa=?, logon=?, nome=?, cpf=?, ativo=?, senha_hash=SHA2(?,256) WHERE id=?"
                : "UPDATE fc_usuario_externo SET id_empresa=?, logon=?, nome=?, cpf=?, ativo=? WHERE id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, idEmpresa);
                ps.setString(2, log);
                ps.setString(3, nome);
                ps.setString(4, soDigitos(cpf));
                ps.setString(5, ativo);
                if (trocaSenha) { ps.setString(6, senha); ps.setInt(7, id); }
                else ps.setInt(6, id);
                ps.executeUpdate();
            }
            return id;
        }
    }

    /** Apaga a pessoa e, junto, tudo que ela tinha liberado. */
    public boolean excluirUsuario(int id) throws SQLException {
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                for (String t : new String[]{ "fc_acesso_equipamento", "fc_acesso_contrato" }) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "DELETE FROM " + t + " WHERE id_usuario_externo = ?")) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                }
                int n;
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM fc_usuario_externo WHERE id = ?")) {
                    ps.setInt(1, id);
                    n = ps.executeUpdate();
                }
                c.commit();
                return n > 0;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }
}
