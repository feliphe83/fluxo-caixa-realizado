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
 * Itens de cotação com o orçamento ESTOURADO e ainda sem a aprovação de
 * alçada — o que precisa de alguém liberar para a compra seguir.
 *
 * A consulta é a que a controladoria usa no ERP: pega o resultado da cotação
 * marcado com orcamento_estourado = 'S', aprovadoparacompra = 'F' e que ainda
 * não tem registro em aprovorcestouroalcadaetapa (ninguém aprovou o estouro).
 *
 * O único filtro configurável é o NEGÓCIO (1 agrícola, 3 indústria, 4
 * administrativo); zero traz todos. O bind aparece duas vezes na consulta
 * ("negocio = X or X = 0"), então é preenchido nas duas posições com o mesmo
 * valor.
 *
 * Colunas usadas na mensagem (em minúsculo, como o RowMapperUtil devolve):
 * nr_cotacao, cod_material, descricao, nr_solicitacao, precototal, comprador,
 * nome (fornecedor), negocio, qtde_aprovada, cod_unidade, qtde_estoque.
 */
public class OrcamentoEstouradoDAO {

    private static final Logger LOG = Logger.getLogger(OrcamentoEstouradoDAO.class.getName());

    /** Dois binds: o código do negócio (0 = todos), nas duas posições do OR. */
    private static final String SQL = """
        SELECT ResultadoCotacaoItem.NR_COTACAO
             , ResultadoCotacaoItem.COD_MATERIAL
             , Material.DESCRICAO
             , ResultadoCotacaoItem.NR_SOLICITACAO
             ,(
          ((ResultadoCotacaoItem.PRECO / ResultadoCotacaoItem.PRECOPARAXQTDE) * CotacaoItem.Qtde_Aprovada)
          - resultadocotacaoitem.descontototal
          - ((ResultadoCotacaoItem.PRECO / ResultadoCotacaoItem.PRECOPARAXQTDE) * CotacaoItem.Qtde_Aprovada * (ResultadoCotacaoItem.Descontoporcentagem / 100))
        )
        * (1 + (ResultadoCotacaoItem.Perc_Ipi / 100)) AS PrecoTotal
             , ResultadoCotacaoItem.PRECO
             , resultadocotacaoitem.descontototal
             , CotacaoItem.QUANTIDADE
             , CotacaoItem.Qtde_Aprovada
             , Material.Cod_Unidade
             , rh.fn_nomefuncionario( cotacao.cod_grupoempresa
                                    , cotacao.cod_funcionario) comprador
             , Cotacao.COD_FORNECEDOR
             , geral.fn_busca_nomefantasia( cotacao.cod_fornecedor
                                          , 'RS') nome
             , decode(material.fn_existe_inventario_aberto(materialporfilial.cod_grupoempresa,
                                                           materialporfilial.cod_empresa,
                                                           materialporfilial.cod_filial,
                                                           materialporfilial.cod_material),'N',
                      nvl(material.fn_vlr_inventario_material(materialporfilial.cod_grupoempresa,
                                                           materialporfilial.cod_empresa,
                                                           materialporfilial.cod_filial,
                                                           materialporfilial.cod_material,
                                                           0,
                                                           to_char(sysdate,'YYYY'),
                                                           to_char(sysdate,'MM'),
                                                           'Q'),0),
                      '') qtde_estoque
             , o.negocio
        from   material.cotacaoitem cotacaoitem
             , material.materialporfilial
             , material.solicitacaocompra solicitacaocompra
             , material.material
             , material.cotacao cotacao
             , material.historicoempresacompra
             , material.resultadocotacaoitem resultadocotacaoitem
             , rh.objetocusto o
        where  cotacaoitem.nr_cotacao                            = cotacao.nr_cotacao
        and solicitacaocompra.cod_objetocusto = o.cod_objetocusto
        and (o.negocio = ?
        or ? = 0)
        and    cotacaoitem.cod_material                          = materialporfilial.cod_material
        and    cotacaoitem.nr_solicitacao                        = resultadocotacaoitem.nr_solicitacao
        and    materialporfilial.cod_material                    = resultadocotacaoitem.cod_material
        and    materialporfilial.cod_grupoempresa                = historicoempresacompra.cod_grupoempresa
        and    materialporfilial.cod_empresa                     = historicoempresacompra.cod_empresa
        and    materialporfilial.cod_filial                      = historicoempresacompra.cod_filial
        and    materialporfilial.situacao                        = 'A'
        and    sysdate  between materialporfilial.datainicio and nvl(materialporfilial.datatermino, to_date('31/12/9999','dd/mm/rrrr'))
        and    solicitacaocompra.nr_solicitacao               (+)= resultadocotacaoitem.nr_solicitacao
        and    material.cod_material                             = resultadocotacaoitem.cod_material
        and    cotacao.cod_grupoempresa                          = historicoempresacompra.cod_grupoempresa
        and    cotacao.cod_empresa                               = historicoempresacompra.cod_empresa
        and    cotacao.cod_filial                                = historicoempresacompra.cod_filial
        and    cotacao.datageracao between historicoempresacompra.datainicio and nvl(historicoempresacompra.datatermino, to_date('31/12/9999','dd/mm/rrrr'))
        and    historicoempresacompra.cod_filial_compra          = 1
        and    historicoempresacompra.cod_empresa_compra         = 1
        and    historicoempresacompra.cod_grupoempresa           = 1
        and    cotacao.nr_cotacao                                = resultadocotacaoitem.nr_cotacao
        and    not exists ( select 1
                            from   material.aprovorcestouroalcadaetapa
                            where  aprovorcestouroalcadaetapa.nr_solicitacao = resultadocotacaoitem.nr_solicitacao
                            and    aprovorcestouroalcadaetapa.nr_cotacao     = resultadocotacaoitem.nr_cotacao
                            and    aprovorcestouroalcadaetapa.cod_plano      = resultadocotacaoitem.cod_plano
                            and    aprovorcestouroalcadaetapa.cod_material   = resultadocotacaoitem.cod_material )
        and    resultadocotacaoitem.situacao                     = 'L'
        and    resultadocotacaoitem.sequencia                    = 1
        and    resultadocotacaoitem.aprovadoparacompra           = 'F'
        and    resultadocotacaoitem.orcamento_estourado          = 'S'
        order by material.descricao
        """;

    /**
     * Os itens com orçamento estourado pendentes de aprovação.
     *
     * @param negocio 0 para todos, ou 1/3/4 para agrícola/indústria/administrativo
     */
    public List<Map<String, Object>> buscar(int negocio) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            ps.setInt(1, negocio);
            ps.setInt(2, negocio);

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar orçamentos estourados (negócio "
                    + negocio + "): " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de orçamentos estourados: " + e.getMessage(), e);
        }
    }
}
