package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.model.FluxoRealizadoItem;
import br.com.lopes.fluxo.util.OracleConnectionUtil;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DAO responsável por executar a query de Fluxo de Caixa Realizado no Oracle.
 *
 * A query recebe dois parâmetros bind: DATA_INI e DATA_FIM (java.sql.Date).
 * O alias &DATA_INI / &DATA_FIM do SQL original é substituído por ? (JDBC).
 */
public class FluxoRealizadoDAO {

    private static final Logger LOG = Logger.getLogger(FluxoRealizadoDAO.class.getName());

    // ─── SQL principal ────────────────────────────────────────────────────
    // Idêntica à query fornecida, com &DATA_INI → ? e &DATA_FIM → ?
    // Os dois parâmetros aparecem 4 vezes no SQL original:
    //   pos 1,2 → UNION ALL bloco 1  (between &DATA_INI and &DATA_FIM)
    //   pos 3   → (nvl(movimentobancario.datavencimento,...) < &DATA_FIM)
    //   pos 4,5 → UNION ALL bloco 1  segundo between
    //   pos 6,7 → UNION ALL bloco 2  between
    // Ordem dos binds: veja bindParameters() abaixo.
    private static final String SQL = """
        SELECT TO_NUMBER(cod_contafluxo), descricaoconta, cod_empenho,
               desc_empenho, cod_fornecedor, nome, cod_tipocontaspagar,
               descricao_tipodecontaspagar, dataentrada, datapgto,
               datavcto_orig, datavcto, data_criacao, usuario, PAGARRECEBER,
               documento, parcela,
               SUM(valorpgto) REALIZADO
        FROM (
            /* ── BLOCO 1: baixas concluídas (parcelabaixada = 'S') ── */
            SELECT parcelascontaspagar.cod_fornecedor,
                   parcelascontaspagar.cpf,
                   parcelascontaspagar.usuario,
                   parcelascontaspagar.datavcto,
                   parcelascontaspagar.data_criacao,
                   parcelascontaspagar.documento,
                   parcelascontaspagar.datavcto_orig,
                   parcelascontaspagar.parcela,
                   parcelascontaspagar.dataentrada,
                   parcelascontaspagar.valorparcela                   valorbrutoparcela,
                   0                                                  valorparcela,
                   SUM(baixaparcelas.valorbaixa)                      valorpgto,
                   baixaparcelas.databaixa                            datapgto,
                   INITCAP(geral.fn_obtem_nome(parcelascontaspagar.cod_fornecedor,
                                               parcelascontaspagar.cpf))          nome,
                   DECODE(parcelascontaspagar.datapgto, NULL, 0,
                          movimentobancario.documento)                            cheque,
                   parcelascontaspagar.cod_indicefinanceiro,
                   parcelascontaspagar.cod_tipocontaspagar,
                   parcelascontaspagar.pagarreceber,
                   parcelascontaspagar.cod_tipocobranca,
                   parcelascontaspagar.cod_empenho,
                   empenho.descricao                                 desc_empenho,
                   tipocontaspagar.descricao                         descricao_tipodecontaspagar,
                   tipocobranca.descricaotipocobranca,
                   parcelascontaspagar.OBSERVACAO||' '||
                     parcelascontaspagar.observacao_complementar      OBSERVACAO,
                   DECODE(parcelascontaspagar.cod_indiceFinanceiro,
                          parametro_financeiro.cod_indicefinanceiropadrao, NULL,
                          baixaparcelas.data_indicefinanceiro)        DataIndice,
                   parcelascontaspagar.cod_grupoempresa,
                   parcelascontaspagar.cod_empresa,
                   parcelascontaspagar.cod_filial,
                   baixaparcelas.sequenciabaixa,
                   parcelascontaspagar.origem,
                   parcelascontaspagar.cod_situacao,
                   situacaoparcelascontaspagar.descricao              des_situacao,
                   parcelascontaspagar.cod_tipopagamento,
                   tipopagamento.descricao                           des_tipopagamento,
                   tipofornecedor.cod_tipofornecedor,
                   tipofornecedor.descricao                          desc_tipofornecedor,
                   parcelascontaspagar.numero_integracao,
                   cc.nivel1, cc.nivel1_desc, cc.nivel2, cc.nivel2_desc,
                   TO_NUMBER(flc.cod_contafluxo)                     cod_contafluxo,
                   flc.descricaoconta
            FROM   financeiro.tipopagamento,
                   financeiro.situacaoparcelascontaspagar,
                   custo.empenho                                      empenho,
                   financeiro.tipocobranca,
                   material.tipofornecedor                            tipofornecedor,
                   financeiro.tipocontaspagar                         tipocontaspagar,
                   rh.contabancaria,
                   financeiro.movimentobancario,
                   financeiro.baixaparcelas,
                   financeiro.parametro_financeiro,
                   financeiro.parcelascontaspagar                     parcelascontaspagar,
                   financeiro.fluxocontabancaria,
                   financeiro.fluxoempenho                            fl,
                   financeiro.fluxoconta                              flc,
                   conta_fluxo                                        cc
            WHERE  'N'                                       = 'N'
            AND    empenho.cod_empenho                       = fl.cod_empenho
            AND    fl.cod_planofluxo                         = 16
            AND    fl.cod_contafluxo                         = flc.cod_contafluxo
            AND    fl.cod_planofluxo                         = flc.cod_planofluxo
            AND    flc.cod_contafluxo                        = cc.cod_conta
            AND    'RR'                                      = 'RR'
            AND   (movimentobancario.valor = 0 OR 'N' = 'N' OR
                  ('N' = 'S' AND NVL(movimentobancario.datavencimento,
                                     baixaparcelas.databaixa) < ?))
            AND    tipopagamento.cod_tipopagamento           (+) = parcelascontaspagar.cod_tipopagamento
            AND    NVL(situacaoparcelascontaspagar.visualizar_parcela,'S') = 'S'
            AND    situacaoparcelascontaspagar.cod_situacao              = parcelascontaspagar.cod_situacao
            AND   (baixaparcelas.databaixa BETWEEN ? AND ?)
            AND    parametro_financeiro.cod_grupoEmpresa                = parcelascontaspagar.cod_grupoempresa
            AND    parametro_financeiro.cod_empresa                     = 1
            AND    parametro_financeiro.Cod_Filial                      = 1
            AND    parcelascontaspagar.provisao                        <> 'S'
            AND    parcelascontaspagar.parcelabaixada                   = 'S'
            AND    parcelascontaspagar.valorparcela                    <> 0
            AND    empenho.cod_empenho                       (+)        = parcelascontaspagar.cod_empenho
            AND    tipocobranca.cod_tipocobranca             (+)        = parcelascontaspagar.cod_tipocobranca
            AND    parcelascontaspagar.cod_tipocontaspagar NOT IN (901, 922)
            AND    tipofornecedor.cod_tipofornecedor         (+)        = tipocontaspagar.cod_tipofornecedor
            AND    rh.intersecao(parcelascontaspagar.dataentrada,
                                 parcelascontaspagar.dataentrada,
                                 tipocontaspagar.datainicio,
                                 tipocontaspagar.datafim)              = 'TRUE'
            AND    tipocontaspagar.cod_tipocontaspagar                  = parcelascontaspagar.cod_tipocontaspagar
            AND    tipocontaspagar.cod_filial                           = parcelascontaspagar.cod_filial
            AND    tipocontaspagar.cod_empresa                          = parcelascontaspagar.cod_empresa
            AND    tipocontaspagar.cod_grupoempresa                     = parcelascontaspagar.cod_grupoempresa
            AND    contabancaria.cod_tipocontabancaria                  = movimentobancario.cod_tipocontabancaria
            AND    contabancaria.cod_contabancaria                      = movimentobancario.cod_contabancaria
            AND    contabancaria.cod_agencia                            = movimentobancario.cod_agencia
            AND    contabancaria.cod_banco                              = movimentobancario.cod_banco
            AND    contabancaria.cod_tipocontabancaria                  = fluxocontabancaria.cod_tipocontabancaria
            AND    contabancaria.cod_contabancaria                      = fluxocontabancaria.cod_contabancaria
            AND    contabancaria.cod_agencia                            = fluxocontabancaria.cod_agencia
            AND    contabancaria.cod_banco                              = fluxocontabancaria.cod_banco
            AND    fluxocontabancaria.cod_planofluxo                    = '16'
            AND    PARCELASCONTASPAGAR.PAGARRECEBER                     = 'P'
            AND   (movimentobancario.cod_banco           = 0 OR 0 = 0)
            AND   (movimentobancario.cod_agencia         = 0 OR 0 = 0)
            AND   (movimentobancario.cod_contabancaria   = 0 OR 0 = 0)
            AND   (movimentobancario.cod_tipocontabancaria = 0 OR 0 = 0)
            AND  ((movimentobancario.valor > 0 AND 'N' = 'N') OR 'N' = 'S')
            AND    movimentobancario.num_lancamentobancario             = baixaparcelas.num_lancamentobancario
            AND    movimentobancario.cod_grupoempresa                   = baixaparcelas.cod_grupoempresa
            AND   (baixaparcelas.num_lancamentobancario IS NOT NULL
                   AND baixaparcelas.num_lancamentobancario > 0)
            AND    baixaparcelas.parcela             = parcelascontaspagar.parcela
            AND    baixaparcelas.documento           = parcelascontaspagar.documento
            AND    baixaparcelas.cod_tipocontaspagar = parcelascontaspagar.cod_tipocontaspagar
            AND    baixaparcelas.cod_grupoempresa    = parcelascontaspagar.cod_grupoempresa
            AND    NVL(PARCELASCONTASPAGAR.COD_TIPOCOBRANCA, 0) <> 99
            AND    PARCELASCONTASPAGAR.documento NOT IN (206473, 206474)
            AND    EXISTS (
                       SELECT 1
                       FROM   financeiro.movimentobancario,
                              rh.contabancaria
                       WHERE  movimentobancario.cod_grupoempresa       = baixaparcelas.cod_grupoempresa
                       AND    movimentobancario.num_lancamentobancario = baixaparcelas.num_lancamentobancario
                       AND    movimentobancario.cod_banco              = contabancaria.cod_banco
                       AND    movimentobancario.cod_agencia            = contabancaria.cod_agencia
                       AND    movimentobancario.cod_contabancaria      = contabancaria.cod_contabancaria
                       AND    movimentobancario.cod_tipocontabancaria  = contabancaria.cod_tipocontabancaria
                       AND    movimentobancario.cod_grupoempresa       = contabancaria.cod_grupoempresa
                       AND    contabancaria.cod_empresa                = parcelascontaspagar.cod_empresa
                       AND    contabancaria.cod_filial                 = parcelascontaspagar.cod_filial
                   )
            AND   (baixaparcelas.databaixa BETWEEN ? AND ?)
            AND    tipocontaspagar.imprimefluxocaixa = 'S'
            GROUP BY parcelascontaspagar.cod_fornecedor,
                     parcelascontaspagar.cpf, parcelascontaspagar.datavcto,
                     parcelascontaspagar.documento, parcelascontaspagar.parcela,
                     parcelascontaspagar.dataentrada, parcelascontaspagar.valorparcela,
                     (parcelascontaspagar.valorparcela *
                       financeiro.busca_indicefinanceiro(
                           parcelascontaspagar.cod_indicefinanceiro,
                           NVL(baixaparcelas.data_indicefinanceiro, baixaparcelas.databaixa))),
                     baixaparcelas.databaixa,
                     INITCAP(geral.fn_obtem_nome(parcelascontaspagar.cod_fornecedor,
                                                 parcelascontaspagar.cpf)),
                     DECODE(parcelascontaspagar.datapgto, NULL, 0, movimentobancario.documento),
                     parcelascontaspagar.cod_indicefinanceiro,
                     parcelascontaspagar.cod_tipocontaspagar,
                     parcelascontaspagar.pagarreceber,
                     parcelascontaspagar.cod_tipocobranca,
                     parcelascontaspagar.cod_empenho,
                     empenho.descricao, tipocontaspagar.descricao,
                     tipocobranca.descricaotipocobranca,
                     parcelascontaspagar.OBSERVACAO||' '||
                       parcelascontaspagar.observacao_complementar,
                     DECODE(parcelascontaspagar.cod_indiceFinanceiro,
                            parametro_financeiro.cod_indicefinanceiropadrao, NULL,
                            baixaparcelas.data_indicefinanceiro),
                     parcelascontaspagar.cod_grupoempresa,
                     parcelascontaspagar.cod_empresa, parcelascontaspagar.cod_filial,
                     baixaparcelas.sequenciabaixa, parcelascontaspagar.origem,
                     parcelascontaspagar.cod_situacao,
                     situacaoparcelascontaspagar.descricao,
                     parcelascontaspagar.cod_tipopagamento, tipopagamento.descricao,
                     tipofornecedor.cod_tipofornecedor, tipofornecedor.descricao,
                     parcelascontaspagar.numero_integracao,
                     cc.nivel1, cc.nivel1_desc, cc.nivel2, cc.nivel2_desc,
                     flc.cod_contafluxo, flc.descricaoconta,
                     parcelascontaspagar.datavcto_orig,
                     parcelascontaspagar.usuario, parcelascontaspagar.data_criacao

            UNION ALL

            /* ── BLOCO 2: baixas parciais / em aberto (parcelabaixada = 'N') ── */
            SELECT parcelascontaspagar.cod_fornecedor,
                   parcelascontaspagar.cpf,
                   parcelascontaspagar.usuario,
                   parcelascontaspagar.datavcto,
                   parcelascontaspagar.data_criacao,
                   parcelascontaspagar.documento,
                   parcelascontaspagar.datavcto_orig,
                   parcelascontaspagar.parcela,
                   parcelascontaspagar.dataentrada,
                   parcelascontaspagar.valorparcela                 valorbrutoparcela,
                   DECODE(parcelascontaspagar.provisao, 'N',
                       financeiro.busca_valoratualparcela(
                           parcelascontaspagar.cod_grupoempresa,
                           parcelascontaspagar.cod_tipocontaspagar,
                           parcelascontaspagar.documento,
                           parcelascontaspagar.parcela,
                           parcelascontaspagar.cod_indicefinanceiro,
                           NVL(parcelascontaspagar.data_indicefinanceiro,
                               parcelascontaspagar.datavcto),
                           parcelascontaspagar.datapgto,
                           parcelascontaspagar.valorparcela,
                           parcelascontaspagar.valorindexado,
                           'P', 'N', NULL,
                           DECODE('P','P','N','S')),
                       ((parcelascontaspagar.valorparcela - parcelascontaspagar.valorindexado) *
                        financeiro.busca_indicefinanceiro(
                            parcelascontaspagar.cod_indicefinanceiro,
                            parcelascontaspagar.datavcto))
                   )                                               valorparcela,
                   SUM(baixaparcelas.valorbaixa)                   valorpago,
                   baixaparcelas.databaixa                         datapgto,
                   INITCAP(geral.fn_obtem_nome(parcelascontaspagar.cod_fornecedor,
                                               parcelascontaspagar.cpf))          nome,
                   0                                               cheque,
                   parcelascontaspagar.cod_indicefinanceiro,
                   parcelascontaspagar.cod_tipocontaspagar,
                   parcelascontaspagar.pagarreceber,
                   parcelascontaspagar.cod_tipocobranca,
                   parcelascontaspagar.cod_empenho,
                   empenho.descricao                               desc_empenho,
                   tipocontaspagar.descricao,
                   tipocobranca.descricaotipocobranca,
                   parcelascontaspagar.OBSERVACAO||' '||
                     parcelascontaspagar.observacao_complementar    OBSERVACAO,
                   DECODE(parcelascontaspagar.cod_indiceFinanceiro,
                          parametro_financeiro.cod_indicefinanceiropadrao, NULL,
                          baixaparcelas.data_indicefinanceiro)      DataIndice,
                   parcelascontaspagar.cod_grupoempresa,
                   parcelascontaspagar.cod_empresa,
                   parcelascontaspagar.cod_filial,
                   baixaparcelas.sequenciabaixa,
                   parcelascontaspagar.origem,
                   parcelascontaspagar.cod_situacao,
                   situacaoparcelascontaspagar.descricao            des_situacao,
                   parcelascontaspagar.cod_tipopagamento,
                   tipopagamento.descricao                         des_tipopagamento,
                   tipofornecedor.cod_tipofornecedor,
                   tipofornecedor.descricao                        desc_tipofornecedor,
                   parcelascontaspagar.numero_integracao,
                   cc.nivel1, cc.nivel1_desc, cc.nivel2, cc.nivel2_desc,
                   TO_NUMBER(flc.cod_contafluxo)                   cod_contafluxo,
                   flc.descricaoconta
            FROM   financeiro.tipopagamento,
                   financeiro.situacaoparcelascontaspagar,
                   custo.empenho,
                   financeiro.tipocobranca,
                   material.tipofornecedor                          tipofornecedor,
                   financeiro.tipocontaspagar                       tipocontaspagar,
                   rh.contabancaria,
                   financeiro.movimentobancario,
                   financeiro.baixaparcelas,
                   financeiro.parametro_financeiro,
                   financeiro.parcelascontaspagar,
                   financeiro.fluxocontabancaria,
                   financeiro.fluxoempenho                          fl,
                   financeiro.fluxoconta                            flc,
                   conta_fluxo                                      cc
            WHERE  'N'                              = 'N'
            AND    empenho.cod_empenho              = fl.cod_empenho
            AND    fl.cod_planofluxo                = 16
            AND    fl.cod_contafluxo                = flc.cod_contafluxo
            AND    fl.cod_planofluxo                = flc.cod_planofluxo
            AND    flc.cod_contafluxo               = cc.cod_conta
            AND    PARCELASCONTASPAGAR.PAGARRECEBER = 'P'
            AND    'RR'                             = 'RR'
            AND    tipopagamento.cod_tipopagamento           (+) = parcelascontaspagar.cod_tipopagamento
            AND    NVL(situacaoparcelascontaspagar.visualizar_parcela,'S') = 'S'
            AND    situacaoparcelascontaspagar.cod_situacao              = parcelascontaspagar.cod_situacao
            AND    contabancaria.cod_tipocontabancaria                  = fluxocontabancaria.cod_tipocontabancaria
            AND    contabancaria.cod_contabancaria                      = fluxocontabancaria.cod_contabancaria
            AND    contabancaria.cod_agencia                            = fluxocontabancaria.cod_agencia
            AND    contabancaria.cod_banco                              = fluxocontabancaria.cod_banco
            AND    fluxocontabancaria.cod_planofluxo                    = '16'
            AND    contabancaria.cod_tipocontabancaria                  = movimentobancario.cod_tipocontabancaria
            AND    contabancaria.cod_contabancaria                      = movimentobancario.cod_contabancaria
            AND    contabancaria.cod_agencia                            = movimentobancario.cod_agencia
            AND    contabancaria.cod_banco                              = movimentobancario.cod_banco
            AND   (movimentobancario.cod_banco             = 0 OR 0 = 0)
            AND   (movimentobancario.cod_agencia           = 0 OR 0 = 0)
            AND   (movimentobancario.cod_contabancaria     = 0 OR 0 = 0)
            AND   (movimentobancario.cod_tipocontabancaria = 0 OR 0 = 0)
            AND  ((movimentobancario.valor > 0 AND 'N' = 'N') OR 'N' = 'S')
            AND    movimentobancario.num_lancamentobancario             = baixaparcelas.num_lancamentobancario
            AND    movimentobancario.cod_grupoempresa                   = baixaparcelas.cod_grupoempresa
            AND    baixaparcelas.num_lancamentobancario                 > 0
            AND    baixaparcelas.parcela             = parcelascontaspagar.parcela
            AND    baixaparcelas.documento           = parcelascontaspagar.documento
            AND    baixaparcelas.cod_tipocontaspagar = parcelascontaspagar.cod_tipocontaspagar
            AND    baixaparcelas.cod_grupoempresa    = parcelascontaspagar.cod_grupoempresa
            AND    parametro_financeiro.cod_grupoEmpresa    = parcelascontaspagar.cod_grupoempresa
            AND    parametro_financeiro.cod_empresa         = parcelascontaspagar.cod_empresa
            AND    parametro_financeiro.Cod_Filial          = parcelascontaspagar.cod_filial
            AND    parcelascontaspagar.provisao            <> 'S'
            AND    parcelascontaspagar.parcelabaixada       = 'N'
            AND    parcelascontaspagar.valorparcela        <> 0
            AND    empenho.cod_empenho               (+)   = parcelascontaspagar.cod_empenho
            AND    tipocobranca.cod_tipocobranca      (+)   = parcelascontaspagar.cod_tipocobranca
            AND    tipofornecedor.cod_tipofornecedor  (+)   = tipocontaspagar.cod_tipofornecedor
            AND    rh.intersecao(parcelascontaspagar.dataentrada,
                                 parcelascontaspagar.dataentrada,
                                 tipocontaspagar.datainicio,
                                 tipocontaspagar.datafim)  = 'TRUE'
            AND    tipocontaspagar.cod_tipocontaspagar      = parcelascontaspagar.cod_tipocontaspagar
            AND    tipocontaspagar.cod_filial               = parcelascontaspagar.cod_filial
            AND    tipocontaspagar.cod_empresa              = parcelascontaspagar.cod_empresa
            AND    tipocontaspagar.cod_grupoempresa         = parcelascontaspagar.cod_grupoempresa
            AND    PARCELASCONTASPAGAR.PAGARRECEBER         = 'P'
            AND    NVL(PARCELASCONTASPAGAR.COD_TIPOCOBRANCA, 0) <> 99
            AND    PARCELASCONTASPAGAR.documento NOT IN (206473, 206474)
            AND    EXISTS (
                       SELECT 1
                       FROM   financeiro.movimentobancario,
                              rh.contabancaria
                       WHERE  movimentobancario.cod_grupoempresa       = baixaparcelas.cod_grupoempresa
                       AND    movimentobancario.num_lancamentobancario = baixaparcelas.num_lancamentobancario
                       AND    movimentobancario.cod_banco              = contabancaria.cod_banco
                       AND    movimentobancario.cod_agencia            = contabancaria.cod_agencia
                       AND    movimentobancario.cod_contabancaria      = contabancaria.cod_contabancaria
                       AND    movimentobancario.cod_tipocontabancaria  = contabancaria.cod_tipocontabancaria
                       AND    movimentobancario.cod_grupoempresa       = contabancaria.cod_grupoempresa
                       AND    contabancaria.cod_empresa                = 1
                       AND    contabancaria.cod_filial                 = 1
                   )
            AND   (baixaparcelas.databaixa BETWEEN ? AND ?)
            AND    tipocontaspagar.imprimefluxocaixa = 'S'
            GROUP BY parcelascontaspagar.cod_fornecedor,
                     parcelascontaspagar.cpf, parcelascontaspagar.datavcto,
                     parcelascontaspagar.documento, parcelascontaspagar.parcela,
                     parcelascontaspagar.dataentrada, parcelascontaspagar.valorparcela,
                     DECODE(parcelascontaspagar.provisao, 'N',
                         financeiro.busca_valoratualparcela(
                             parcelascontaspagar.cod_grupoempresa,
                             parcelascontaspagar.cod_tipocontaspagar,
                             parcelascontaspagar.documento, parcelascontaspagar.parcela,
                             parcelascontaspagar.cod_indicefinanceiro,
                             NVL(parcelascontaspagar.data_indicefinanceiro,
                                 parcelascontaspagar.datavcto),
                             parcelascontaspagar.datapgto,
                             parcelascontaspagar.valorparcela,
                             parcelascontaspagar.valorindexado,
                             'P', 'N', NULL, DECODE('P','P','N','S')),
                         ((parcelascontaspagar.valorparcela - parcelascontaspagar.valorindexado) *
                          financeiro.busca_indicefinanceiro(
                              parcelascontaspagar.cod_indicefinanceiro,
                              parcelascontaspagar.datavcto))),
                     baixaparcelas.databaixa, INITCAP(geral.fn_obtem_nome(
                         parcelascontaspagar.cod_fornecedor, parcelascontaspagar.cpf)),
                     0, parcelascontaspagar.cod_indicefinanceiro,
                     parcelascontaspagar.cod_tipocontaspagar,
                     parcelascontaspagar.pagarreceber,
                     parcelascontaspagar.cod_tipocobranca,
                     parcelascontaspagar.cod_empenho,
                     empenho.descricao, tipocontaspagar.descricao,
                     tipocobranca.descricaotipocobranca,
                     parcelascontaspagar.OBSERVACAO||' '||
                       parcelascontaspagar.observacao_complementar,
                     DECODE(parcelascontaspagar.cod_indiceFinanceiro,
                            parametro_financeiro.cod_indicefinanceiropadrao, NULL,
                            baixaparcelas.data_indicefinanceiro),
                     parcelascontaspagar.cod_grupoempresa,
                     parcelascontaspagar.cod_empresa, parcelascontaspagar.cod_filial,
                     baixaparcelas.sequenciabaixa, parcelascontaspagar.origem,
                     parcelascontaspagar.cod_situacao,
                     situacaoparcelascontaspagar.descricao,
                     parcelascontaspagar.cod_tipopagamento, tipopagamento.descricao,
                     tipofornecedor.cod_tipofornecedor, tipofornecedor.descricao,
                     parcelascontaspagar.numero_integracao,
                     cc.nivel1, cc.nivel1_desc, cc.nivel2, cc.nivel2_desc,
                     flc.cod_contafluxo, flc.descricaoconta,
                     parcelascontaspagar.datavcto_orig,
                     parcelascontaspagar.usuario, parcelascontaspagar.data_criacao
        )
        GROUP BY nivel1, nivel1_desc, nivel2, nivel2_desc,
                 cod_contafluxo, descricaoconta, cod_empenho,
                 desc_empenho, cod_fornecedor, nome, datapgto,
                 cod_tipocontaspagar, descricao_tipodecontaspagar,
                 dataentrada, datavcto_orig, datavcto,
                 usuario, data_criacao, PAGARRECEBER,
                 documento, parcela
        ORDER BY cod_contafluxo, datapgto
    """;

    /**
     * Executa a query com o intervalo de datas informado.
     *
     * Ordem dos binds (7 parâmetros ? no SQL):
     *   1  → DATA_FIM  (cláusula < DATA_FIM do movimentobancario)
     *   2  → DATA_INI  (BETWEEN bloco1 – 1ª ocorrência)
     *   3  → DATA_FIM  (BETWEEN bloco1 – 1ª ocorrência)
     *   4  → DATA_INI  (BETWEEN bloco1 – 2ª ocorrência)
     *   5  → DATA_FIM  (BETWEEN bloco1 – 2ª ocorrência)
     *   6  → DATA_INI  (BETWEEN bloco2)
     *   7  → DATA_FIM  (BETWEEN bloco2)
     */
    public List<FluxoRealizadoItem> buscar(LocalDate dataIni, LocalDate dataFim) {
        List<FluxoRealizadoItem> lista = new ArrayList<>();

        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            Date sqlIni = Date.valueOf(dataIni);
            Date sqlFim = Date.valueOf(dataFim);

            ps.setDate(1, sqlFim);   // < DATA_FIM
            ps.setDate(2, sqlIni);   // BETWEEN bloco1 – 1ª
            ps.setDate(3, sqlFim);
            ps.setDate(4, sqlIni);   // BETWEEN bloco1 – 2ª
            ps.setDate(5, sqlFim);
            ps.setDate(6, sqlIni);   // BETWEEN bloco2
            ps.setDate(7, sqlFim);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar fluxo realizado: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta do fluxo realizado", e);
        }

        return lista;
    }

    // ─── Mapping ─────────────────────────────────────────────────────────

    private FluxoRealizadoItem mapRow(ResultSet rs) throws SQLException {
        FluxoRealizadoItem item = new FluxoRealizadoItem();

        // col 1: TO_NUMBER(cod_contafluxo)
        int codConta = rs.getInt(1);
        item.setCodContaFluxo(rs.wasNull() ? null : codConta);

        item.setDescricaoConta(rs.getString(2));
        item.setCodEmpenho(rs.getString(3));
        item.setDescEmpenho(rs.getString(4));

        int codForn = rs.getInt(5);
        item.setCodFornecedor(rs.wasNull() ? null : codForn);

        item.setNome(rs.getString(6));

        int codTipo = rs.getInt(7);
        item.setCodTipoContasPagar(rs.wasNull() ? null : codTipo);

        item.setDescricaoTipoDeContasPagar(rs.getString(8));
        item.setDataEntrada(toLocalDate(rs.getDate(9)));
        item.setDataPgto(toLocalDate(rs.getDate(10)));
        item.setDataVctoOrig(toLocalDate(rs.getDate(11)));
        item.setDataVcto(toLocalDate(rs.getDate(12)));
        item.setDataCriacao(toLocalDate(rs.getDate(13)));
        item.setUsuario(rs.getString(14));
        item.setPagarReceber(rs.getString(15));
        item.setDocumento(rs.getString(16));
        item.setParcela(rs.getString(17));
        item.setRealizado(rs.getBigDecimal(18));

        return item;
    }

    private LocalDate toLocalDate(Date d) {
        return d == null ? null : d.toLocalDate();
    }
}
