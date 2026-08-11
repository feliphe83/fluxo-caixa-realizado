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
 * Ordens de compra aprovadas HOJE cujo valor total passa de um limite.
 *
 * São três origens unidas: a ordem de compra imediata e duas variações da
 * cotação virada em ordem de compra (uma para plano de pagamento normal,
 * outra para o plano informado manualmente, que busca o vencimento em
 * ordemcomprapagamento).
 *
 * O limite é aplicado sobre o total da ORDEM, não do item, e esse total é
 * somado DEPOIS do GROUP BY que junta as parcelas. O nível
 * de dentro tem uma linha por data de pagamento (PP1.DATAPGTO está no group by
 * de lá), então uma ordem de 70 mil paga em três parcelas chegava ao WhatsApp
 * como 210 mil — o mesmo item somado três vezes.
 *
 * vlr_total_por_numero_processo soma cada item UMA vez por número de processo,
 * e o filtro do valor mínimo vem depois dela — uma ordem de dez itens pequenos
 * que somem mais que o limite entra inteira no alerta.
 *
 * Como os demais alertas de contrato e divergência, não filtra por aprovador:
 * todo destinatário do agendamento recebe a mesma lista.
 *
 * Duas mudanças em relação à consulta original, ambas em trechos que não
 * afetam o resultado esperado:
 *
 * - saiu o "and oc.nroc = 148816" do primeiro ramo de cotação. Era um número
 *   fixo, resíduo de teste: com ele o ramo só poderia devolver aquela ordem,
 *   que nunca voltará a ser aprovada hoje — na prática o ramo estava morto;
 * - saiu um VENCIMENTO calculado no nível de dentro (LISTAGG de cod_plano).
 *   Nenhum nível de fora o selecionava, e a condição dele ("numero =
 *   tmp.numero") não casava com nenhuma coluna de ordemcomprapagamento, o
 *   que fazia o Oracle resolvê-la contra o próprio tmp e varrer a tabela
 *   inteira a cada linha. O VENCIMENTO que vai na mensagem é o de fora.
 *
 * Colunas usadas na mensagem: numero_processo, aprovador, nome_fornecedor,
 * desc_material, qtdesolicitada, preco, ultima_compra, variacao_percentual,
 * vencimento, vlrtotal, vlr_total_por_numero_processo.
 */
public class OrdemCompraAprovadaDAO {

    private static final Logger LOG = Logger.getLogger(OrdemCompraAprovadaDAO.class.getName());

    /** Bind único: o valor total mínimo da ordem — ver {@link #buscarAprovadasAcimaDe(double)}. */
    private static final String SQL = """
        SELECT * FROM (
          SELECT item_unico.*
               , SUM(item_unico.VLRTOTAL)
                   OVER (PARTITION BY item_unico.NUMERO_PROCESSO) AS VLR_TOTAL_POR_NUMERO_PROCESSO
          FROM (
        SELECT APROVADOR, COD_APROVADOR, TIPO, DATA_PROCESSO, NUMERO_PROCESSO, COD_ETAPA,
               COD_MATERIAL, COD_REQUISITANTE, REQUISITANTE, QTDESOLICITADA, DATA_APROVACAO,
               DESC_MATERIAL, NR_SOLICITACAO, VLRTOTAL, ITEM, COD_ALMOXARIFADO, DESC_ALMOXARIFADO,
               DESC_OBJETOCUSTO, PRECO, ULTIMA_COMPRA, COD_FORNECEDOR, NOME_FORNECEDOR,
               VARIACAO_PERCENTUAL, COD_UNIDADE,
               (SELECT LISTAGG(DISTINCT TO_CHAR(ordemcomprapagamento.datapgto,'DD/MM/YYYY'), ', ')
                         WITHIN GROUP (ORDER BY TO_CHAR(ordemcomprapagamento.datapgto,'DD/MM/YYYY'))
                  FROM material.ordemcomprapagamento ordemcomprapagamento
                 WHERE ordemcomprapagamento.nroc = resultado.numero_processo) AS VENCIMENTO
        FROM (
            SELECT tmp.aprovador
                 , tmp.cod_aprovador
                 , tmp.tipo
                 , TRUNC(tmp.data) AS data_processo
                 , tmp.numero AS numero_processo
                 , tmp.cod_etapa
                 , tmp.cod_material
                 , tmp.cod_requisitante
                 , tmp.requisitante
                 , tmp.qtdesolicitada
                 , TRUNC(tmp.data_aprovacao) AS data_aprovacao
                 , tmp.descricao AS desc_material
                 , tmp.nr_solicitacao
                 , tmp.vlrtotal
                 , tmp.item
                 , tmp.cod_almoxarifado
                 , (SELECT almoxarifado.descricaoalmoxarifado
                      FROM material.almoxarifado almoxarifado
                     WHERE almoxarifado.cod_filial = tmp.cod_filial
                       AND almoxarifado.cod_empresa = tmp.cod_empresa
                       AND almoxarifado.cod_grupoempresa = tmp.cod_grupoempresa
                       AND almoxarifado.cod_almoxarifado = tmp.cod_almoxarifado) AS desc_almoxarifado
                 , (SELECT objetocusto.descricao
                      FROM rh.objetocusto objetocusto
                     WHERE objetocusto.cod_objetocusto = tmp.cod_objetocusto) AS desc_objetocusto
                 , tmp.preco
                 , tmp.ultima_compra
                 , tmp.cod_fornecedor
                 , material.fn_buscanomefornec(tmp.cod_fornecedor, SYSDATE) AS nome_fornecedor
                 , ROUND((tmp.preco - tmp.ultima_compra) / NULLIF(tmp.ultima_compra, 0) * 100, 2) AS variacao_percentual
                 , tmp.cod_unidade
            FROM (
                select rh.fn_nomefuncionario(ordemcompraimediata.cod_grupoempresa,ordemcompraimediata.cod_aprovador) aprovador
                     , to_number(ordemcompraimediata.cod_aprovador) cod_aprovador
                     , '04 Ordem Compra Imediata' tipo
                     , trunc(ordemcompraimediata.data_oc) data
                     , ordemcompraimediata.nroc_imediata numero
                     , null cod_etapa
                     , itensordemcompraimediata.cod_material
                     , ordemcompraimediata.cod_funcionario cod_requisitante
                     , rh.fn_nomefuncionario(ordemcompraimediata.cod_grupoempresa,ordemcompraimediata.cod_funcionario) requisitante
                     , itensordemcompraimediata.quantidade qtdesolicitada
                     , trunc(ordemcompraimediata.data_aprovacao) data_aprovacao
                     , material.descricao
                     , itensordemcompraimediata.nr_solicitacao
                     , null valor_total
                     , itensordemcompraimediata.item
                     , itensordemcompraimediata.cod_almoxarifado
                     , decode( itensordemcompraimediata.cod_objetocusto
                             , null
                             ,(nvl((select equip_obj.cod_objetocusto
                                    from   automotivo.historicoequipamentoobcusto equip_obj
                                    where  equip_obj.cod_equipamento  = itensordemcompraimediata.cod_equpamento
                                    and    trunc(ordemcompraimediata.data_oc) between equip_obj.data_inicio and nvl(equip_obj.data_final,to_date('31/12/2999'))
                                    and    equip_obj.cod_filial       = ordemcompraimediata.cod_filial
                                    and    equip_obj.cod_empresa      = ordemcompraimediata.cod_empresa
                                    and    equip_obj.cod_grupoempresa = ordemcompraimediata.cod_grupoempresa)
                                  ,(select ind_obj.cod_objetocusto
                                    from   industria.historico_objetocusto_cte ind_obj
                                    where  ind_obj.codigo_cte       = itensordemcompraimediata.codigo_cte
                                    and    trunc(ordemcompraimediata.data_oc) between ind_obj.datainicio and nvl(ind_obj.datafim,to_date('31/12/2999'))
                                    and    ind_obj.cod_filial       = ordemcompraimediata.cod_filial
                                    and    ind_obj.cod_empresa      = ordemcompraimediata.cod_empresa
                                    and    ind_obj.cod_grupoempresa = ordemcompraimediata.cod_grupoempresa)))
                             , itensordemcompraimediata.cod_objetocusto) cod_objetocusto
                     , ordemcompraimediata.cod_grupoempresa
                     , ordemcompraimediata.cod_empresa
                     , ordemcompraimediata.cod_filial
                     , sum( itensordemcompraimediata.quantidade * itensordemcompraimediata.preco) vlrtotal
                     , itensordemcompraimediata.preco
                     , material.fn_busca_ultimo_preco(1,1,1, itensordemcompraimediata.cod_material) ultima_compra
                     , ordemcompraimediata.cod_fornecedor, material.cod_unidade, null cod_plano
                from   material.material material
                     , material.itensordemcompraimediata itensordemcompraimediata
                     , material.ordemcompraimediata ordemcompraimediata
                where  material.cod_material                           = itensordemcompraimediata.cod_material
                and    itensordemcompraimediata.nroc_imediata          = ordemcompraimediata.nroc_imediata
                and    ordemcompraimediata.situacao                    = 'A'
                and    ordemcompraimediata.cod_grupoempresa            = 1
                and    ordemcompraimediata.cod_empresa                 = 1
                and    ordemcompraimediata.cod_filial                  = 1
                and    ordemcompraimediata.data_aprovacao = TRUNC(SYSDATE)
                group  by rh.fn_nomefuncionario(ordemcompraimediata.cod_grupoempresa,ordemcompraimediata.cod_aprovador)
                        , to_number(ordemcompraimediata.cod_aprovador)
                        , trunc(ordemcompraimediata.data_oc)
                        , to_number(ordemcompraimediata.nroc_imediata)
                        , itensordemcompraimediata.cod_material
                        , ordemcompraimediata.cod_funcionario
                        , rh.fn_nomefuncionario(ordemcompraimediata.cod_grupoempresa,ordemcompraimediata.cod_funcionario)
                        , itensordemcompraimediata.quantidade
                        , trunc(ordemcompraimediata.data_aprovacao)
                        , material.descricao
                        , itensordemcompraimediata.nr_solicitacao
                        , itensordemcompraimediata.item
                        , itensordemcompraimediata.cod_almoxarifado
                        , itensordemcompraimediata.cod_objetocusto
                        , ordemcompraimediata.cod_grupoempresa
                        , ordemcompraimediata.cod_empresa
                        , ordemcompraimediata.cod_filial
                        , itensordemcompraimediata.cod_equpamento
                        , ordemcompraimediata.data_oc
                        , itensordemcompraimediata.codigo_cte
                        , itensordemcompraimediata.preco
                        , ordemcompraimediata.nroc_imediata
                        , ordemcompraimediata.cod_fornecedor, material.cod_unidade, ordemcompraimediata.cod_plano

                union

                select rh.fn_nomefuncionario(to_number(aprovacaoparacompra.cod_grupoempresa), to_number(aprovacaoparacompra.cod_funcionario)) aprovador
                     , to_number(aprovacaoparacompra.cod_funcionario) cod_aprovador
                     , '03 Cotações [Ordem de Compra]' tipo
                     , solicitacaocompra.data data
                     , oc.nroc numero
                     , null cod_etapa
                     , solicitacaocompra.cod_material
                     , solicitacaocompra.cod_funcionario cod_requisitante
                     , rh.fn_nomefuncionario(solicitacaocompra.cod_grupoempresa_destino,solicitacaocompra.cod_funcionario) requisitante
                     , solicitacaocompra.qtdesolicitada
                     , to_date(aprovacaoparacompra.dataaprovacao) data_aprovacao
                     , material.descricao
                     , solicitacaocompra.nr_solicitacao
                     , null valor_total
                     , null item
                     , solicitacaocompra.cod_almoxarifado
                     , solicitacaocompra.cod_objetocusto
                     , solicitacaocompra.cod_grupoempresa_destino as cod_grupoempresa
                     , solicitacaocompra.cod_empresa_destino as cod_empresa
                     , solicitacaocompra.cod_filial_destino as cod_filial
                     , sum(solicitacaocompra.qtdesolicitada * resultadocotacaoitem.preco- resultadocotacaoitem.vrdesc_rateio_ind-resultadocotacaoitem.descontototal ) vlrtotal
                     , sum(resultadocotacaoitem.preco-resultadocotacaoitem.vrdesc_rateio_ind-resultadocotacaoitem.descontototal ) preco
                     , material.fn_busca_ultimo_preco(1,1,1,solicitacaocompra.cod_material) ultima_compra
                     , oc1.cod_fornecedor, material.cod_unidade, TO_CHAR(pp.descricaoplano)
                from   material.material
                     , material.aprovacaoparacompra aprovacaoparacompra
                     , material.resultadocotacaoitem resultadocotacaoitem
                     , material.solicitacaocompra
                     , material.historicoempresacompra hist_compra_gef_todas
                     , material.historicoempresacompra hist_compra_gef_logada
                     , material.itensordemcompra oc
                     , material.ordemcompra oc1, material.planopagamento pp
                where  material.cod_material = solicitacaocompra.cod_material
                and    resultadocotacaoitem.cod_material = oc.cod_material
                and    oc.nroc = oc1.nroc   and oc1.cod_plano = pp.cod_plano      and pp.Informarmanualmente = 'N'
                and    resultadocotacaoitem.nr_solicitacao = oc.nr_solicitacao
                and    aprovacaoparacompra.nr_cotacao = resultadocotacaoitem.nr_cotacao
                and    aprovacaoparacompra.cod_plano = resultadocotacaoitem.cod_plano
                and    aprovacaoparacompra.cod_material = resultadocotacaoitem.cod_material
                and    aprovacaoparacompra.nr_solicitacao = resultadocotacaoitem.nr_solicitacao
                and    resultadocotacaoitem.nr_solicitacao = solicitacaocompra.nr_solicitacao
                and    resultadocotacaoitem.cod_material = solicitacaocompra.cod_material
                and    resultadocotacaoitem.aprovadoparacompra = 'T'
                and    aprovacaoparacompra.dataaprovacao = TRUNC(SYSDATE)
                and    resultadocotacaoitem.situacao = 'A'
                and    solicitacaocompra.solicitacaoaprovada = 'T'
                and    solicitacaocompra.cod_grupoempresa_destino = hist_compra_gef_todas.cod_grupoempresa
                and    solicitacaocompra.cod_empresa_destino = hist_compra_gef_todas.cod_empresa
                and    solicitacaocompra.cod_filial_destino = hist_compra_gef_todas.cod_filial
                and    TRUNC(SYSDATE) between hist_compra_gef_todas.datainicio and nvl(hist_compra_gef_todas.datatermino, to_date('31/12/9999','DD/MM/YYYY'))
                and    hist_compra_gef_todas.cod_filial_compra = hist_compra_gef_logada.cod_filial_compra
                and    hist_compra_gef_todas.cod_empresa_compra = hist_compra_gef_logada.cod_empresa_compra
                and    hist_compra_gef_todas.cod_grupoempresa = hist_compra_gef_logada.cod_grupoempresa
                and    TRUNC(SYSDATE) between hist_compra_gef_logada.datainicio and nvl(hist_compra_gef_logada.datatermino, to_date('31/12/9999','DD/MM/YYYY'))
                and    hist_compra_gef_logada.cod_filial = 1
                and    hist_compra_gef_logada.cod_empresa = 1
                and    hist_compra_gef_logada.cod_grupoempresa = 1
                and    not exists(select 1 from material.ordemcompraimediata where ordemcompraimediata.nr_cotacao = resultadocotacaoitem.nr_cotacao)
                group by rh.fn_nomefuncionario(to_number(aprovacaoparacompra.cod_grupoempresa),to_number(aprovacaoparacompra.cod_funcionario))
                       , to_number(aprovacaoparacompra.cod_funcionario)
                       , solicitacaocompra.data
                       , to_number(resultadocotacaoitem.nr_cotacao)
                       , solicitacaocompra.cod_material
                       , solicitacaocompra.cod_funcionario
                       , rh.fn_nomefuncionario(solicitacaocompra.cod_grupoempresa_destino,solicitacaocompra.cod_funcionario)
                       , solicitacaocompra.qtdesolicitada
                       , to_date(aprovacaoparacompra.dataaprovacao)
                       , material.descricao
                       , solicitacaocompra.nr_solicitacao
                       , solicitacaocompra.cod_almoxarifado
                       , solicitacaocompra.cod_objetocusto
                       , solicitacaocompra.cod_grupoempresa_destino
                       , solicitacaocompra.cod_empresa_destino
                       , solicitacaocompra.cod_filial_destino
                       , resultadocotacaoitem.preco
                       , oc.nroc
                       , oc1.cod_fornecedor, material.cod_unidade, pp.descricaoplano

                union

                select rh.fn_nomefuncionario(to_number(aprovacaoparacompra.cod_grupoempresa), to_number(aprovacaoparacompra.cod_funcionario)) aprovador
                     , to_number(aprovacaoparacompra.cod_funcionario) cod_aprovador
                     , '03 Cotações [Ordem de Compra]' tipo
                     , solicitacaocompra.data data
                     , oc.nroc numero
                     , null cod_etapa
                     , solicitacaocompra.cod_material
                     , solicitacaocompra.cod_funcionario cod_requisitante
                     , rh.fn_nomefuncionario(solicitacaocompra.cod_grupoempresa_destino,solicitacaocompra.cod_funcionario) requisitante
                     , solicitacaocompra.qtdesolicitada
                     , to_date(aprovacaoparacompra.dataaprovacao) data_aprovacao
                     , material.descricao
                     , solicitacaocompra.nr_solicitacao
                     , null valor_total
                     , null item
                     , solicitacaocompra.cod_almoxarifado
                     , solicitacaocompra.cod_objetocusto
                     , solicitacaocompra.cod_grupoempresa_destino as cod_grupoempresa
                     , solicitacaocompra.cod_empresa_destino as cod_empresa
                     , solicitacaocompra.cod_filial_destino as cod_filial
                     , sum(solicitacaocompra.qtdesolicitada * resultadocotacaoitem.preco-resultadocotacaoitem.descontototal ) vlrtotal
                     , (resultadocotacaoitem.preco-resultadocotacaoitem.descontototal) preco
                     , material.fn_busca_ultimo_preco(1,1,1,solicitacaocompra.cod_material) ultima_compra
                     , oc1.cod_fornecedor, material.cod_unidade , TO_CHAR(PP1.DATAPGTO)
                from   material.material
                     , material.aprovacaoparacompra aprovacaoparacompra
                     , material.resultadocotacaoitem resultadocotacaoitem
                     , material.solicitacaocompra
                     , material.historicoempresacompra hist_compra_gef_todas
                     , material.historicoempresacompra hist_compra_gef_logada
                     , material.itensordemcompra oc
                     , material.ordemcompra oc1, material.planopagamento pp, material.ordemcomprapagamento pp1
                where  material.cod_material = solicitacaocompra.cod_material
                and    resultadocotacaoitem.cod_material = oc.cod_material
                and    oc.nroc = oc1.nroc   and oc1.cod_plano = pp.cod_plano   and oc.nroc = pp1.nroc    and pp.Informarmanualmente = 'S'
                and    resultadocotacaoitem.nr_solicitacao = oc.nr_solicitacao
                and    aprovacaoparacompra.nr_cotacao = resultadocotacaoitem.nr_cotacao
                and    aprovacaoparacompra.cod_plano = resultadocotacaoitem.cod_plano
                and    aprovacaoparacompra.cod_material = resultadocotacaoitem.cod_material
                and    aprovacaoparacompra.nr_solicitacao = resultadocotacaoitem.nr_solicitacao
                and    resultadocotacaoitem.nr_solicitacao = solicitacaocompra.nr_solicitacao
                and    resultadocotacaoitem.cod_material = solicitacaocompra.cod_material
                and    resultadocotacaoitem.aprovadoparacompra = 'T'
                and    aprovacaoparacompra.dataaprovacao = TRUNC(SYSDATE)
                and    resultadocotacaoitem.situacao = 'A'
                and    solicitacaocompra.solicitacaoaprovada = 'T'
                and    solicitacaocompra.cod_grupoempresa_destino = hist_compra_gef_todas.cod_grupoempresa
                and    solicitacaocompra.cod_empresa_destino = hist_compra_gef_todas.cod_empresa
                and    solicitacaocompra.cod_filial_destino = hist_compra_gef_todas.cod_filial
                and    TRUNC(SYSDATE) between hist_compra_gef_todas.datainicio and nvl(hist_compra_gef_todas.datatermino, to_date('31/12/9999','DD/MM/YYYY'))
                and    hist_compra_gef_todas.cod_filial_compra = hist_compra_gef_logada.cod_filial_compra
                and    hist_compra_gef_todas.cod_empresa_compra = hist_compra_gef_logada.cod_empresa_compra
                and    hist_compra_gef_todas.cod_grupoempresa = hist_compra_gef_logada.cod_grupoempresa
                and    TRUNC(SYSDATE) between hist_compra_gef_logada.datainicio and nvl(hist_compra_gef_logada.datatermino, to_date('31/12/9999','DD/MM/YYYY'))
                and    hist_compra_gef_logada.cod_filial = 1
                and    hist_compra_gef_logada.cod_empresa = 1
                and    hist_compra_gef_logada.cod_grupoempresa = 1
                and    not exists(select 1 from material.ordemcompraimediata where ordemcompraimediata.nr_cotacao = resultadocotacaoitem.nr_cotacao)
                group by rh.fn_nomefuncionario(to_number(aprovacaoparacompra.cod_grupoempresa),to_number(aprovacaoparacompra.cod_funcionario))
                       , to_number(aprovacaoparacompra.cod_funcionario)
                       , solicitacaocompra.data
                       , to_number(resultadocotacaoitem.nr_cotacao)
                       , solicitacaocompra.cod_material
                       , solicitacaocompra.cod_funcionario
                       , rh.fn_nomefuncionario(solicitacaocompra.cod_grupoempresa_destino,solicitacaocompra.cod_funcionario)
                       , solicitacaocompra.qtdesolicitada
                       , to_date(aprovacaoparacompra.dataaprovacao)
                       , material.descricao
                       , solicitacaocompra.nr_solicitacao
                       , solicitacaocompra.cod_almoxarifado
                       , solicitacaocompra.cod_objetocusto
                       , solicitacaocompra.cod_grupoempresa_destino
                       , solicitacaocompra.cod_empresa_destino
                       , solicitacaocompra.cod_filial_destino
                       , (resultadocotacaoitem.preco-resultadocotacaoitem.descontototal)
                       , oc.nroc
                       , oc1.cod_fornecedor, material.cod_unidade, PP1.DATAPGTO
            ) tmp
        ) resultado
        GROUP BY APROVADOR, COD_APROVADOR, TIPO, DATA_PROCESSO, NUMERO_PROCESSO, COD_ETAPA,
                 COD_MATERIAL, COD_REQUISITANTE, REQUISITANTE, QTDESOLICITADA, DATA_APROVACAO,
                 DESC_MATERIAL, NR_SOLICITACAO, VLRTOTAL, ITEM, COD_ALMOXARIFADO, DESC_ALMOXARIFADO,
                 DESC_OBJETOCUSTO, PRECO, ULTIMA_COMPRA, COD_FORNECEDOR, NOME_FORNECEDOR,
                 VARIACAO_PERCENTUAL, COD_UNIDADE
          ) item_unico
        )
        WHERE VLR_TOTAL_POR_NUMERO_PROCESSO > ?
        ORDER BY aprovador, tipo, numero_processo, item, data_processo
        """;

    /**
     * Itens das ordens aprovadas hoje cujo total da ordem passa do limite.
     * Uma linha por item; quem consome agrupa por numero_processo.
     *
     * @param valorMinimo total mínimo da ordem para entrar no alerta
     */
    public List<Map<String, Object>> buscarAprovadasAcimaDe(double valorMinimo) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            ps.setDouble(1, valorMinimo);

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar ordens de compra aprovadas acima de "
                    + valorMinimo + ": " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de ordens de compra aprovadas: " + e.getMessage(), e);
        }
    }
}
