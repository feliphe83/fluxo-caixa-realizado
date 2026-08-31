package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orçamento de compras: planejado x realizado, por grupo de empenho e empenho.
 *
 * A consulta é a do ERP, entregue pronta, em
 * src/main/resources/sql/orcamento-compras.sql. Fora do arquivo ela só ganha
 * o período e o negócio.
 *
 * ── O NEGÓCIO NÃO ESTÁ NA CONSULTA ORIGINAL ──
 *
 * Ela agrega tudo num total só, então nunca precisou dele. Para filtrar por
 * negócio é preciso o objeto de custo do lançamento, e daí a árvore
 * (posto.fn_busca_arvore_objetocusto(cod, 'D', 'NG')), que é como o resto do
 * sistema resolve negócio.
 *
 * O nome da coluna de objeto de custo nessas duas tabelas não está
 * documentado em lugar nenhum deste projeto, e daqui não há como executar
 * nada no Oracle para conferir. Em vez de escolher um nome e torcer — o que
 * já custou um ORA-01858 no painel de diesel —, o DAO PERGUNTA ao dicionário
 * quais colunas existem e monta a expressão conforme a resposta. Se nenhuma
 * candidata existir, o painel funciona sem o filtro de negócio e diz isso na
 * tela, em vez de não abrir.
 */
public class OrcamentoComprasDAO {

    private static final Logger LOG = Logger.getLogger(OrcamentoComprasDAO.class.getName());

    private static final String RECURSO_SQL = "/sql/orcamento-compras.sql";

    /** As duas tabelas de lançamento que a consulta usa, com o apelido "lc". */
    private static final String[] TABELAS = { "CUSTO.LANCAMENTO_CUSTO", "MATERIAL.REALIZADO" };

    /** Nomes possíveis do objeto de custo, na ordem de preferência. */
    private static final String[] COLUNAS_OBJETO = {
        "COD_OBJETOCUSTO_DETALHE", "COD_OBJETOCUSTO", "COD_OBJETO_CUSTO", "COD_OBJETO"
    };

    private static volatile String sqlBase;
    /** A coluna encontrada, "" se não houver nenhuma. Null = ainda não perguntei. */
    private static volatile String colunaObjeto;

    /** Uma linha por negócio × grupo de empenho × empenho. */
    public List<Map<String, Object>> buscar(int anomesIni, int anomesFim, String negocio) {
        try (Connection conn = OracleConnectionUtil.getConnection()) {
            String coluna = descobrirColunaObjeto(conn);
            boolean temNegocio = coluna != null && !coluna.isEmpty();

            String sql = base()
                    .replace("%ANOMES_INI%", String.valueOf(anomesIni))
                    .replace("%ANOMES_FIM%", String.valueOf(anomesFim))
                    .replace("%NEG_COD%",  temNegocio
                            ? "posto.fn_busca_arvore_objetocusto(lc." + coluna + ",'C','NG')"
                            : "null")
                    .replace("%NEG_DESC%", temNegocio
                            ? "nvl(posto.fn_busca_arvore_objetocusto(lc." + coluna + ",'D','NG'),'Sem negócio')"
                            : "'Não disponível'")
                    // O objeto de custo sai da MESMA coluna que o negócio: o
                    // negócio é um nível da árvore dele. Onde não há coluna,
                    // não há nem um nem outro.
                    .replace("%OBJ_COD%",  temNegocio ? "lc." + coluna : "null")
                    .replace("%OBJ_DESC%", temNegocio
                            ? "nvl(custo.fn_busca_descricao_oc(lc." + coluna + "),'Sem objeto de custo')"
                            : "'Não disponível'")
                    .replace("%FILTRO_NEGOCIO%",
                            (temNegocio && negocio != null && !negocio.isBlank())
                                    ? "and tmp.negocio = ?" : "");

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (temNegocio && negocio != null && !negocio.isBlank()) ps.setString(1, negocio.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> linhas = RowMapperUtil.toList(rs);
                    LOG.info("Orçamento de compras " + anomesIni + "-" + anomesFim + ": "
                            + linhas.size() + " empenhos"
                            + (temNegocio ? " (negócio por lc." + coluna + ")" : " (sem negócio)"));
                    return linhas;
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar o orçamento de compras", e);
            throw new RuntimeException(mensagem(e), e);
        }
    }

    /** @return "" quando o filtro de negócio não é possível nesta base. */
    public String colunaDeNegocio() {
        String c = colunaObjeto;
        return c == null ? "" : c;
    }

    /**
     * A coluna de objeto de custo que existe nas DUAS tabelas.
     *
     * Tem de existir nas duas: a consulta usa o mesmo apelido "lc" para
     * custo.lancamento_custo e material.realizado, e uma expressão que só
     * funciona numa das pernas derruba a consulta inteira.
     */
    private static String descobrirColunaObjeto(Connection conn) {
        String cache = colunaObjeto;
        if (cache != null) return cache;

        String sql = "select column_name from all_tab_columns "
                   + "where owner = ? and table_name = ? and column_name = ?";
        for (String candidata : COLUNAS_OBJETO) {
            boolean nasDuas = true;
            for (String tabela : TABELAS) {
                String[] p = tabela.split("\\.");
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, p[0]); ps.setString(2, p[1]); ps.setString(3, candidata);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { nasDuas = false; break; }
                    }
                } catch (SQLException e) {
                    LOG.log(Level.WARNING, "Não foi possível consultar o dicionário", e);
                    colunaObjeto = "";
                    return "";
                }
            }
            if (nasDuas) {
                LOG.info("Negócio virá de lc." + candidata);
                colunaObjeto = candidata;
                return candidata;
            }
        }
        LOG.warning("Nenhuma coluna de objeto de custo encontrada nas duas tabelas; "
                  + "o orçamento de compras vai sem filtro de negócio");
        colunaObjeto = "";
        return "";
    }

    /** As colunas das tabelas envolvidas — para quando algo não casar. */
    public List<Map<String, Object>> diagnostico() {
        String sql = """
            select owner, table_name, column_name, data_type, column_id
            from   all_tab_columns
            where  (owner = 'CUSTO'    and table_name in ('LANCAMENTO_CUSTO','EMPENHO','GRUPOEMPENHO'))
               or  (owner = 'MATERIAL' and table_name = 'REALIZADO')
            order by owner, table_name, column_id
            """;
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return RowMapperUtil.toList(rs);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro no diagnóstico do orçamento de compras", e);
            throw new RuntimeException(mensagem(e), e);
        }
    }

    /** O SQL literal já montado, para colar no PL/SQL Developer. */
    public String sql(int anomesIni, int anomesFim) {
        String coluna = colunaDeNegocio();
        boolean tem = !coluna.isEmpty();
        return base()
                .replace("%ANOMES_INI%", String.valueOf(anomesIni))
                .replace("%ANOMES_FIM%", String.valueOf(anomesFim))
                .replace("%NEG_COD%",  tem ? "posto.fn_busca_arvore_objetocusto(lc." + coluna + ",'C','NG')" : "null")
                .replace("%NEG_DESC%", tem ? "nvl(posto.fn_busca_arvore_objetocusto(lc." + coluna + ",'D','NG'),'Sem negócio')" : "'Não disponível'")
                .replace("%OBJ_COD%",  tem ? "lc." + coluna : "null")
                .replace("%OBJ_DESC%", tem ? "nvl(custo.fn_busca_descricao_oc(lc." + coluna + "),'Sem objeto de custo')" : "'Não disponível'")
                .replace("%FILTRO_NEGOCIO%", "");
    }

    private static String base() {
        String cache = sqlBase;
        if (cache != null) return cache;
        try (InputStream in = OrcamentoComprasDAO.class.getResourceAsStream(RECURSO_SQL)) {
            if (in == null) throw new IllegalStateException("Recurso não encontrado: " + RECURSO_SQL);
            cache = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            sqlBase = cache;
            return cache;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler " + RECURSO_SQL, e);
        }
    }

    // ── Detalhe do item (4º nível) ────────────────────────────────────────

    private static final String RECURSO_SQL_ITENS = "/sql/orcamento-compras-itens.sql";
    private static volatile String sqlItens;

    /**
     * Os itens de um objeto de custo dentro de um empenho, no(s) mês(es)
     * escolhido(s): o realizado aberto até material + fornecedor (compra) ou
     * número do contrato (parcela de contrato).
     *
     * O total dos itens PODE não fechar com o realizado do objeto — o valor
     * que manda continua o do nível de cima, calculado pelo dashboard. Aqui é
     * só o detalhe de onde o dinheiro foi.
     *
     * @param meses   os anomes selecionados (ex.: {202509, 202510})
     * @param empenho o código do empenho aberto
     * @param objeto  o código do objeto de custo clicado; "" (vazio, mas não
     *                nulo — é o que orcamento-compras.html sempre manda) =
     *                "sem objeto"; {@code null} = todos os objetos do empenho
     *                (usado por orcamento-safra.html, que não navega por
     *                objeto de custo — só quer o realizado do empenho
     *                inteiro, não importa em qual objeto ele caiu)
     */
    public List<Map<String, Object>> itens(int[] meses, int empenho, String objeto) {
        try (Connection conn = OracleConnectionUtil.getConnection()) {
            String coluna = descobrirColunaObjeto(conn);
            if (coluna == null || coluna.isEmpty()) {
                throw new IllegalStateException("Sem coluna de objeto de custo nesta base — "
                        + "não dá para detalhar o item.");
            }
            boolean semFiltroObjeto = objeto == null;
            boolean temObjeto = !semFiltroObjeto && !objeto.isBlank();
            String filtroObjeto = semFiltroObjeto ? ""
                    : temObjeto ? "and r." + coluna + " = ?"
                    : "and r." + coluna + " is null";
            String sql = baseItens()
                    .replace("%FILTRO_ANOMES%", "and r.anomes in (" + inMeses(meses) + ")")
                    .replace("%FILTRO_OBJETO%", filtroObjeto);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, empenho);
                if (temObjeto) ps.setString(2, objeto.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    return RowMapperUtil.toList(rs);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao detalhar os itens do orçamento de compras", e);
            throw new RuntimeException(mensagem(e), e);
        }
    }

    /** Os meses como lista para o IN, já validados como inteiros. "-1" se vazio (não casa nada). */
    private static String inMeses(int[] meses) {
        if (meses == null || meses.length == 0) return "-1";
        StringBuilder sb = new StringBuilder();
        for (int m : meses) { if (sb.length() > 0) sb.append(','); sb.append(m); }
        return sb.toString();
    }

    private static String baseItens() {
        String cache = sqlItens;
        if (cache != null) return cache;
        try (InputStream in = OrcamentoComprasDAO.class.getResourceAsStream(RECURSO_SQL_ITENS)) {
            if (in == null) throw new IllegalStateException("Recurso não encontrado: " + RECURSO_SQL_ITENS);
            cache = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            sqlItens = cache;
            return cache;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler " + RECURSO_SQL_ITENS, e);
        }
    }

    private static String mensagem(SQLException e) {
        String m = e.getMessage() == null ? e.getClass().getName() : e.getMessage().trim();
        int q = m.indexOf('\n');
        return q > 0 ? m.substring(0, q) : m;
    }
}
