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
 * DUAS DECISÕES QUE VALE EXPLICAR:
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
 *    servlet, por uma lista de nomes candidatos — e o endpoint de diagnóstico
 *    mostra na tela o que ele encontrou. Recebimento de diesel são algumas
 *    dezenas de notas por mês; somar isso em Java não custa nada.
 *
 * O período filtra por to_date(substr(dataentrada_seq,1,8),'ddmmyyyy'):
 * dataentrada_seq é a data de entrada em DDMMYYYY — é a mesma coluna que
 * {@link OrdemCompraDAO} compara com '01011900' e '01012050', que só são
 * datas se lidas nesse formato. O substr protege o caso de a coluna trazer
 * sequência concatenada depois da data.
 */
public class DieselRecebimentoDAO {

    private static final Logger LOG = Logger.getLogger(DieselRecebimentoDAO.class.getName());

    /** Teto de linhas — recebimento de diesel é dezenas por mês, não milhares. */
    private static final int MAX_LINHAS = 20000;

    /**
     * Binds: data inicial, data final (ambas 'YYYY-MM-DD').
     *
     * A nota entra por outer join: item de entrada sem nota casada continua
     * sendo diesel que chegou, e some-lo por causa de um cabeçalho faltando
     * daria um painel que não fecha com o estoque.
     */
    private static final String SQL = """
        select it.*
             , m.descricao                                              material_descricao
             , m.cod_unidade                                            material_unidade
             , to_char(to_date(substr(it.dataentrada_seq,1,8),'ddmmyyyy'),'YYYY-MM-DD') data_entrada
             , nf.nrnf                                                  nota_numero
             , nf.serie                                                 nota_serie
             , oc.cod_fornecedor                                        fornecedor_codigo
             , material.fn_buscanomefornec(oc.cod_fornecedor, sysdate)  fornecedor_nome
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
        and   it.dataentrada_seq is not null
        and   to_date(substr(it.dataentrada_seq,1,8),'ddmmyyyy')
              between to_date(?, 'YYYY-MM-DD') and to_date(?, 'YYYY-MM-DD')
        order by 3
        """;

    /** Uma linha por item de entrada de diesel no período. */
    public List<Map<String, Object>> buscar(LocalDate ini, LocalDate fim) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            ps.setString(1, ini.toString());
            ps.setString(2, fim.toString());
            ps.setMaxRows(MAX_LINHAS);

            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> linhas = RowMapperUtil.toList(rs);
                LOG.info("Recebimento de diesel " + ini + " a " + fim + ": " + linhas.size() + " itens");
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

    /** O SQL literal, para o diagnóstico e para colar no PL/SQL Developer. */
    public String sql() { return SQL; }

    private static String mensagem(SQLException e) {
        String m = e.getMessage() == null ? e.getClass().getName() : e.getMessage().trim();
        int quebra = m.indexOf('\n');
        return quebra > 0 ? m.substring(0, quebra) : m;
    }
}
