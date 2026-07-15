package br.com.lopes.fluxo.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controle de acesso às categorias de consulta do chat (assistente IA).
 *
 * As permissões vivem na tabela genérica fc_permissao (MySQL), com as chaves
 * {@link #AGRICOLA} e {@link #FINANCEIRO} — gerenciadas na tela de
 * administração, aba Permissões, como os demais relatórios. Administradores
 * têm todas as categorias automaticamente.
 *
 * Fluxo: a cada pergunta, o AgroChatServlet carrega as categorias do usuário
 * logado e as registra aqui, associadas ao sessionId do chat. Quando o n8n
 * chama uma ferramenta (ex.: /api/agricola/talhoes) passando o mesmo
 * sessionId, o servlet da ferramenta valida com {@link #verificarAcesso} —
 * o bloqueio é do servidor, não depende do modelo de IA obedecer.
 */
public final class ChatPermissaoUtil {

    /** Nível 1: pode abrir e usar o assistente (sem isso, o chat nem responde). */
    public static final String ACESSO     = "chat_acesso";
    /** Nível 2: o que o usuário pode consultar dentro do chat. */
    public static final String AGRICOLA   = "chat_agricola";
    public static final String FINANCEIRO = "chat_financeiro";

    private static final Set<String> TODAS = Set.of(ACESSO, AGRICOLA, FINANCEIRO);

    private static final Logger LOG = Logger.getLogger(ChatPermissaoUtil.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    /** Mesma duração máxima da sessão de login (8h). */
    private static final long VALIDADE_MS = 8 * 60 * 60 * 1000L;

    private static final class Entrada {
        final Set<String> categorias;
        final long timestamp;
        Entrada(Set<String> categorias) {
            this.categorias = categorias;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static final ConcurrentHashMap<String, Entrada> POR_SESSAO = new ConcurrentHashMap<>();

    private ChatPermissaoUtil() {}

    /** Categorias de chat do usuário; administrador tem todas. */
    public static Set<String> carregarCategorias(long idUsuario, boolean administrador) {
        if (administrador) return TODAS;

        String sql = "SELECT relatorio FROM fc_permissao WHERE id_usuario=? AND ativo='S' AND relatorio IN (?,?,?)";
        Set<String> categorias = new HashSet<>();
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, idUsuario);
            ps.setString(2, ACESSO);
            ps.setString(3, AGRICOLA);
            ps.setString(4, FINANCEIRO);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) categorias.add(rs.getString("relatorio"));
            }
        } catch (SQLException e) {
            // Falha no banco de permissões = nega tudo (fail-safe)
            LOG.log(Level.SEVERE, "Erro ao carregar permissões do chat", e);
        }
        return categorias;
    }

    /** Associa o sessionId do chat às categorias do usuário logado. */
    public static void registrar(String sessionId, Set<String> categorias) {
        if (sessionId == null || sessionId.isBlank()) return;
        POR_SESSAO.put(sessionId, new Entrada(categorias));
        long agora = System.currentTimeMillis();
        POR_SESSAO.entrySet().removeIf(en -> agora - en.getValue().timestamp > VALIDADE_MS);
    }

    /**
     * Valida o acesso de uma sessão de chat a uma categoria.
     *
     * @param descricaoCategoria texto para a mensagem, ex.: "consultas agrícolas"
     * @return null se autorizado; senão a mensagem de erro a devolver ao agente
     */
    public static String verificarAcesso(String sessionId, String categoria, String descricaoCategoria) {
        if (sessionId == null || sessionId.isBlank()) {
            return "Chamada sem sessionId — verifique a configuração da ferramenta no workflow do n8n.";
        }
        Entrada e = POR_SESSAO.get(sessionId);
        if (e == null || System.currentTimeMillis() - e.timestamp > VALIDADE_MS) {
            return "Sessão do chat não reconhecida ou expirada — peça ao usuário para recarregar a página do assistente e perguntar novamente.";
        }
        if (!e.categorias.contains(categoria)) {
            return "O usuário não tem permissão para " + descricaoCategoria
                 + ". Informe-o educadamente e oriente-o a solicitar acesso ao administrador da intranet.";
        }
        return null;
    }
}
