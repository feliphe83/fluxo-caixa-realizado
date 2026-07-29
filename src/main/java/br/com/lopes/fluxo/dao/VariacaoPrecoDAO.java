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
 * Itens de cotação liberados HOJE cujo preço unitário subiu acima de um
 * limite em relação à última entrada do material — já descontada a inflação
 * do período.
 *
 * A comparação não é com o preço nominal da última compra: o valor antigo é
 * corrigido pelos juros compostos do índice financeiro 17 acumulados entre a
 * data da última entrada e a data de liberação da cotação
 * (financeiro.bancoindice). Só o que sobra depois dessa correção conta como
 * aumento real.
 *
 * A consulta é a mesma que rodava fora do sistema, mantida fiel — só o
 * id_logon do aprovador e o percentual mínimo viraram parâmetro. O código de
 * formulário segue fixo (7871), como na tela de aprovação de cotação.
 *
 * Cada linha é um ITEM (cotação + material) e vira uma mensagem.
 *
 * Colunas usadas na mensagem: nr_cotacao, cod_material, descricao,
 * preco_unitario, quantidade, nome (fornecedor atual), qtade2 e
 * data_ultima_compra e razao_social_ultima_compra (da última entrada),
 * variacao, observacaoparaaprovador.
 */
public class VariacaoPrecoDAO {

    private static final Logger LOG = Logger.getLogger(VariacaoPrecoDAO.class.getName());

    /**
     * Binds, nesta ordem: id_logon do aprovador e percentual mínimo de
     * variação — ver {@link #buscarVariacoes(int, double)}.
     *
     * A consulta original trazia também um "possui_aprovacao_intermediaria"
     * com o id_logon chumbado; era coluna morta (nenhum nível de fora a
     * seleciona) e ficou de lado, junto com o código fixo que ela carregava.
     * A tabela material.aprovacaoparacompraintermed continua no FROM com os
     * mesmos outer joins, porque essa parte influencia as linhas.
     */
    private static final String SQL = """
        WITH DadosMaterial AS (
            SELECT tmp2.*
            FROM (
                SELECT data_liberacao,
                       cod_unidade,
                       cod_objetocusto,
                       nr_cotacao,
                       cod_material,
                       descricao,
                       (VRLIQUIDO / QUANTIDADE) AS preco_unitario,
                       quantidade,
                       nome,
                       valorunitario2,
                       qtade2,
                       data_ultima_compra,
                       razao_social_ultima_compra,
                       ((preco_unitario/valorunitario2)-1)*100 AS variacao_sem_correcao,
                       observacaoparaaprovador
                FROM (
                    SELECT tmp.nr_cotacao,
                           (SELECT material_ultimaentrada.valor_unitario
                            FROM material.material_ultimaentrada, material.material mm
                            WHERE material_ultimaentrada.cod_material = tmp.cod_material
                              AND material_ultimaentrada.cod_material = mm.cod_material
                              AND material_ultimaentrada.cod_filial = 1
                              AND material_ultimaentrada.cod_empresa = 1
                              AND material_ultimaentrada.cod_grupoempresa = 1) AS valorunitario2,
                           (SELECT material_ultimaentrada.qtde_compra
                            FROM material.material_ultimaentrada, material.material mm
                            WHERE material_ultimaentrada.cod_material = tmp.cod_material
                              AND material_ultimaentrada.cod_material = mm.cod_material
                              AND material_ultimaentrada.cod_filial = 1
                              AND material_ultimaentrada.cod_empresa = 1
                              AND material_ultimaentrada.cod_grupoempresa = 1) AS qtade2,
                           (SELECT material_ultimaentrada.data_entrada
                            FROM material.material_ultimaentrada, material.material mm
                            WHERE material_ultimaentrada.cod_material = tmp.cod_material
                              AND material_ultimaentrada.cod_material = mm.cod_material
                              AND material_ultimaentrada.cod_filial = 1
                              AND material_ultimaentrada.cod_empresa = 1
                              AND material_ultimaentrada.cod_grupoempresa = 1) AS data_ultima_compra,
                           (SELECT material_ultimaentrada.razao_social
                            FROM material.material_ultimaentrada, material.material mm
                            WHERE material_ultimaentrada.cod_material = tmp.cod_material
                              AND material_ultimaentrada.cod_material = mm.cod_material
                              AND material_ultimaentrada.cod_filial = 1
                              AND material_ultimaentrada.cod_empresa = 1
                              AND material_ultimaentrada.cod_grupoempresa = 1) AS razao_social_ultima_compra,
                           tmp.cod_material,
                           tmp.nr_solicitacao,
                           tmp.precototal,
                           tmp.preco_unitario,
                           tmp.vrliquido,
                           tmp.vrfinal,
                           tmp.preco,
                           tmp.precoparaxqtde,
                           tmp.descontototal,
                           tmp.descontoporunidade,
                           tmp.descontoporcentagem,
                           tmp.quantidade,
                           tmp.qtde_aprovada,
                           tmp.cod_grupoempresa || '-' || tmp.cod_empresa || '-' || tmp.cod_filial AS GEF,
                           tmp.descricao,
                           tmp.cod_unidade,
                           tmp.justificativa,
                           tmp.informacao_fornecedor,
                           tmp.comprador,
                           tmp.nome,
                           tmp.nomefantasia,
                           tmp.cod_fornecedor,
                           rownum AS num_linha,
                           tmp.data_liberacao,
                           tmp.cod_objetocusto,
                           tmp.descontoitem,
                           tmp.tipoprazoentrega,
                           tmp.atualiza_proximo_orcamento,
                           SUM(tmp.vrfinal) OVER() AS valor_total,
                           SUM(tmp.valor_totalprodutos) OVER() AS valor_totalprodutos,
                           SUM(tmp.descontoitem) OVER() AS total_descontoitem,
                           DECODE(NVL(tmp.quantidade,0), 0, 0, material.F_VERIFICAPORCAUMENTO(material.fn_verificaprecoultentrada(tmp.cod_grupoempresa, tmp.cod_empresa, tmp.cod_filial, tmp.cod_material, SYSDATE, 'V') , tmp.vrLiquido / tmp.Quantidade)) AS PORC,
                           observacaoparaaprovador
                    FROM ( select resultadocotacaoitem.nr_cotacao
                                , resultadocotacaoitem.cod_plano
                                , resultadocotacaoitem.cod_material
                                , resultadocotacaoitem.nr_solicitacao
                                , ((resultadocotacaoitem.preco/resultadocotacaoitem.precoparaxqtde) * cotacaoitem.qtde_aprovada) precototal
                                , (resultadocotacaoitem.preco/resultadocotacaoitem.precoparaxqtde) preco_unitario
                                , material.fn_busca_valoritem('CO','A',resultadocotacaoitem.cod_material,resultadocotacaoitem.nr_solicitacao,0,resultadocotacaoitem.cod_plano,resultadocotacaoitem.nr_cotacao,'N','S','N','U') vrliquido
                                , material.fn_busca_valoritem('CO','A',resultadocotacaoitem.cod_material,resultadocotacaoitem.nr_solicitacao,0,resultadocotacaoitem.cod_plano,resultadocotacaoitem.nr_cotacao,'S','S','S','U') vrfinal
                                , material.fn_busca_valoritem('CO','A',resultadocotacaoitem.cod_material,resultadocotacaoitem.nr_solicitacao,0,resultadocotacaoitem.cod_plano,resultadocotacaoitem.nr_cotacao,'S','S','S','IPI') valoripi
                                , material.fn_busca_valoritem('CO','A',resultadocotacaoitem.cod_material,resultadocotacaoitem.nr_solicitacao,0,resultadocotacaoitem.cod_plano,resultadocotacaoitem.nr_cotacao,'S','S','S','ST') vrst
                                , material.fn_busca_valoritem('CO','A',resultadocotacaoitem.cod_material,resultadocotacaoitem.nr_solicitacao,0,resultadocotacaoitem.cod_plano,resultadocotacaoitem.nr_cotacao,'S','S','S','DES') descontoitem
                                , material.fn_busca_valoritem('CO','A',resultadocotacaoitem.cod_material,resultadocotacaoitem.nr_solicitacao,0,resultadocotacaoitem.cod_plano,resultadocotacaoitem.nr_cotacao,'S','S','S','FRT') freteitem
                                , resultadocotacaoitem.preco
                                , resultadocotacaoitem.precoparaxqtde
                                , resultadocotacaoitem.nr_calculo
                                , resultadocotacaoitem.descontototal
                                , resultadocotacaoitem.descontoporunidade
                                , resultadocotacaoitem.descontoporcentagem
                                , resultadocotacaoitem.vrdesc_rateio_ind
                                , resultadocotacaoitem.calculoefetuado
                                , cotacaoitem.quantidade
                                , cotacaoitem.qtde_aprovada
                                , cotacao.cod_grupoempresa
                                , cotacao.cod_empresa
                                , cotacao.cod_filial
                                , material.fn_caracteres_especiais_html(pv_string => material.descricao) descricao
                                , material.cod_unidade
                                , solicitacaocompra.observacao justificativa
                                , solicitacaocompra.informacao_fornecedor
                                , rh.fn_nomefuncionario(cotacao.cod_grupoempresa, cotacao.cod_funcionario) comprador
                                , geral.fn_busca_nomefantasia(cotacao.cod_fornecedor,'RS') nome
                                , geral.fn_busca_nomefantasia(cotacao.cod_fornecedor,'NF') nomefantasia
                                , cotacao.cod_fornecedor
                                , resultadocotacaoitem.data_liberacao
                                , decode( nvl(solicitacaocompra.cod_objetocusto,0), 0
                                        , material.fn_obterobjcustosolicitacao(cotacao.nr_cotacao, cotacaoitem.cod_material)
                                        , solicitacaocompra.cod_objetocusto) cod_objetocusto
                                , historicogrupomaterial.cod_empenho
                                , material.cod_familia
                                , material.cod_grupomaterial
                                , decode(resultadocotacaoitem.tipoprazoentrega,'DC','Dias Corridos','Dias Úteis') tipoprazoentrega
                                , (cotacaoitem.qtde_aprovada * (resultadocotacaoitem.preco / resultadocotacaoitem.precoparaxqtde)) valor_totalprodutos
                                , nvl(solicitacaocompra.atualiza_proximo_orcamento,'N') atualiza_proximo_orcamento
                                , material.fn_busca_obs_cotacao(resultadocotacaoitem.nr_cotacao, resultadocotacaoitem.cod_plano, resultadocotacaoitem.cod_material, resultadocotacaoitem.nr_solicitacao) observacaoparaaprovador_concat
                                , case when resultadocotacaoitem.data_indicefinanceiro is not null and nvl(resultadocotacaoitem.cod_indicefinanceiro,0) > 0 then
                                            (resultadocotacaoitem.preco / resultadocotacaoitem.precoparaxqtde) / financeiro.busca_indicefinanceiro(resultadocotacaoitem.cod_indicefinanceiro, resultadocotacaoitem.data_indicefinanceiro)
                                       else null
                                  end vlr_cota_indice
                                , rh.fn_nomefuncionario(solicitacaocompra.cod_grupoempresa, solicitacaocompra.cod_funcionario) solicitante
                                , material.fn_caracteres_especiais_html(pv_string => material.fn_busca_negocio_objcusto(0,0,0, solicitacaocompra.cod_objetocusto)) descricao_negocio
                                , resultadocotacaoitem.observacaoparaaprovador observacaoparaaprovador
                           from   financeiro.indicefinanceiro
                                , rh.objetocusto
                                , material.grupomaterial
                                , material.familiamaterial
                                , material.historicogrupomaterial
                                , financeiro.tipocobranca
                                , material.grupocotacao
                                , material.fornecedor
                                , material.cotacaoitem
                                , material.materialporfilial
                                , material.parametrosmaterial
                                , material.solicitacaocompra
                                , material.planopagamento
                                , material.material
                                , material.resultadocotacao
                                , material.resultadocotacaoitem resultadocotacaoitem
                                , material.cotacao
                                , material.historicoempresacompra
                                , material.contratosuprimento_cotacao
                                , ctb.historicoempresactb
                                , material.aprovacaoparacompraintermed
                           where  contratosuprimento_cotacao.nr_solicitacao    (+)= solicitacaocompra.nr_solicitacao
                           and    indicefinanceiro.cod_indicefinanceiro        (+)= resultadocotacaoitem.cod_indicefinanceiro
                           and    objetocusto.cod_objetocusto                  (+)= solicitacaocompra.cod_objetocusto
                           and    grupomaterial.cod_grupomaterial                 = material.cod_grupomaterial
                           and    grupomaterial.cod_familia                       = material.cod_familia
                           and    familiamaterial.cod_familia                     = material.cod_familia
                           and    aprovacaoparacompraintermed.nr_cotacao     (+)  = resultadocotacaoitem.nr_cotacao
                           and    aprovacaoparacompraintermed.cod_plano      (+)  = resultadocotacaoitem.cod_plano
                           and    aprovacaoparacompraintermed.cod_material   (+)  = resultadocotacaoitem.cod_material
                           and    aprovacaoparacompraintermed.nr_solicitacao (+)  = resultadocotacaoitem.nr_solicitacao
                           and    trunc(sysdate)                                  between historicogrupomaterial.datainicio and nvl(historicogrupomaterial.datatermino,to_date('31/12/2999','DD/MM/RRRR'))
                           and    historicogrupomaterial.cod_subgrupo             = material.cod_subgrupo
                           and    historicogrupomaterial.cod_grupomaterial        = material.cod_grupomaterial
                           and    historicogrupomaterial.cod_familia              = material.cod_familia
                           and    historicogrupomaterial.cod_filial               = historicoempresactb.cod_filialctb
                           and    historicogrupomaterial.cod_empresa              = historicoempresactb.cod_empresactb
                           and    historicogrupomaterial.cod_grupoempresa         = historicoempresactb.cod_grupoempresa
                           and    historicoempresactb.cod_filial                  = historicoempresacompra.cod_filial
                           and    historicoempresactb.cod_empresa                 = historicoempresacompra.cod_empresa
                           and    historicoempresactb.cod_grupoempresa            = historicoempresacompra.cod_grupoempresa
                           and    trunc(sysdate)                                  between historicoempresactb.datainicio and nvl(historicoempresactb.datatermino,to_date('31/12/2999','DD/MM/RRRR'))
                           and    tipocobranca.cod_tipocobranca                (+)= fornecedor.cod_tipocobranca
                           and    grupocotacao.cod_grupocotacao                (+)= cotacao.cod_grupocotacao
                           and    grupocotacao.cod_grupoempresa                (+)= cotacao.cod_grupoempresa
                           and    grupocotacao.cod_empresa                     (+)= cotacao.cod_empresa
                           and    grupocotacao.cod_filial                      (+)= cotacao.cod_filial
                           and    fornecedor.cod_fornecedor                       = cotacao.cod_fornecedor
                           and    cotacaoitem.nr_cotacao                          = cotacao.nr_cotacao
                           and    cotacaoitem.cod_material                        = resultadocotacaoitem.cod_material
                           and    cotacaoitem.nr_solicitacao                      = resultadocotacaoitem.nr_solicitacao
                           and    materialporfilial.cod_material                  = resultadocotacaoitem.cod_material
                           and    materialporfilial.cod_filial                    = nvl(solicitacaocompra.cod_filial_destino, historicoempresacompra.cod_filial)
                           and    materialporfilial.cod_empresa                   = nvl(solicitacaocompra.cod_empresa_destino, historicoempresacompra.cod_empresa)
                           and    materialporfilial.cod_grupoempresa              = nvl(solicitacaocompra.cod_grupoempresa_destino, historicoempresacompra.cod_grupoempresa)
                           and    materialporfilial.situacao                      = 'A'
                           and    nvl(solicitacaocompra.data,trunc(sysdate))      between materialporfilial.datainicio and nvl(materialporfilial.datatermino,to_date('31/12/2999','DD/MM/RRRR'))
                           and    parametrosmaterial.cod_filial                   = cotacao.cod_filial
                           and    parametrosmaterial.cod_empresa                  = cotacao.cod_empresa
                           and    parametrosmaterial.cod_grupoempresa             = cotacao.cod_grupoempresa
                           and    solicitacaocompra.nr_solicitacao            (+) = resultadocotacaoitem.nr_solicitacao
                           and    planopagamento.cod_plano                        = resultadocotacaoitem.cod_plano
                           and    material.cod_material                           = resultadocotacaoitem.cod_material
                           and    resultadocotacao.nr_cotacao                     = resultadocotacaoitem.nr_cotacao
                           and    resultadocotacao.cod_plano                      = resultadocotacaoitem.cod_plano
                           and    resultadocotacaoitem.nr_cotacao                 = cotacao.nr_cotacao
                           and    cotacao.cod_filial                              = historicoempresacompra.cod_filial
                           and    cotacao.cod_empresa                             = historicoempresacompra.cod_empresa
                           and    cotacao.cod_grupoempresa                        = historicoempresacompra.cod_grupoempresa
                           and    cotacao.datageracao between historicoempresacompra.datainicio and nvl(historicoempresacompra.datatermino, to_date('31/12/2999','dd/mm/rrrr'))
                           and    historicoempresacompra.cod_filial_compra        = 1
                           and    historicoempresacompra.cod_empresa_compra       = 1
                           and    historicoempresacompra.cod_grupoempresa         = 1
                           and    resultadocotacaoitem.situacao                   = 'L'
                           and    resultadocotacaoitem.sequencia                  = 1
                           and    resultadocotacaoitem.aprovadoparacompra         = 'F'
                           and    case when nvl(resultadocotacaoitem.orcamento_estourado,'N') = 'N' then 1
                                       when resultadocotacaoitem.orcamento_estourado = 'S' and
                                            (select count(1)
                                             from   material.aprovorcestouroalcadaetapa
                                             where  aprovorcestouroalcadaetapa.nr_cotacao     = resultadocotacaoitem.nr_cotacao
                                             and    aprovorcestouroalcadaetapa.cod_plano      = resultadocotacaoitem.cod_plano
                                             and    aprovorcestouroalcadaetapa.cod_material   = resultadocotacaoitem.cod_material
                                             and    aprovorcestouroalcadaetapa.nr_solicitacao = resultadocotacaoitem.nr_solicitacao) > 0 then 1
                                       else 0
                                  end = 1
                         ) tmp
                    WHERE segurancanovo.fn_verificasupervisorde(tmp.cod_grupoempresa, tmp.cod_empresa, tmp.cod_filial, ?, tmp.cod_objetocusto, tmp.cod_empenho, tmp.cod_familia, tmp.cod_grupomaterial, 7871, tmp.nr_cotacao, tmp.cod_material, tmp.nr_solicitacao) = 'T'
                ) tmp2
            ) tmp2
            WHERE TRUNC(tmp2.data_liberacao) = TRUNC(SYSDATE)
              AND tmp2.cod_unidade NOT IN ('SV')
        ),
        JurosCompostos AS (
            SELECT dm.nr_cotacao,
                   dm.cod_material,
                   dm.data_ultima_compra,
                   dm.data_liberacao,
                   EXP(SUM(LN(1 + bi.valor / 100))) - 1 AS variacao_juros_compostos
            FROM DadosMaterial dm
            JOIN financeiro.bancoindice bi
                ON bi.cod_indicefinanceiro = 17
            WHERE bi.data BETWEEN dm.data_ultima_compra AND dm.data_liberacao
            GROUP BY dm.nr_cotacao, dm.cod_material, dm.data_ultima_compra, dm.data_liberacao
        )
        SELECT dm.*,
               COALESCE(jc.variacao_juros_compostos * 100, 0) AS variacao_juros_compostos_percentual,
               (dm.preco_unitario / ((dm.valorunitario2 * COALESCE(jc.variacao_juros_compostos * 100, 0) / 100) + dm.valorunitario2)-1)*100 AS variacao
        FROM DadosMaterial dm
        LEFT JOIN JurosCompostos jc
            ON dm.nr_cotacao = jc.nr_cotacao
            AND dm.cod_material = jc.cod_material
        WHERE (dm.preco_unitario / ((dm.valorunitario2 * COALESCE(jc.variacao_juros_compostos * 100, 0) / 100) + dm.valorunitario2)-1)*100 >= ?
        """;

    /**
     * Itens liberados hoje com aumento real acima do limite, para um aprovador.
     *
     * @param idLogon        id_logon do aprovador no ERP — cadastrado em
     *                       fc_usuario.id_logon_erp
     * @param variacaoMinima percentual mínimo de aumento (já corrigido pela
     *                       inflação) para o item entrar no alerta
     */
    public List<Map<String, Object>> buscarVariacoes(int idLogon, double variacaoMinima) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            ps.setInt(1, idLogon);
            ps.setDouble(2, variacaoMinima);

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar variações de preço (idLogon=" + idLogon
                    + ", variacaoMinima=" + variacaoMinima + "): " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de variação de preço: " + e.getMessage(), e);
        }
    }
}
