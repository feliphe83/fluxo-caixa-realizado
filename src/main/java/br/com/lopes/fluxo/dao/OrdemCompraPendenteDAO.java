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
 * Ordens de compra aguardando aprovação de um determinado aprovador — as
 * normais (cotação liberada para compra) e as imediatas. Alimenta o alerta
 * de WhatsApp que roda de tempos em tempos
 * (AlertaOcPendenteScheduler), substituindo a aplicação que fazia isso num
 * agendador do Windows.
 *
 * A consulta é a mesma do ERP, mantida fiel (mesmos joins e filtros das
 * telas de aprovação) — só o id_logon do aprovador virou parâmetro, para
 * poder rodar por destinatário. Os códigos de formulário continuam fixos,
 * como nas telas originais: 7871 (aprovação de cotação) e 8297 (ordem de
 * compra imediata).
 *
 * São duas consultas, uma por etapa de aprovação, porque no ERP também são
 * (ver {@link #montarSql}): a do primeiro aprovador pega o que ainda não
 * passou pela aprovação intermediária, a do segundo pega justamente o que
 * já passou. Qual usar vem do cadastro do usuário
 * (fc_usuario.etapa_aprovacao).
 *
 * Cada linha é um ITEM: uma ordem com três materiais devolve três linhas,
 * todas com o mesmo nr_solicitacao. Quem consome agrupa por
 * tipo + nr_solicitacao para montar uma mensagem por ordem.
 *
 * Colunas devolvidas: tipo, nr_solicitacao, cod_material (já é a descrição
 * do material, não o código), cod_unidade, preco_unitario, precototal,
 * quantidade, nome (fornecedor), observacao, desc_objetocusto.
 *
 * Na ordem de compra IMEDIATA, preco_unitario vinha igual a precototal (o
 * total do item, de fn_calcula_valoritem, sem dividir pela quantidade) —
 * corrigido para preco_total/quantidade, senão "Preço Unitário × Quantidade"
 * na mensagem do alerta não batia com o "Total". A ordem de compra normal
 * (cotação) já calculava o unitário corretamente e não foi alterada.
 *
 * Na ordem de compra NORMAL (cotação), preco_unitario/precototal vinham só
 * de preco/precoparaxqtde, sem descontar vrdesc_rateio_ind — a coluna já era
 * lida (resultadocotacaoitem.vrdesc_rateio_ind) mas ficava sem uso na conta.
 * Na imediata isso não acontece porque ali o total passa inteiro por
 * fn_calcula_valoritem, que já recebe o rateio equivalente
 * (itensordemcompraimediata.vrdesc_rateio) como parâmetro.
 */
public class OrdemCompraPendenteDAO {

    private static final Logger LOG = Logger.getLogger(OrdemCompraPendenteDAO.class.getName());

    /** Primeiro aprovador — o padrão de quem não tem etapa marcada no cadastro. */
    public static final int ETAPA_PRIMEIRO_APROVADOR = 1;
    /** Segundo aprovador — vê o que já passou pela aprovação intermediária. */
    public static final int ETAPA_SEGUNDO_APROVADOR = 2;

    /**
     * Os dois "?" são o mesmo id_logon (um em cada metade da união) — ver
     * {@link #buscarPendentes(int, int)}. Os dois comentários /*FILTRO...*&#47;
     * são substituídos por {@link #montarSql} conforme a etapa.
     */
    private static final String SQL = """
        select 'ORDEM DE COMPRA' tipo, nr_solicitacao, cod_material, cod_unidade, preco_unitario,
               precototal, quantidade, nome, observacao, desc_objetocusto
        from (
        SELECT nr_solicitacao, material.fn_buscadescmaterial(cod_material,sysdate) cod_material, cod_unidade, preco_unitario,
               precototal, quantidade, nome, justificativa observacao, desc_objetocusto, possui_aprovacao_intermediaria
        FROM (
            select tmp.nr_cotacao, tmp.cod_plano, tmp.cod_material,
                   tmp.nr_solicitacao, tmp.precototal, tmp.preco_unitario,
                   tmp.vrliquido, tmp.vrfinal, tmp.valoripi, tmp.vrst,
                   tmp.preco, tmp.perc_ipi, tmp.aliquota_icms_st,
                   tmp.base_icms_st, tmp.precoparaxqtde, tmp.nr_calculo,
                   tmp.prazoentrega, tmp.dataentrega, tmp.observacao,
                   tmp.observacaoparaaprovador, tmp.descontototal,
                   tmp.descontoporunidade, tmp.descontoporcentagem,
                   tmp.vrdesc_rateio_ind, tmp.calculoefetuado, tmp.quantidade,
                   tmp.qtde_aprovada, tmp.cod_grupoempresa || '-' || tmp.cod_empresa || '-' || tmp.cod_filial GEF,
                   tmp.cod_unidade, tmp.justificativa, tmp.informacao_fornecedor,
                   tmp.datautilizacaoprevista, tmp.data_solicitacaocompra,
                   tmp.descricaoplano, tmp.cod_funcionario, tmp.comprador,
                   tmp.nome, tmp.nomefantasia, tmp.cod_fornecedor,
                   tmp.prioridade, tmp.orcamento_estourado,
                   tmp.especificacaotecnica, tmp.observacaoplanodepagamento,
                   tmp.observacaoprazodeentrega, tmp.data_validade,
                   tmp.descricaotipocobranca, tmp.cod_grupocotacao,
                   tmp.atualiza_aprovacao, tmp.datarecebimento,
                   tmp.datarecebimento_trunc, tmp.descricao_grupocotacao,
                   rownum num_linha, tmp.data_liberacao, tmp.cod_objetocusto,
                   tmp.cod_empenho, tmp.cod_familia, tmp.cod_grupomaterial,
                   tmp.justificativa_aprovacao, tmp.justif_cancelamento_compra,
                   tmp.desc_familia, tmp.desc_grupomaterial, tmp.desc_objetocusto,
                   tmp.tipoprazoentrega, tmp.atualiza_proximo_orcamento,
                   sum(tmp.vrfinal) over() valor_total,
                   sum(tmp.valor_totalprodutos) over() valor_totalprodutos,
                   sum(tmp.valoripi) over() valor_totalipi,
                   sum(tmp.vrst) over() valor_totalst,
                   sum(tmp.descontoitem) over() total_descontoitem,
                   sum(tmp.freteitem) over() total_freteitem,
                   tmp.observacaoparaaprovador_concat, tmp.contratosuprimento,
                   tmp.cod_indicefinanceiro, tmp.desc_indice_financeiro,
                   tmp.data_indicefinanceiro, tmp.vlr_cota_indice,
                   tmp.solicitante,
                   decode(nvl(tmp.quantidade,0), 0, 0, material.F_VERIFICAPORCAUMENTO(material.fn_verificaprecoultentrada(tmp.cod_grupoempresa, tmp.cod_empresa, tmp.cod_filial, tmp.cod_material, sysdate, 'V') , tmp.vrLiquido / tmp.Quantidade)) PORC,
                   tmp.compraunificada, tmp.tabelapreco, tmp.numero_contrato,
                   tmp.gerado_prazo_val_preco, tmp.gerado_segregacao,
                   tmp.possui_aprovacao_intermediaria
            from (
                select tmp.* from (
                    select resultadocotacaoitem.nr_cotacao, resultadocotacaoitem.cod_plano,
                           resultadocotacaoitem.cod_material, resultadocotacaoitem.nr_solicitacao,
                           (((resultadocotacaoitem.preco - resultadocotacaoitem.vrdesc_rateio_ind)/resultadocotacaoitem.precoparaxqtde) * cotacaoitem.qtde_aprovada) precototal,
                           ((resultadocotacaoitem.preco - resultadocotacaoitem.vrdesc_rateio_ind)/resultadocotacaoitem.precoparaxqtde) preco_unitario,
                           material.fn_busca_valoritem('CO','A',resultadocotacaoitem.cod_material,resultadocotacaoitem.nr_solicitacao,0,resultadocotacaoitem.cod_plano,resultadocotacaoitem.nr_cotacao,'N','S','N','U') vrliquido,
                           material.fn_busca_valoritem('CO','A',resultadocotacaoitem.cod_material,resultadocotacaoitem.nr_solicitacao,0,resultadocotacaoitem.cod_plano,resultadocotacaoitem.nr_cotacao,'S','S','S','U') vrfinal,
                           material.fn_busca_valoritem('CO','A',resultadocotacaoitem.cod_material,resultadocotacaoitem.nr_solicitacao,0,resultadocotacaoitem.cod_plano,resultadocotacaoitem.nr_cotacao,'S','S','S','IPI') valoripi,
                           material.fn_busca_valoritem('CO','A',resultadocotacaoitem.cod_material,resultadocotacaoitem.nr_solicitacao,0,resultadocotacaoitem.cod_plano,resultadocotacaoitem.nr_cotacao,'S','S','S','ST') vrst,
                           material.fn_busca_valoritem('CO','A',resultadocotacaoitem.cod_material,resultadocotacaoitem.nr_solicitacao,0,resultadocotacaoitem.cod_plano,resultadocotacaoitem.nr_cotacao,'S','S','S','DES') descontoitem,
                           material.fn_busca_valoritem('CO','A',resultadocotacaoitem.cod_material,resultadocotacaoitem.nr_solicitacao,0,resultadocotacaoitem.cod_plano,resultadocotacaoitem.nr_cotacao,'S','S','S','FRT') freteitem,
                           resultadocotacaoitem.preco, resultadocotacaoitem.perc_ipi,
                           resultadocotacaoitem.aliquota_icms_st, resultadocotacaoitem.base_icms_st,
                           resultadocotacaoitem.precoparaxqtde, resultadocotacaoitem.nr_calculo,
                           resultadocotacaoitem.prazoentrega prazoentrega, resultadocotacaoitem.dataentrega,
                           resultadocotacaoitem.observacao, resultadocotacaoitem.observacaoparaaprovador,
                           resultadocotacaoitem.descontototal, resultadocotacaoitem.descontoporunidade,
                           resultadocotacaoitem.descontoporcentagem, resultadocotacaoitem.vrdesc_rateio_ind,
                           resultadocotacaoitem.calculoefetuado, cotacaoitem.quantidade,
                           cotacaoitem.qtde_aprovada, cotacao.cod_grupoempresa,
                           cotacao.cod_empresa, cotacao.cod_filial, material.cod_unidade,
                           solicitacaocompra.observacao justificativa, solicitacaocompra.informacao_fornecedor,
                           solicitacaocompra.datautilizacaoprevista, solicitacaocompra.data data_solicitacaocompra,
                           planopagamento.descricaoplano, cotacao.cod_funcionario,
                           rh.fn_nomefuncionario(cotacao.cod_grupoempresa, cotacao.cod_funcionario) comprador,
                           geral.fn_busca_nomefantasia(cotacao.cod_fornecedor,'RS') nome,
                           geral.fn_busca_nomefantasia(cotacao.cod_fornecedor,'NF') nomefantasia,
                           cotacao.cod_fornecedor,
                           decode(materialporfilial.curva_xyz, 'X', '(X)-Alta', 'Y', '(Y)-Média', '(Z)-Baixa') prioridade,
                           resultadocotacaoitem.orcamento_estourado, material.especificacaotecnica,
                           resultadocotacao.observacaoplanodepagamento, resultadocotacao.observacaoprazodeentrega,
                           resultadocotacao.data_validade, tipocobranca.descricaotipocobranca,
                           cotacao.cod_grupocotacao, material.atualiza_aprovacao,
                           resultadocotacao.datarecebimento, trunc(resultadocotacao.datarecebimento) datarecebimento_trunc,
                           grupocotacao.descricao descricao_grupocotacao, resultadocotacaoitem.data_liberacao,
                           decode(nvl(solicitacaocompra.cod_objetocusto,0), 0,
                           material.fn_obterobjcustosolicitacao(cotacao.nr_cotacao, cotacaoitem.cod_material),
                           solicitacaocompra.cod_objetocusto) cod_objetocusto,
                           historicogrupomaterial.cod_empenho, material.cod_familia,
                           material.cod_grupomaterial, resultadocotacaoitem.justificativa_aprovacao,
                           resultadocotacaoitem.justif_cancelamento_compra, familiamaterial.descricao desc_familia,
                           grupomaterial.descricao desc_grupomaterial, objetocusto.descricao desc_objetocusto,
                           decode(resultadocotacaoitem.tipoprazoentrega,'DC','Dias Corridos','Dias Úteis') tipoprazoentrega,
                           (cotacaoitem.qtde_aprovada * (resultadocotacaoitem.preco / resultadocotacaoitem.precoparaxqtde)) valor_totalprodutos,
                           nvl(solicitacaocompra.atualiza_proximo_orcamento,'N') atualiza_proximo_orcamento,
                           material.fn_busca_obs_cotacao(resultadocotacaoitem.nr_cotacao, resultadocotacaoitem.cod_plano,
                           resultadocotacaoitem.cod_material, resultadocotacaoitem.nr_solicitacao) observacaoparaaprovador_concat,
                           resultadocotacaoitem.contratosuprimento, resultadocotacaoitem.cod_indicefinanceiro,
                           indicefinanceiro.descricao desc_indice_financeiro, resultadocotacaoitem.data_indicefinanceiro,
                           case when resultadocotacaoitem.data_indicefinanceiro is not null and nvl(resultadocotacaoitem.cod_indicefinanceiro,0) > 0 then
                                (resultadocotacaoitem.preco / resultadocotacaoitem.precoparaxqtde) / financeiro.busca_indicefinanceiro(resultadocotacaoitem.cod_indicefinanceiro, resultadocotacaoitem.data_indicefinanceiro)
                           else null end vlr_cota_indice,
                           rh.fn_nomefuncionario(solicitacaocompra.cod_grupoempresa, solicitacaocompra.cod_funcionario) solicitante,
                           cotacao.compraunificada, resultadocotacaoitem.tabelapreco,
                           contratosuprimento_cotacao.numero_contrato, resultadocotacaoitem.gerado_prazo_val_preco,
                           resultadocotacaoitem.gerado_segregacao,
                           (select count(1) from material.aprovacaoparacompraintermed
                            where aprovacaoparacompraintermed.nr_cotacao = resultadocotacaoitem.nr_cotacao
                            and aprovacaoparacompraintermed.cod_plano = resultadocotacaoitem.cod_plano
                            and aprovacaoparacompraintermed.cod_material = resultadocotacaoitem.cod_material
                            and aprovacaoparacompraintermed.nr_solicitacao = resultadocotacaoitem.nr_solicitacao
                           ) possui_aprovacao_intermediaria
                    from financeiro.indicefinanceiro, rh.objetocusto, material.grupomaterial,
                         material.familiamaterial, material.historicogrupomaterial,
                         financeiro.tipocobranca, material.grupocotacao, material.fornecedor,
                         material.cotacaoitem, material.materialporfilial, material.parametrosmaterial,
                         material.solicitacaocompra, material.planopagamento, material.material,
                         material.resultadocotacao, material.resultadocotacaoitem resultadocotacaoitem, material.cotacao,
                         material.historicoempresacompra, material.contratosuprimento_cotacao, ctb.historicoempresactb
                    where contratosuprimento_cotacao.nr_solicitacao(+) = solicitacaocompra.nr_solicitacao
                    and indicefinanceiro.cod_indicefinanceiro(+) = resultadocotacaoitem.cod_indicefinanceiro
                    and objetocusto.cod_objetocusto(+) = solicitacaocompra.cod_objetocusto
                    and grupomaterial.cod_grupomaterial = material.cod_grupomaterial
                    and grupomaterial.cod_familia = material.cod_familia
                    and familiamaterial.cod_familia = material.cod_familia
                    and trunc(sysdate) between historicogrupomaterial.datainicio and nvl(historicogrupomaterial.datatermino,to_date('31/12/2999','DD/MM/RRRR'))
                    and historicogrupomaterial.cod_subgrupo = material.cod_subgrupo
                    and historicogrupomaterial.cod_grupomaterial = material.cod_grupomaterial
                    and historicogrupomaterial.cod_familia = material.cod_familia
                    and historicogrupomaterial.cod_filial = historicoempresactb.cod_filialctb
                    and historicogrupomaterial.cod_empresa = historicoempresactb.cod_empresactb
                    and historicogrupomaterial.cod_grupoempresa = historicoempresactb.cod_grupoempresa
                    and historicoempresactb.cod_filial = historicoempresacompra.cod_filial
                    and historicoempresactb.cod_empresa = historicoempresacompra.cod_empresa
                    and historicoempresactb.cod_grupoempresa = historicoempresacompra.cod_grupoempresa
                    and trunc(sysdate) between historicoempresactb.datainicio and nvl(historicoempresactb.datatermino,to_date('31/12/2999','DD/MM/RRRR'))
                    and tipocobranca.cod_tipocobranca(+) = fornecedor.cod_tipocobranca
                    and grupocotacao.cod_grupocotacao(+) = cotacao.cod_grupocotacao
                    and grupocotacao.cod_grupoempresa(+) = cotacao.cod_grupoempresa
                    and grupocotacao.cod_empresa(+) = cotacao.cod_empresa
                    and grupocotacao.cod_filial(+) = cotacao.cod_filial
                    and fornecedor.cod_fornecedor = cotacao.cod_fornecedor
                    and cotacaoitem.nr_cotacao = cotacao.nr_cotacao
                    and cotacaoitem.cod_material = resultadocotacaoitem.cod_material
                    and cotacaoitem.nr_solicitacao = resultadocotacaoitem.nr_solicitacao
                    and materialporfilial.cod_material = resultadocotacaoitem.cod_material
                    and materialporfilial.cod_filial = nvl(solicitacaocompra.cod_filial_destino, historicoempresacompra.cod_filial)
                    and materialporfilial.cod_empresa = nvl(solicitacaocompra.cod_empresa_destino, historicoempresacompra.cod_empresa)
                    and materialporfilial.cod_grupoempresa = nvl(solicitacaocompra.cod_grupoempresa_destino, historicoempresacompra.cod_grupoempresa)
                    and materialporfilial.situacao = 'A'
                    and nvl(solicitacaocompra.data,trunc(sysdate)) between materialporfilial.datainicio and nvl(materialporfilial.datatermino,to_date('31/12/2999','DD/MM/RRRR'))
                    and parametrosmaterial.cod_filial = cotacao.cod_filial
                    and parametrosmaterial.cod_empresa = cotacao.cod_empresa
                    and parametrosmaterial.cod_grupoempresa = cotacao.cod_grupoempresa
                    and solicitacaocompra.nr_solicitacao(+) = resultadocotacaoitem.nr_solicitacao
                    and planopagamento.cod_plano = resultadocotacaoitem.cod_plano
                    and material.cod_material = resultadocotacaoitem.cod_material
                    and resultadocotacao.nr_cotacao = resultadocotacaoitem.nr_cotacao
                    and resultadocotacao.cod_plano = resultadocotacaoitem.cod_plano
                    and resultadocotacaoitem.nr_cotacao = cotacao.nr_cotacao
                    and cotacao.cod_filial = historicoempresacompra.cod_filial
                    and cotacao.cod_empresa = historicoempresacompra.cod_empresa
                    and cotacao.cod_grupoempresa = historicoempresacompra.cod_grupoempresa
                    and cotacao.datageracao between historicoempresacompra.datainicio and nvl(historicoempresacompra.datatermino, to_date('31/12/2999','dd/mm/rrrr'))
                    and historicoempresacompra.cod_filial_compra = 1
                    and historicoempresacompra.cod_empresa_compra = 1
                    and historicoempresacompra.cod_grupoempresa = 1
                    and resultadocotacaoitem.situacao = 'L'
                    and resultadocotacaoitem.sequencia = 1
                    and resultadocotacaoitem.aprovadoparacompra = 'F'
                    and case when nvl(resultadocotacaoitem.orcamento_estourado,'N') = 'N' then 1
                             when resultadocotacaoitem.orcamento_estourado = 'S' and
                                  (select count(1) from material.aprovorcestouroalcadaetapa
                                   where aprovorcestouroalcadaetapa.nr_cotacao = resultadocotacaoitem.nr_cotacao
                                   and aprovorcestouroalcadaetapa.cod_plano = resultadocotacaoitem.cod_plano
                                   and aprovorcestouroalcadaetapa.cod_material = resultadocotacaoitem.cod_material
                                   and aprovorcestouroalcadaetapa.nr_solicitacao = resultadocotacaoitem.nr_solicitacao) > 0 then 1
                             else 0 end = 1
                ) tmp
                where segurancanovo.fn_verificasupervisorde(
                    tmp.cod_grupoempresa, tmp.cod_empresa, tmp.cod_filial,
                    ?, tmp.cod_objetocusto, tmp.cod_empenho,
                    tmp.cod_familia, tmp.cod_grupomaterial, 7871,
                    tmp.nr_cotacao, tmp.cod_material, tmp.nr_solicitacao
                ) = 'T' and possui_aprovacao_intermediaria /*FILTRO_ETAPA*/
            ) tmp
        ))

        UNION ALL

        select 'ORDEM DE COMPRA IMEDIATA', nroc_imediata, material.fn_buscadescmaterial(cod_material,sysdate) cod_material,
               unidade, decode(quantidade, 0, 0, preco_total/quantidade), preco_total, quantidade, fornecedor, observacao, null objetodecusto
        from (
        select ordemcompraimediata.nroc_imediata
             , ordemcompraimediata.data_oc
             , itensordemcompraimediata.cod_material
             , (select m.cod_unidade from material.material m where m.cod_material = itensordemcompraimediata.cod_material) unidade
             , itensordemcompraimediata.quantidade
             , ordemcompraimediata.cod_funcionario
             , material.fn_buscanomefornec(ordemcompraimediata.cod_fornecedor,sysdate) fornecedor
             , ordemcompraimediata.cod_fornecedor
             , ordemcompraimediata.cod_plano
             , ordemcompraimediata.observacao
             , ordemcompraimediata.nr_cotacao
             , ordemcompraimediata.data_aprovacao
             , ordemcompraimediata.data_cancelamento
             -- fn_calcula_valoritem é cara (PL/SQL por item); das cinco somas do
             -- relatório original só o total com IPI/ST/desconto/frete é usado
             -- aqui, então as outras quatro (preco, valor_ipi, valor_icms_st,
             -- preco_total_indice) ficaram de fora — são colunas de projeção,
             -- não mudam quais linhas a consulta devolve.
             , sum(material.fn_calcula_valoritem( 'OCI'
                                                , null
                                                , itensordemcompraimediata.nroc_imediata
                                                , itensordemcompraimediata.quantidade
                                                , itensordemcompraimediata.preco
                                                , itensordemcompraimediata.precoparaqtde
                                                , 0
                                                , ordemcompraimediata.desconto_valor
                                                , ordemcompraimediata.desconto_perc
                                                , itensordemcompraimediata.descontovalor
                                                , itensordemcompraimediata.descontoporunidade
                                                , itensordemcompraimediata.descontoporcentagem
                                                , itensordemcompraimediata.vrdesc_rateio
                                                , ordemcompraimediata.vlrfretetotal
                                                , itensordemcompraimediata.vlrfretetotal
                                                , itensordemcompraimediata.vlrfreteporunidade
                                                , 0
                                                , itensordemcompraimediata.considera_frete_ipi
                                                , itensordemcompraimediata.considera_desconto_ipi
                                                , itensordemcompraimediata.vr_ipi
                                                , itensordemcompraimediata.base_ipi
                                                , itensordemcompraimediata.valor_ipi
                                                , itensordemcompraimediata.vr_icms
                                                , itensordemcompraimediata.aliquota_icms_st
                                                , itensordemcompraimediata.base_icms_st
                                                , itensordemcompraimediata.valor_icms_st
                                                , 'S'
                                                , 'S'
                                                , 'S'
                                                , 'U'
                                                , 'S'
                                                , itensordemcompraimediata.preco_indice
                                                , 'N'
                                                )) preco_total
        from   material.itensordemcompraimediata
             , material.ordemcompraimediata ordemcompraimediata
             , (select alcada.nroc_imediata
                     , alcada.cod_grupoempresa
                     , alcada.cod_empresa
                     , alcada.cod_filial
                from ( select tmp.*
                       from ( select itensordemcompraimediata.nroc_imediata
                                   , itensordemcompraimediata.cod_objetocusto
                                   , ordemcompraimediata.cod_grupoempresa
                                   , ordemcompraimediata.cod_empresa
                                   , ordemcompraimediata.cod_filial
                                   , material.cod_familia
                                   , material.cod_grupomaterial
                                   , nvl(itensordemcompraimediata.cod_solicitante,ordemcompraimediata.cod_funcionario) cod_solicitante
                              from   material.material
                                   , material.itensordemcompraimediata
                                   , material.ordemcompraimediata
                              where  material.cod_material                     = itensordemcompraimediata.cod_material
                              and    itensordemcompraimediata.codigo_cte      is null
                              and    itensordemcompraimediata.cod_equpamento  is null
                              and    itensordemcompraimediata.cod_objetocusto is not null
                              and    itensordemcompraimediata.nroc_imediata    = ordemcompraimediata.nroc_imediata
                              and    ordemcompraimediata.data_aprovacao       is null
                              and    ordemcompraimediata.data_cancelamento    is null
                              and    ordemcompraimediata.situacao              = 'L'
                              and    ordemcompraimediata.cod_grupoempresa      = 1
                              and    ordemcompraimediata.cod_empresa           = 1
                              and    ordemcompraimediata.cod_filial            = 1
                              /*FILTRO_OCI_ETAPA*/

                              union  all

                              select itensordemcompraimediata.nroc_imediata
                                   , (select historico_objetocusto_cte.cod_objetocusto
                                      from   industria.historico_objetocusto_cte
                                      where  ordemcompraimediata.data_oc between historico_objetocusto_cte.datainicio and nvl(historico_objetocusto_cte.datafim,TO_DATE('31/12/2999','DD/MM/YYYY'))
                                      and    historico_objetocusto_cte.codigo_cte       = itensordemcompraimediata.codigo_cte
                                      and    historico_objetocusto_cte.cod_grupoempresa = ordemcompraimediata.cod_grupoempresa
                                      and    historico_objetocusto_cte.cod_empresa      = ordemcompraimediata.cod_empresa
                                      and    historico_objetocusto_cte.cod_filial       = ordemcompraimediata.cod_filial
                                      and    rownum = 1
                                     ) cod_objetocusto
                                   , ordemcompraimediata.cod_grupoempresa
                                   , ordemcompraimediata.cod_empresa
                                   , ordemcompraimediata.cod_filial
                                   , material.cod_familia
                                   , material.cod_grupomaterial
                                   , nvl(itensordemcompraimediata.cod_solicitante,ordemcompraimediata.cod_funcionario) cod_solicitante
                              from   material.material
                                   , material.itensordemcompraimediata
                                   , material.ordemcompraimediata
                              where  material.cod_material                     = itensordemcompraimediata.cod_material
                              and    itensordemcompraimediata.cod_equpamento  is null
                              and    itensordemcompraimediata.cod_objetocusto is null
                              and    itensordemcompraimediata.codigo_cte      is not null
                              and    itensordemcompraimediata.nroc_imediata    = ordemcompraimediata.nroc_imediata
                              and    ordemcompraimediata.data_aprovacao       is null
                              and    ordemcompraimediata.data_cancelamento    is null
                              and    ordemcompraimediata.situacao              = 'L'
                              and    ordemcompraimediata.cod_grupoempresa      = 1
                              and    ordemcompraimediata.cod_empresa           = 1
                              and    ordemcompraimediata.cod_filial            = 1
                              /*FILTRO_OCI_ETAPA*/

                              union  all

                              select itensordemcompraimediata.nroc_imediata
                                   , (select historicoequipamentoobcusto.cod_objetocusto
                                      from   automotivo.historicoequipamentoobcusto
                                      where  ordemcompraimediata.data_oc between historicoequipamentoobcusto.data_inicio and nvl(historicoequipamentoobcusto.data_final,TO_DATE('31/12/2999','DD/MM/YYYY'))
                                      and    historicoequipamentoobcusto.cod_grupoempresa = ordemcompraimediata.cod_grupoempresa
                                      and    historicoequipamentoobcusto.cod_empresa      = ordemcompraimediata.cod_empresa
                                      and    historicoequipamentoobcusto.cod_filial       = ordemcompraimediata.cod_filial
                                      and    historicoequipamentoobcusto.cod_equipamento  = itensordemcompraimediata.cod_equpamento
                                      and    rownum = 1
                                     ) cod_objetocusto
                                   , ordemcompraimediata.cod_grupoempresa
                                   , ordemcompraimediata.cod_empresa
                                   , ordemcompraimediata.cod_filial
                                   , material.cod_familia
                                   , material.cod_grupomaterial
                                   , nvl(itensordemcompraimediata.cod_solicitante,ordemcompraimediata.cod_funcionario) cod_solicitante
                              from   material.material
                                   , material.itensordemcompraimediata
                                   , material.ordemcompraimediata
                              where  material.cod_material                     = itensordemcompraimediata.cod_material
                              and    itensordemcompraimediata.cod_objetocusto is null
                              and    itensordemcompraimediata.codigo_cte      is null
                              and    itensordemcompraimediata.cod_equpamento  is not null
                              and    itensordemcompraimediata.nroc_imediata    = ordemcompraimediata.nroc_imediata
                              and    ordemcompraimediata.data_aprovacao       is null
                              and    ordemcompraimediata.data_cancelamento    is null
                              and    ordemcompraimediata.situacao              = 'L'
                              and    ordemcompraimediata.cod_grupoempresa      = 1
                              and    ordemcompraimediata.cod_empresa           = 1
                              and    ordemcompraimediata.cod_filial            = 1
                              /*FILTRO_OCI_ETAPA*/
                            ) tmp
                       where  segurancanovo.fn_verificasupervisorde ( pn_cod_grupoempresa  => tmp.cod_grupoempresa
                                                                    , pn_cod_empresa       => tmp.cod_empresa
                                                                    , pn_cod_filial        => tmp.cod_filial
                                                                    , pn_id_logon          => ?
                                                                    , pn_cod_objetocusto   => tmp.cod_objetocusto
                                                                    , pn_cod_empenho       => 0
                                                                    , pn_cod_familia       => tmp.cod_familia
                                                                    , pn_cod_grupomaterial => tmp.cod_grupomaterial
                                                                    , pn_cod_formulario    => '8297'
                                                                    , pn_cod_funcionario   => tmp.cod_solicitante
                                                                    ) = 'T'
                     ) alcada
                group  by alcada.nroc_imediata
                        , alcada.cod_grupoempresa
                        , alcada.cod_empresa
                        , alcada.cod_filial
               ) alcada_supervisor
        where  itensordemcompraimediata.nroc_imediata = ordemcompraimediata.nroc_imediata
        and    ordemcompraimediata.data_aprovacao     is null
        and    ordemcompraimediata.data_cancelamento  is null
        and    ordemcompraimediata.situacao           = 'L'
        and    ordemcompraimediata.nroc_imediata      = alcada_supervisor.nroc_imediata
        and    ordemcompraimediata.cod_grupoempresa   = alcada_supervisor.cod_grupoempresa
        and    ordemcompraimediata.cod_empresa        = alcada_supervisor.cod_empresa
        and    ordemcompraimediata.cod_filial         = alcada_supervisor.cod_filial
        group  by ordemcompraimediata.cod_grupoempresa
                , ordemcompraimediata.cod_empresa
                , ordemcompraimediata.cod_filial
                , ordemcompraimediata.nroc_imediata
                , ordemcompraimediata.data_oc
                , ordemcompraimediata.cod_funcionario
                , ordemcompraimediata.cod_fornecedor
                , ordemcompraimediata.cod_plano
                , ordemcompraimediata.observacao
                , ordemcompraimediata.nr_cotacao
                , ordemcompraimediata.data_aprovacao
                , ordemcompraimediata.data_cancelamento
                , itensordemcompraimediata.cod_material
                , itensordemcompraimediata.quantidade
        )
        """;

    /** Já montadas no carregamento da classe: a consulta não muda em execução. */
    private static final String SQL_PRIMEIRO = montarSql(ETAPA_PRIMEIRO_APROVADOR);
    private static final String SQL_SEGUNDO  = montarSql(ETAPA_SEGUNDO_APROVADOR);

    /**
     * A consulta da etapa pedida. O que muda entre as duas:
     *
     * - ordem de compra normal: o primeiro aprovador vê o que ainda não tem
     *   aprovação intermediária ({@code < 1}), o segundo vê o que já tem
     *   ({@code >= 1}) — são conjuntos complementares;
     * - ordem de compra imediata: para o segundo aprovador entram só as que
     *   têm etapa 1 registrada em material.aprovacao_oci_intermediaria. No
     *   ERP isso é um join a mais nos três blocos da união; aqui virou um
     *   EXISTS, que dá o mesmo conjunto (o join se apoiava no GROUP BY de
     *   fora para desduplicar) sem mexer na lista de tabelas.
     *
     * Note que aqui a imediata não é complementar: uma OCI com etapa 1
     * aparece para os dois aprovadores, porque a consulta do primeiro não
     * filtra por etapa nenhuma. É assim no ERP.
     */
    private static String montarSql(int etapa) {
        boolean segundo = etapa == ETAPA_SEGUNDO_APROVADOR;
        return SQL
                .replace("/*FILTRO_ETAPA*/", segundo ? ">= 1" : "< 1")
                .replace("/*FILTRO_OCI_ETAPA*/", segundo
                        ? """
                          and exists (select 1
                                      from   material.aprovacao_oci_intermediaria o
                                      where  o.nroc_imediata  = ordemcompraimediata.nroc_imediata
                                      and    o.etapaaprovacao = 1)"""
                        : "");
    }

    /**
     * Ordens pendentes de aprovação para um aprovador.
     *
     * @param idLogon id_logon do aprovador no ERP (o mesmo das telas de
     *                aprovação) — cadastrado em fc_usuario.id_logon_erp
     * @param etapa   {@link #ETAPA_PRIMEIRO_APROVADOR} ou
     *                {@link #ETAPA_SEGUNDO_APROVADOR}
     */
    public List<Map<String, Object>> buscarPendentes(int idLogon, int etapa) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     etapa == ETAPA_SEGUNDO_APROVADOR ? SQL_SEGUNDO : SQL_PRIMEIRO)) {

            // Mesmo id_logon nas duas metades da união (OC normal e imediata).
            ps.setInt(1, idLogon);
            ps.setInt(2, idLogon);

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar ordens de compra pendentes (idLogon=" + idLogon
                    + ", etapa=" + etapa + "): " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de ordens de compra pendentes: " + e.getMessage(), e);
        }
    }
}
