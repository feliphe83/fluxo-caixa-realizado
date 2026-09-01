package br.com.lopes.fluxo.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controle de acesso por ÁREA de negócio no painel "Acompanhamento do
 * Orçamento de Compras — Safra" (orcamento-safra.html / OrcamentoSafraServlet).
 *
 * Reaproveita a mesma tabela genérica de permissões (fc_permissao) já usada
 * pela aba Permissões em Administração para os "relatórios" (RELS, em
 * admin.html) — cada área liberada é só mais uma linha ativa lá, gerenciada
 * do mesmo jeito. {@link ChatPermissaoUtil} segue exatamente este padrão
 * para o chat; aqui é o equivalente para área de negócio.
 *
 * Sem NENHUMA das chaves abaixo ativa para o usuário, ele vê TUDO sem
 * restrição — é o comportamento de sempre, para não bloquear ninguém no dia
 * em que isso for pro ar sem que um administrador tenha configurado nada
 * ainda. Administrador sempre vê tudo, também sem restrição.
 */
public final class AreaOrcamentoPermissaoUtil {

    private static final Logger LOG = Logger.getLogger(AreaOrcamentoPermissaoUtil.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    /** Marcada, libera tudo — equivalente a não ter restrição nenhuma. */
    public static final String EMPRESA = "orcamento_safra_empresa";
    private static final String AGRICOLA = "orcamento_safra_agricola";
    private static final String INDUSTRIAL = "orcamento_safra_industrial";
    private static final String ADMINISTRATIVA = "orcamento_safra_administrativa";
    private static final String COMERCIAL = "orcamento_safra_comercial";

    /** chave em fc_permissao -> negócio real, como o Oracle devolve (RH/posto.fn_busca_arvore_objetocusto). */
    private static final Map<String, String> AREA_POR_CHAVE = Map.of(
            AGRICOLA, "ÁREA AGRÍCOLA",
            INDUSTRIAL, "ÁREA INDUSTRIAL",
            ADMINISTRATIVA, "ÁREA ADMINISTRATIVA",
            COMERCIAL, "LOGÍSTICA E COMERCIALIZAÇÃO");

    private AreaOrcamentoPermissaoUtil() {}

    /**
     * As áreas (negócio) que este usuário pode ver.
     *
     * @return null = sem restrição, vê tudo (administrador; usuário sem
     *         nenhuma área configurada; ou EMPRESA marcada — libera geral).
     *         Não-nulo = só essas áreas.
     */
    public static Set<String> areasPermitidas(long idUsuario, boolean administrador) {
        if (administrador) return null;

        String sql = "SELECT relatorio FROM fc_permissao WHERE id_usuario=? AND ativo='S' "
                   + "AND relatorio IN (?,?,?,?,?)";
        Set<String> chaves = new HashSet<>();
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, idUsuario);
            ps.setString(2, EMPRESA);
            ps.setString(3, AGRICOLA);
            ps.setString(4, INDUSTRIAL);
            ps.setString(5, ADMINISTRATIVA);
            ps.setString(6, COMERCIAL);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) chaves.add(rs.getString("relatorio"));
            }
        } catch (SQLException e) {
            // Falha ao consultar permissão não pode travar o painel pra todo
            // mundo — segue a mesma filosofia de "sem configuração, vê tudo".
            LOG.log(Level.WARNING, "Erro ao carregar permissão de área do orçamento (seguindo sem restrição)", e);
            return null;
        }

        if (chaves.isEmpty() || chaves.contains(EMPRESA)) return null;

        Set<String> areas = new HashSet<>();
        for (String chave : chaves) {
            String area = AREA_POR_CHAVE.get(chave);
            if (area != null) areas.add(area);
        }
        return areas;
    }
}
