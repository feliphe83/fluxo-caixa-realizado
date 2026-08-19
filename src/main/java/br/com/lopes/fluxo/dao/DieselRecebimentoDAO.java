package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Recebimento de óleo diesel — a entrada do combustível no estoque.
 *
 * A fonte é material.itensentrada: é nela que o ERP grava o item que deu
 * entrada no almoxarifado contra a nota fiscal do fornecedor. Do lado do
 * consumo, quem responde é posto.abastecimento ({@link AgroCombustivelDAO});
 * aqui é o outro lado do tanque, o que entrou.
 *
 * TRÊS DECISÕES QUE VALE EXPLICAR:
 *
 * 1. O diesel é escolhido pela DESCRIÇÃO do material, não pelo código.
 *    AgroCombustivelDAO fixa 389497 (Óleo Diesel B S10) porque lá o filtro é
 *    de um combustível só. No recebimento isso seria um erro: chega S10,
 *    chega S500, e um material novo cadastrado amanhã sumiria do painel sem
 *    ninguém perceber. Casando por '%DIESEL%' um material novo entra sozinho,
 *    e a tela mostra a quebra por material para que dê para ver o que entrou.
 *
 * 2. A consulta traz itensentrada.* em vez de somar no banco. Quantidade e
 *    valor do item não têm nome documentado em lugar nenhum deste projeto, e
 *    daqui não há como executar nada no Oracle para conferir. Trazendo as
 *    colunas todas, quem escolhe qual é a quantidade e qual é o valor é o
 *    servlet, por uma lista de nomes candidatos — e o rodapé da tela mostra o
 *    que ele encontrou. Recebimento de diesel são algumas dezenas de notas
 *    por mês; somar isso em Java não custa nada.
 *
 * 3. A DATA não é adivinhada: o DAO PERGUNTA o tipo de dataentrada_seq a
 *    all_tab_columns e monta a expressão conforme a resposta.
 *
 *    Isso não é zelo — é o resultado de dois palpites errados seguidos.
 *    {@link OrdemCompraDAO} compara essa coluna com '01011900' e '01012050',
 *    literais que só são datas lidos como DDMMYYYY, e daí veio a suposição de
 *    que fosse texto. Um to_date direto devolveu ORA-01858 ("caractere não
 *    numérico onde se esperava numérico") — que é exatamente o que
 *    to_date('19/08/26','ddmmyyyy') dá, por causa da barra: sinal de coluna
 *    DATE convertida implicitamente pelo NLS. Trocado por um CASE que só
 *    aceita oito dígitos, o painel parou de estourar e passou a devolver
 *    zero, porque a conversão implícita nunca casa com oito dígitos.
 *
 *    Com o tipo em mãos os dois casos são atendidos sem chute: sendo DATE, a
 *    coluna é usada direta; sendo texto, entra o CASE aninhado — o de fora
 *    como porteiro (só passa ^[0-9]{8}) e o de dentro escolhendo entre
 *    YYYYMMDD e DDMMYYYY pelo pedaço que parece ano. CASE aninhado é a única
 *    forma de garantir que o porteiro roda antes: dentro de um mesmo WHEN o
 *    Oracle não promete ordem de avaliação.
 */
public class DieselRecebimentoDAO {

    private static final Logger LOG = Logger.getLogger(DieselRecebimentoDAO.class.getName());

    /** Teto de linhas — recebimento de diesel é dezenas por mês, não milhares. */
    private static final int MAX_LINHAS = 20000;

    /**
     * Binds: data inicial, data final (ambas 'YYYY-MM-DD').
     *
     * A nota e a ordem de compra entram por outer join: item de entrada sem
     * cabeçalho casado continua sendo diesel que chegou, e sumir com ele
     * daria um painel que não fecha com o estoque.
     */
    /** Como o Oracle chama o tipo da coluna de data — descoberto, não suposto. */
    private static volatile String tipoDataEntrada;

    /**
     * A expressão que vira a data da entrada, conforme o tipo real da coluna.
     * Nada aqui vem de fora: é escolha entre dois textos fixos.
     */
    private static String expressaoData(String tipo) {
        if (tipo != null && (tipo.startsWith("DATE") || tipo.startsWith("TIMESTAMP"))) {
            return "trunc(it.dataentrada_seq)";
        }
        return """
                   case when regexp_like(it.dataentrada_seq, '^[0-9]{8}') then
                          case when to_number(substr(it.dataentrada_seq,1,4)) between 1900 and 2100
                                    then to_date(substr(it.dataentrada_seq,1,8),'yyyymmdd')
                               when to_number(substr(it.dataentrada_seq,5,4)) between 1900 and 2100
                                    then to_date(substr(it.dataentrada_seq,1,8),'ddmmyyyy')
                          end
                   end""";
    }

    private static final String SQL = """
        select v.*
             , to_char(v.entrada_dt, 'YYYY-MM-DD') data_entrada
        from (
            select it.*
                 , m.descricao                                              material_descricao
                 , m.cod_unidade                                            material_unidade
                 , nf.nrnf                                                  nota_numero
                 , nf.serie                                                 nota_serie
                 , oc.cod_fornecedor                                        fornecedor_codigo
                 , material.fn_buscanomefornec(oc.cod_fornecedor, sysdate)  fornecedor_nome
                 , %EXPRESSAO_DATA%                                         entrada_dt
            from       material.itensentrada it
            inner join material.material     m
                    on m.cod_material = it.cod_material
            left join  material.notafiscal   nf
                    on nf.sequencia_nf = it.sequencia_nf
                   and nf.nrnf         = it.nrnf
                   and nf.serie        = it.serie
            left join  material.ordemcompra  oc
                    on oc.nroc = it.nroc
            where upper(m.descricao) like '%DIESEL%'
        ) v
        where v.entrada_dt between to_date(?, 'YYYY-MM-DD') and to_date(?, 'YYYY-MM-DD')
        order by v.entrada_dt
        """;

    /** Uma linha por item de entrada de diesel no período. */
    public List<Map<String, Object>> buscar(LocalDate ini, LocalDate fim) {
        try (Connection conn = OracleConnectionUtil.getConnection()) {
            return buscar(conn, ini, fim);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar recebimento de diesel", e);
            throw new RuntimeException(mensagem(e), e);
        }
    }

    private List<Map<String, Object>> buscar(Connection conn, LocalDate ini, LocalDate fim) {
        String sql = SQL.replace("%EXPRESSAO_DATA%", expressaoData(descobrirTipoData(conn)));
        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ini.toString());
            ps.setString(2, fim.toString());
            ps.setMaxRows(MAX_LINHAS);

            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> linhas = RowMapperUtil.toList(rs);
                LOG.info("Recebimento de diesel " + ini + " a " + fim + ": " + linhas.size()
                         + " itens (dataentrada_seq é " + tipoDataEntrada + ")");
                return linhas;
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar recebimento de diesel", e);
            // A mensagem do Oracle sobe até a tela: se for nome de coluna ou
            // de tabela errado, é o ORA-xxxxx que diz qual, e não adianta
            // esconder isso atrás de "erro ao consultar".
            throw new RuntimeException(mensagem(e), e);
        }
    }

    /**
     * O tipo de material.itensentrada.dataentrada_seq, guardado depois da
     * primeira vez. Se a consulta ao dicionário falhar, devolve null e a
     * expressão cai no caminho de texto — degradar para o palpite antigo é
     * melhor do que derrubar o painel por causa do dicionário.
     */
    private static String descobrirTipoData(Connection conn) {
        String cache = tipoDataEntrada;
        if (cache != null) return "?".equals(cache) ? null : cache;

        String sql = "select data_type from all_tab_columns "
                   + "where owner = 'MATERIAL' and table_name = 'ITENSENTRADA' "
                   + "and column_name = 'DATAENTRADA_SEQ'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            String tipo = rs.next() ? rs.getString(1) : null;
            tipoDataEntrada = tipo == null ? "?" : tipo.trim().toUpperCase();
            LOG.info("material.itensentrada.dataentrada_seq é do tipo " + tipoDataEntrada);
            return tipo == null ? null : tipoDataEntrada;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Não foi possível ler o tipo de dataentrada_seq", e);
            tipoDataEntrada = "?";
            return null;
        }
    }

    /** O tipo descoberto, para o painel poder mostrar de onde veio a data. */
    public String tipoDataEntrada() {
        return tipoDataEntrada == null || "?".equals(tipoDataEntrada) ? "" : tipoDataEntrada;
    }

    // ── Diagnóstico ───────────────────────────────────────────────────────
    // O painel foi escrito sem nunca poder executar nada neste Oracle, e o
    // primeiro palpite sobre o formato da data já custou um ORA-01858. Em vez
    // de continuar adivinhando, estas duas consultas perguntam ao próprio
    // banco: quais colunas a tabela tem, de que tipo, e como os valores da
    // data realmente se parecem.

    private static final String SQL_COLUNAS = """
        select column_name, data_type, data_length, data_precision, data_scale, nullable, column_id
        from   all_tab_columns
        where  owner = ? and table_name = ?
        order by column_id
        """;

    /** Colunas e tipos de uma tabela — owner e nome em MAIÚSCULAS. */
    public List<Map<String, Object>> colunas(String owner, String tabela) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_COLUNAS)) {
            ps.setString(1, owner.toUpperCase());
            ps.setString(2, tabela.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao listar colunas de " + owner + "." + tabela, e);
            throw new RuntimeException(mensagem(e), e);
        }
    }

    /**
     * Amostra CRUA da coluna de data, sem nenhuma conversão — é o valor como
     * está gravado que responde se é DDMMYYYY, YYYYMMDD, uma data de verdade
     * ou outra coisa. Vem sempre pelo lado do diesel, para a amostra ser das
     * linhas que o painel realmente lê.
     */
    private static final String SQL_AMOSTRA_DATA = """
        select dataentrada_seq
             , count(*) linhas
        from  ( select it.dataentrada_seq
                from       material.itensentrada it
                inner join material.material     m on m.cod_material = it.cod_material
                where upper(m.descricao) like '%DIESEL%'
                and   rownum <= 500 )
        group by dataentrada_seq
        order by 1 desc
        """;

    public List<Map<String, Object>> amostraData() {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_AMOSTRA_DATA)) {
            ps.setMaxRows(40);
            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro na amostra de dataentrada_seq", e);
            throw new RuntimeException(mensagem(e), e);
        }
    }

    /** O SQL como ele realmente vai ao banco, para colar no PL/SQL Developer. */
    public String sql() {
        return SQL.replace("%EXPRESSAO_DATA%", expressaoData(tipoDataEntrada()));
    }

    private static String mensagem(SQLException e) {
        String m = e.getMessage() == null ? e.getClass().getName() : e.getMessage().trim();
        int quebra = m.indexOf('\n');
        return quebra > 0 ? m.substring(0, quebra) : m;
    }
}
