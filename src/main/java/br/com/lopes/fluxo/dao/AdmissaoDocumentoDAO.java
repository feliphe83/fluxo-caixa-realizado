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

/**
 * Documentos de admissão enviados pelo candidato/novo funcionário, pelo link
 * público (sem login) do site da usina.
 *
 * Três tabelas:
 *  - fc_admissao_tipo_documento: os "campos" configuráveis em Administração
 *    (RG, CTPS, comprovante de residência etc.) — dinâmico de propósito,
 *    porque a lista de documentos exigidos muda com o tempo e não pode
 *    depender de alterar código.
 *  - fc_admissao_candidato: uma linha por CPF que já começou o processo —
 *    identidade separada de fc_usuario, do mesmo jeito que
 *    {@link AcessoExternoDAO} mantém fc_usuario_externo separado: quem entra
 *    por este link não é um usuário da intranet, só alguém com um CPF válido.
 *  - fc_admissao_documento: o arquivo enviado para cada (candidato, tipo de
 *    documento) — só o caminho no disco, o arquivo em si mora fora do banco
 *    (ver ArmazenamentoAdmissaoUtil).
 */
public class AdmissaoDocumentoDAO {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private Connection conn() throws SQLException {
        Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_admissao_tipo_documento (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  nome VARCHAR(120) NOT NULL,
                  obrigatorio CHAR(1) NOT NULL DEFAULT 'S',
                  ordem INT NOT NULL DEFAULT 0,
                  ativo CHAR(1) NOT NULL DEFAULT 'S'
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_admissao_candidato (
                  id INT AUTO_INCREMENT PRIMARY KEY,
                  cpf VARCHAR(11) NOT NULL UNIQUE,
                  nome VARCHAR(150),
                  cargo_pretendido VARCHAR(150),
                  ligado_ao_erp CHAR(1) NOT NULL DEFAULT 'N',
                  criado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS fc_admissao_documento (
                  id_candidato INT NOT NULL,
                  id_tipo_documento INT NOT NULL,
                  nome_arquivo_original VARCHAR(255),
                  caminho_arquivo VARCHAR(255) NOT NULL,
                  tamanho_bytes BIGINT,
                  enviado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id_candidato, id_tipo_documento)
                )
                """);
            // Seed inicial: documentos comuns de admissão — a administração
            // ajusta depois (nome, obrigatoriedade, ordem, ativo/inativo).
            st.execute("""
                INSERT IGNORE INTO fc_admissao_tipo_documento (id, nome, obrigatorio, ordem) VALUES
                  (1, 'RG (frente e verso)', 'S', 1),
                  (2, 'CPF', 'S', 2),
                  (3, 'Carteira de Trabalho (CTPS)', 'S', 3),
                  (4, 'Comprovante de Residência', 'S', 4),
                  (5, 'Título de Eleitor', 'N', 5),
                  (6, 'Certificado de Reservista', 'N', 6),
                  (7, 'Certidão de Nascimento/Casamento', 'N', 7),
                  (8, 'PIS/PASEP', 'N', 8),
                  (9, 'CNH (Carteira Nacional de Habilitação)', 'N', 9)
                """);
        }
        return c;
    }

    public void garantirEstrutura() throws SQLException {
        conn().close();
    }

    // ── Tipos de documento (Administração) ────────────────────────────────

    public List<Map<String, Object>> tiposDocumento(boolean somenteAtivos) throws SQLException {
        String sql = "SELECT id, nome, obrigatorio, ordem, ativo FROM fc_admissao_tipo_documento "
                + (somenteAtivos ? "WHERE ativo = 'S' " : "") + "ORDER BY ordem, nome";
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("nome", rs.getString("nome"));
                m.put("obrigatorio", "S".equals(rs.getString("obrigatorio")));
                m.put("ordem", rs.getInt("ordem"));
                m.put("ativo", "S".equals(rs.getString("ativo")));
                lista.add(m);
            }
        }
        return lista;
    }

    public int salvarTipoDocumento(Integer id, String nome, boolean obrigatorio, int ordem, boolean ativo) throws SQLException {
        try (Connection c = conn()) {
            if (id == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO fc_admissao_tipo_documento (nome, obrigatorio, ordem, ativo) VALUES (?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, nome);
                    ps.setString(2, obrigatorio ? "S" : "N");
                    ps.setInt(3, ordem);
                    ps.setString(4, ativo ? "S" : "N");
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getInt(1) : 0; }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE fc_admissao_tipo_documento SET nome=?, obrigatorio=?, ordem=?, ativo=? WHERE id=?")) {
                ps.setString(1, nome);
                ps.setString(2, obrigatorio ? "S" : "N");
                ps.setInt(3, ordem);
                ps.setString(4, ativo ? "S" : "N");
                ps.setInt(5, id);
                ps.executeUpdate();
            }
            return id;
        }
    }

    /**
     * Some com o tipo. Se algum candidato já enviou esse documento, não
     * apaga — desativa (a tela de administração já mostra isso e sugere
     * desativar em vez de excluir): perder o rastro de um documento que uma
     * pessoa já mandou é pior do que a lista de tipos ficar com um item a mais.
     */
    public boolean excluirTipoDocumento(int id) throws SQLException {
        try (Connection c = conn()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM fc_admissao_documento WHERE id_tipo_documento = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) return false;
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM fc_admissao_tipo_documento WHERE id = ?")) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        }
    }

    // ── Candidato (tela pública) ───────────────────────────────────────────

    /** Acha ou cria o candidato deste CPF. Nunca falha por já existir — é o ponto de entrada de cada visita. */
    public Map<String, Object> buscarOuCriarCandidato(String cpf) throws SQLException {
        try (Connection c = conn()) {
            Map<String, Object> existente = buscarCandidato(c, cpf);
            if (existente != null) return existente;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO fc_admissao_candidato (cpf) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, cpf);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); }
            }
            return buscarCandidato(c, cpf);
        }
    }

    private Map<String, Object> buscarCandidato(Connection c, String cpf) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, cpf, nome, cargo_pretendido, ligado_ao_erp FROM fc_admissao_candidato WHERE cpf = ?")) {
            ps.setString(1, cpf);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("cpf", rs.getString("cpf"));
                m.put("nome", rs.getString("nome"));
                m.put("cargoPretendido", rs.getString("cargo_pretendido"));
                m.put("ligadoAoErp", "S".equals(rs.getString("ligado_ao_erp")));
                return m;
            }
        }
    }

    /**
     * Preenche nome/cargo achados no ERP — só quando o candidato AINDA não
     * tem nome próprio digitado, pra não sobrescrever o que a pessoa já
     * corrigiu na mão numa visita anterior.
     */
    public void ligarAoErpSeVazio(int idCandidato, String nome, String cargoPretendido) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE fc_admissao_candidato SET nome = ?, cargo_pretendido = ?, ligado_ao_erp = 'S' "
               + "WHERE id = ? AND (nome IS NULL OR nome = '')")) {
            ps.setString(1, nome);
            ps.setString(2, cargoPretendido);
            ps.setInt(3, idCandidato);
            ps.executeUpdate();
        }
    }

    /** A pessoa preenchendo o nome na mão (quando não achou ligação com o ERP). */
    public void atualizarNome(int idCandidato, String nome) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("UPDATE fc_admissao_candidato SET nome = ? WHERE id = ?")) {
            ps.setString(1, nome);
            ps.setInt(2, idCandidato);
            ps.executeUpdate();
        }
    }

    // ── Documentos ──────────────────────────────────────────────────────────

    /** Documentos já enviados por este candidato, por id do tipo. */
    public Map<Integer, Map<String, Object>> documentosDoCandidato(int idCandidato) throws SQLException {
        Map<Integer, Map<String, Object>> mapa = new LinkedHashMap<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id_tipo_documento, nome_arquivo_original, caminho_arquivo, tamanho_bytes, enviado_em "
               + "FROM fc_admissao_documento WHERE id_candidato = ?")) {
            ps.setInt(1, idCandidato);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("nomeArquivoOriginal", rs.getString("nome_arquivo_original"));
                    m.put("caminhoArquivo", rs.getString("caminho_arquivo"));
                    m.put("tamanhoBytes", rs.getLong("tamanho_bytes"));
                    m.put("enviadoEm", rs.getTimestamp("enviado_em").toString());
                    mapa.put(rs.getInt("id_tipo_documento"), m);
                }
            }
        }
        return mapa;
    }

    /** @return o caminho do arquivo anterior (para apagar do disco), ou null se era a 1ª vez. */
    public String salvarDocumento(int idCandidato, int idTipoDocumento, String nomeArquivoOriginal,
                                   String caminhoArquivo, long tamanhoBytes) throws SQLException {
        try (Connection c = conn()) {
            String anterior = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT caminho_arquivo FROM fc_admissao_documento WHERE id_candidato = ? AND id_tipo_documento = ?")) {
                ps.setInt(1, idCandidato);
                ps.setInt(2, idTipoDocumento);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) anterior = rs.getString(1); }
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO fc_admissao_documento (id_candidato, id_tipo_documento, nome_arquivo_original, caminho_arquivo, tamanho_bytes)
                    VALUES (?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE nome_arquivo_original = VALUES(nome_arquivo_original),
                        caminho_arquivo = VALUES(caminho_arquivo), tamanho_bytes = VALUES(tamanho_bytes)
                    """)) {
                ps.setInt(1, idCandidato);
                ps.setInt(2, idTipoDocumento);
                ps.setString(3, nomeArquivoOriginal);
                ps.setString(4, caminhoArquivo);
                ps.setLong(5, tamanhoBytes);
                ps.executeUpdate();
            }
            return anterior;
        }
    }

    public String caminhoDocumento(int idCandidato, int idTipoDocumento) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT caminho_arquivo FROM fc_admissao_documento WHERE id_candidato = ? AND id_tipo_documento = ?")) {
            ps.setInt(1, idCandidato);
            ps.setInt(2, idTipoDocumento);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    // ── Tela de controle (RH) ──────────────────────────────────────────────

    /** Um candidato por linha, com quantos documentos obrigatórios já mandou. */
    public List<Map<String, Object>> listarCandidatos() throws SQLException {
        String sql = """
            SELECT cand.id, cand.cpf, cand.nome, cand.cargo_pretendido, cand.ligado_ao_erp,
                   cand.criado_em, cand.atualizado_em,
                   (SELECT COUNT(*) FROM fc_admissao_tipo_documento t WHERE t.ativo = 'S' AND t.obrigatorio = 'S') total_obrigatorios,
                   (SELECT COUNT(*) FROM fc_admissao_documento d
                      JOIN fc_admissao_tipo_documento t ON t.id = d.id_tipo_documento
                     WHERE d.id_candidato = cand.id AND t.ativo = 'S' AND t.obrigatorio = 'S') enviados_obrigatorios,
                   (SELECT COUNT(*) FROM fc_admissao_documento d WHERE d.id_candidato = cand.id) total_enviados
            FROM fc_admissao_candidato cand
            ORDER BY cand.atualizado_em DESC
            """;
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("cpf", rs.getString("cpf"));
                m.put("nome", rs.getString("nome"));
                m.put("cargoPretendido", rs.getString("cargo_pretendido"));
                m.put("ligadoAoErp", "S".equals(rs.getString("ligado_ao_erp")));
                m.put("criadoEm", rs.getTimestamp("criado_em").toString());
                m.put("atualizadoEm", rs.getTimestamp("atualizado_em").toString());
                m.put("totalObrigatorios", rs.getInt("total_obrigatorios"));
                m.put("enviadosObrigatorios", rs.getInt("enviados_obrigatorios"));
                m.put("totalEnviados", rs.getInt("total_enviados"));
                lista.add(m);
            }
        }
        return lista;
    }

    public Map<String, Object> buscarCandidatoPorId(int id) throws SQLException {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT id, cpf, nome, cargo_pretendido, ligado_ao_erp FROM fc_admissao_candidato WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("cpf", rs.getString("cpf"));
                m.put("nome", rs.getString("nome"));
                m.put("cargoPretendido", rs.getString("cargo_pretendido"));
                m.put("ligadoAoErp", "S".equals(rs.getString("ligado_ao_erp")));
                return m;
            }
        }
    }
}
