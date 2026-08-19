package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Faturamento de venda de produtos — itens de nota fiscal.
 *
 * A consulta é a da área fiscal, com as quatro pernas como vieram:
 *
 *  1. cliente PESSOA FÍSICA   (rh.fisica → rh.pessoa)
 *  2. cliente PESSOA JURÍDICA (rh.juridica, casada pelo CGC)
 *  3. OUTRAS PESSOAS          (material.outraspessoas)
 *  4. CANA-DE-AÇÚCAR          (cod_produto = 12, sem cliente, em UNION ALL)
 *
 * Só duas coisas mudaram, e nenhuma toca em filtro ou junção:
 *
 * A) As quatro colunas chamadas "descricao" ganharam apelido — cidade,
 *    rotina, produto e destino. É obrigatório: {@link RowMapperUtil} monta
 *    cada linha num mapa indexado pelo NOME da coluna, e quatro "descricao"
 *    colapsariam em uma só. A última venceria e o PRODUTO se perderia, que
 *    é justamente o eixo deste painel. O mesmo vale para razaosocial na
 *    segunda perna, que virou "nome" para casar com as outras.
 *
 * B) O período entra por FORA, envolvendo o union inteiro. Assim cada perna
 *    fica igual ao original — inclusive o ">= '01/09/2021'", que continua
 *    lá dentro. Consequência: pedir um período anterior a setembro/2021 não
 *    traz nada, porque o filtro de dentro vence.
 */
public class FaturamentoVendasDAO {

    private static final Logger LOG = Logger.getLogger(FaturamentoVendasDAO.class.getName());

    /** Binds: dataIni, dataFim (yyyy-MM-dd). */
    private static final String SQL = """
        select * from (

        select
                  no.nr_nf,
                  no.idcontratoparceria contrato,
                  no.dataemissao,
                  no.cod_fornecedor,
                  ps.nome,
                  ci.estado,
                  ci.descricao cidade,
                  no.cod_rotina,
                  ro.descricao rotina,
                  pr.descricao produto,
                  b.cod_destinoitem,
                  de.descricao destino,
                  b.cod_unidade_com,
                  un.descricaounidade,
                  b.quantidade ,
                  b.valor_unitario,
                  b.valor_total_item,
                  no.valor_icms,
                  no.valor_ipi ,
                  no.valordesconto ,
                  no.valoroutrasdesp,
                  no.valor_icms_st,
                  no.valor_total_nota

        from faturamento.itensnotasfiscais b ,
             faturamento.notasfiscais no  ,
             faturamento.produtodestinoitem p,
             faturamento.rotinafiscal ro,
             faturamento.destinoitem de,
             producao.produto pr,
             rh.cidade ci,
             material.unidade un,
             material.fornecedor f ,
             rh.fisica fs ,
             rh.pessoa ps

        where
         no.dataemissao  >= '01/09/2021'
        and b.id_nf = no.id_nf
        and no.cod_rotina in (2,7,9,35,91,178,183,185,187,206,208,210,211,215,230,231,237,265,275,199)
        and no.cod_fornecedor = f.cod_fornecedor
        and no.status_proc = 4
        and no.cod_rotina = ro.cod_rotina
        and ps.cod_cidade = ci.cod_cidade
        and b.cod_produto = p.cod_produto
        and b.cod_unidade_com = un.cod_unidade
        and b.cod_produto = pr.cod_produto
        and b.cod_destinoitem = de.cod_destinoitem
        and b.cod_destinoitem = p.cod_destinoitem
        and f.cod_pessoa = fs.cod_pessoa
        and f.cod_fornecedor not in  1
        and fs.cod_pessoa = ps.cod_pessoa
        union

        select

                  a.nr_nf,
                  a.idcontratoparceria contrato,
                  a.dataemissao,
                  a.cod_fornecedor,
                  j.razaosocial nome,
                  ci.estado,
                  ci.descricao cidade,
                  a.cod_rotina,
                  ro.descricao rotina,
                  pr.descricao produto,
                  b.cod_destinoitem,
                  de.descricao destino,
                  b.cod_unidade_com ,
                  un.descricaounidade,
                  b.quantidade,
                  b.valor_unitario ,
                  b.valor_total_item,
                  a.valor_icms,
                  a.valor_ipi ,
                  a.valordesconto,
                  a.valoroutrasdesp,
                  a.valor_icms_st ,
                  a.valor_total_nota

        from faturamento.notasfiscais a  ,
             faturamento.itensnotasfiscais b ,
             faturamento.produtodestinoitem p  ,
             material.fornecedor f ,
             rh.juridica j ,
             rh.pessoa ps ,
             faturamento.rotinafiscal ro,
             faturamento.destinoitem de ,
             producao.produto pr,
             rh.cidade ci ,
             material.unidade un

        where
        a.id_nf = b.id_nf
        and b.cod_destinoitem = p.cod_destinoitem
         and a.dataemissao  >= '01/09/2021'
         and f.cgc = j.cgc
        and ps.cod_cidade = ci.cod_cidade
        and a.cod_rotina = ro.cod_rotina
        and a.cod_rotina  in (2,7,9,35,91,178,183,185,187,206,208,210,211,215,230,231,237,265,275,199)
        and b.cod_produto = p.cod_produto
        and j.cod_pessoa = ps.cod_pessoa
        and b.cod_produto = pr.cod_produto
        and b.cod_destinoitem = de.cod_destinoitem
        and a.status_proc = 4
        and b.cod_destinoitem = p.cod_destinoitem
        and b.cod_unidade_com = un.cod_unidade
        and a.cod_fornecedor = f.cod_fornecedor
        and f.cod_pessoa = j.cod_pessoa


        union

        select

                a.nr_nf,
                a.idcontratoparceria contrato,
                a.dataemissao,
                a.cod_fornecedor,
                ps.nome,
                ci.estado,
                ci.descricao cidade,
                a.cod_rotina,
                ro.descricao rotina,
                pr.descricao produto,
                b.cod_destinoitem,
                de.descricao destino,
                b.cod_unidade_com ,
                un.descricaounidade,
                b.quantidade,
                b.valor_unitario ,
                b.valor_total_item,
                a.valor_icms,
                a.valor_ipi ,
                a.valordesconto,
                a.valoroutrasdesp,
                a.valor_icms_st ,
                a.valor_total_nota

        from faturamento.notasfiscais a  ,
             faturamento.itensnotasfiscais b ,
             faturamento.produtodestinoitem p  ,
             material.fornecedor f ,
             material.outraspessoas ou ,
             rh.pessoa ps ,
             faturamento.rotinafiscal ro,
             faturamento.destinoitem de ,
             producao.produto pr,
             rh.cidade ci ,
             material.unidade un

        where
        a.id_nf = b.id_nf
        and b.cod_destinoitem = p.cod_destinoitem
         and a.dataemissao  >= '01/09/2021'
        and ps.cod_cidade = ci.cod_cidade
        and a.cod_rotina = ro.cod_rotina
        and a.cod_rotina  in (2,7,9,35,91,178,183,185,187,206,208,210,211,215,230,231,237,265,275,199)
        and b.cod_produto = p.cod_produto
        and ou.cod_pessoa = ps.cod_pessoa
        and b.cod_produto = pr.cod_produto
        and b.cod_destinoitem = de.cod_destinoitem
        and a.status_proc = 4
        and b.cod_destinoitem = p.cod_destinoitem
        and b.cod_unidade_com = un.cod_unidade
        and a.cod_fornecedor = f.cod_fornecedor
        and f.cod_pessoa = ou.cod_pessoa

        /* consulta para trazer faturamento de venda de cana de acucar  */

        UNION ALL

        SELECT
                a.nr_nf,
                a.idcontratoparceria contrato,
                a.dataemissao,
                a.cod_fornecedor,
                'null' nome,
                'null' estado,
                'null' cidade,
                a.cod_rotina,
                'null' rotina,
                 pr.descricao produto,
                b.cod_destinoitem,
                '' destino,
                b.cod_unidade_com ,
                'ton' descricaounidade,
                b.quantidade,
                b.valor_unitario ,
                b.valor_total_item,
                a.valor_icms,
                a.valor_ipi ,
                a.valordesconto,
                a.valoroutrasdesp,
                a.valor_icms_st ,
                a.valor_total_nota
        FROM
             faturamento.notasfiscais a  ,
             faturamento.itensnotasfiscais b,
             producao.produto pr


         where a.id_nf = b.id_nf
         and b.cod_produto = pr.cod_produto


         and b.cod_produto = 12
         and a.dataemissao  >= '01/09/2021'
         and a.cod_rotina  in (2,7,9,35,91,178,183,185,187,206,208,210,211,215,230,231,237,265,275,199)
         and a.situacao = '0'

        )
        where dataemissao >= to_date(?, 'YYYY-MM-DD')
        and   dataemissao <= to_date(?, 'YYYY-MM-DD')
        order by dataemissao, nr_nf
        """;

    /**
     * Itens faturados no período.
     *
     * @param dataIni yyyy-MM-dd
     * @param dataFim yyyy-MM-dd
     */
    public List<Map<String, Object>> buscar(String dataIni, String dataFim) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            ps.setString(1, dataIni);
            ps.setString(2, dataFim);

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar faturamento de vendas (" + dataIni
                    + " a " + dataFim + "): " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de faturamento: " + e.getMessage(), e);
        }
    }
}
