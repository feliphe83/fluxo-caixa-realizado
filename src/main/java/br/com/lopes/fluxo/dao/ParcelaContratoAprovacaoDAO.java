package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parcelas de contrato aguardando aprovação.
 *
 * A consulta é a que a área financeira usa — duas pernas em UNION ALL, a
 * segunda para os adiantamentos — com três mudanças, todas em trechos que
 * estavam presos a uma data e a uma pessoa:
 *
 * 1. As duas datas de vencimento (uma em cada perna do UNION) viraram bind
 *    da data de corte do agendamento. Fixas na consulta, mudar o recorte
 *    exigiria deploy.
 * 2. O 324 do fn_funcionarioaprovaetapa e do fn_verificasupervisorde virou
 *    bind: é o id_logon do ERP de quem vai receber. Assim cada destinatário
 *    recebe as parcelas que ELE aprova.
 * 3. O COD_FUNC_APROVADOR = 16417 virou opcional, com zero desligando o
 *    filtro — fixo, todo destinatário receberia a lista daquela pessoa.
 *
 * O 8247 continua fixo: é o código do formulário de aprovação de parcela.
 *
 * DOCUMENTO foi acrescentado à lista de colunas do select de fora. A original
 * trazia só a parcela, e sem o número do contrato duas parcelas "1" de
 * contratos diferentes seriam a mesma coisa para o controle de envio — uma
 * delas nunca seria avisada.
 *
 * POSSUI_PERMISSAO_ETAPA é calculada e NÃO filtra, como na consulta original.
 */
public class ParcelaContratoAprovacaoDAO {

    private static final Logger LOG = Logger.getLogger(ParcelaContratoAprovacaoDAO.class.getName());

    /** Binds: idLogon, dataVcto (perna 1), dataVcto (perna 2), idLogon, funcAprovador, funcAprovador. */
    private static final String SQL = """
        select DOCUMENTO, DATAVCTO, PARCELA, NOME_FORNECEDOR, VALOR_LIQUIDO,
               DESC_OBJETOCUSTO, DESC_EMPENHO, FIXOVARIAVEL, OBSERVACAO
        from (
          SELECT TMP.*
               , FINANCEIRO.fn_funcionarioaprovaetapa( tmp.cod_grupoempresa
                                                     , tmp.cod_empresa
                                                     , tmp.cod_filial
                                                     , tmp.documento
                                                     , ?                       /* pn_id_logon */
                                                     , 8247                    /* pn_cod_formulario */
                                                     , 'A'
                                                     , ''
                                                     , 'P'
                                                     , tmp.cod_tipocontaspagar
                                                     , tmp.parcela
                                                     , null
                                                     , tmp.valor_liquido ) POSSUI_PERMISSAO_ETAPA
          FROM (
            /* --- PRIMEIRA PERNA DO UNION --- */
            SELECT TO_NUMBER(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA) COD_GRUPOEMPRESA
                 , TO_NUMBER(PARCELASCONTASPAGAR.COD_EMPRESA)      COD_EMPRESA
                 , EMPRESA.NOME NOMEEMPRESA
                 , TO_NUMBER(PARCELASCONTASPAGAR.COD_FILIAL)       COD_FILIAL
                 , FILIAL.NOME NOMEFILIAL
                 , PARCELASCONTASPAGAR.DATAVCTO
                 , TO_NUMBER(PARCELASCONTASPAGAR.DOCUMENTO) DOCUMENTO
                 , TO_NUMBER(PARCELASCONTASPAGAR.PARCELA)   PARCELA
                 , PARCELASCONTASPAGAR.DATAPGTO
                 , (FINANCEIRO.BUSCA_VALORATUALPARCELA(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA,
                                                       PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR,
                                                       PARCELASCONTASPAGAR.DOCUMENTO,
                                                       PARCELASCONTASPAGAR.PARCELA,
                                                       PARCELASCONTASPAGAR.COD_INDICEFINANCEIRO,
                                                       PARCELASCONTASPAGAR.DATAVCTO,
                                                       PARCELASCONTASPAGAR.DATAPGTO,
                                                       PARCELASCONTASPAGAR.VALORPARCELA,
                                                       PARCELASCONTASPAGAR.VALORINDEXADO,
                                                       'T') -
                    (FINANCEIRO.CONSULTA_JUROSDESCONTOPARCELA(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA,
                                                       PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR,
                                                       PARCELASCONTASPAGAR.DOCUMENTO,
                                                       PARCELASCONTASPAGAR.PARCELA,
                                                       PARCELASCONTASPAGAR.DATAVCTO,
                                                       'D')) +
                    (FINANCEIRO.CONSULTA_JUROSDESCONTOPARCELA(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA,
                                                       PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR,
                                                       PARCELASCONTASPAGAR.DOCUMENTO,
                                                       PARCELASCONTASPAGAR.PARCELA,
                                                       PARCELASCONTASPAGAR.DATAVCTO,
                                                       'C'))) VALOR
                 , ((FINANCEIRO.BUSCA_VALORATUALPARCELA(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA,
                                                       PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR,
                                                       PARCELASCONTASPAGAR.DOCUMENTO,
                                                       PARCELASCONTASPAGAR.PARCELA,
                                                       PARCELASCONTASPAGAR.COD_INDICEFINANCEIRO,
                                                       PARCELASCONTASPAGAR.DATAVCTO,
                                                       PARCELASCONTASPAGAR.DATAPGTO,
                                                       PARCELASCONTASPAGAR.VALORPARCELA,
                                                       PARCELASCONTASPAGAR.VALORINDEXADO,
                                                       'T') -
                    (FINANCEIRO.CONSULTA_JUROSDESCONTOPARCELA(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA,
                                                       PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR,
                                                       PARCELASCONTASPAGAR.DOCUMENTO,
                                                       PARCELASCONTASPAGAR.PARCELA,
                                                       PARCELASCONTASPAGAR.DATAVCTO,
                                                       'D')) +
                    (FINANCEIRO.CONSULTA_JUROSDESCONTOPARCELA(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA,
                                                       PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR,
                                                       PARCELASCONTASPAGAR.DOCUMENTO,
                                                       PARCELASCONTASPAGAR.PARCELA,
                                                       PARCELASCONTASPAGAR.DATAVCTO,
                                                       'C'))) * nvl(parcelascontrato.valor_indice_acordado_ent, financeiro.busca_indicefinanceiro(parcelascontrato.cod_indicefinanceiro, nvl(parcelascontrato.data_indicefinanceiro, PARCELASCONTRATO.datafinal)))) valor_liquido
                 , TO_NUMBER(PARCELASCONTASPAGAR.COD_SITUACAO) COD_SITUACAO
                 , TO_NUMBER(FINANCEIRO.HISTORICOCONTRATO.FUNCAPROVACAO) COD_FUNC_APROVADOR
                 , TO_NUMBER(nvl(PARCELASCONTASPAGAR.COD_OBJETOCUSTO, historicocontrato.cod_objetocusto)) COD_OBJETOCUSTO
                 , nvl(OBJETOCUSTO.NEGOCIO    , OBJETOCUSTOHIST.NEGOCIO    )||'-'||
                   nvl(OBJETOCUSTO.PROCESSO   , OBJETOCUSTOHIST.PROCESSO   )||'-'||
                   nvl(OBJETOCUSTO.SUBPROCESSO, OBJETOCUSTOHIST.SUBPROCESSO)||'-'||
                   nvl(OBJETOCUSTO.ATIVIDADE  , OBJETOCUSTOHIST.ATIVIDADE  ) || ' - ' ||
                   nvl(OBJETOCUSTO.DESCRICAO  , OBJETOCUSTOHIST.DESCRICAO  ) DESC_OBJETOCUSTO
                 , TO_NUMBER(PARCELASCONTASPAGAR.COD_EMPENHO) COD_EMPENHO
                 , EMPENHO.DESCRICAO DESC_EMPENHO
                 , TO_NUMBER(CONTRATO.COD_TIPOCONTRATO) COD_TIPOCONTRATO
                 , TIPOCONTRATO.DESCRICAO DESC_TIPOCONTRATO
                 , TO_NUMBER(PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR) COD_TIPOCONTASPAGAR
                 , TIPOCONTASPAGAR.DESCRICAO DES_TIPOCONTASPAGAR
                 , TO_NUMBER(FORNECEDOR.COD_FORNECEDOR) COD_FORNECEDOR
                 , PESSOA.NOME NOME_FORNECEDOR
                 , TIPOCOBRANCA.DESCRICAOTIPOCOBRANCA
                 , PARCELASCONTASPAGAR.ORCAMENTO_ESTOURADO
                 , HISTORICOCONTRATO.FIXOVARIAVEL FIXOVARIAVEL
                 , PARCELASCONTASPAGAR.DATAAPROVACAOCONTRATO DATAAPROVACAO
                 , PARCELASCONTASPAGAR.DATAENTRADA
                 , PARCELASCONTASPAGAR.PROVISAO
                 , CAST(PARCELASCONTRATO.JUSTIFICATIVA AS VARCHAR2(4000)) JUSTIFICATIVA
                 , CONTRATO.EXIGENOTAFISCAL
                 , CAST(PARCELASCONTRATO.OBSERVACAO AS VARCHAR2(4000)) OBSERVACAO
                 , PARCELASCONTRATO.DATAFINAL DATACOMPETENCIA
                 , TO_NUMBER(TO_CHAR(TRUNC(SYSDATE),'RRRRMM')) ANOMES
                 , CASE WHEN CONTRATO.PAGARRECEBER = 'P' THEN 'Pagamento' ELSE 'Recebimento' END DESCTIPO_CTR
                 , NVL(PARCELASCONTRATO.USUARIO_CRIACAO, PARCELASCONTASPAGAR.USUARIO) USUARIO_CRIACAO
                 , PARCELASCONTRATO.VALOR VALOR_ORIGINAL
                 , PARCELASCONTASPAGAR.COD_INDICEFINANCEIRO
                 , (SELECT DESCRICAO FROM FINANCEIRO.INDICEFINANCEIRO WHERE COD_INDICEFINANCEIRO = PARCELASCONTASPAGAR.COD_INDICEFINANCEIRO) DESC_INDICE
            FROM   RH.EMPRESA
               ,   RH.FILIAL
               ,   RH.PESSOA
               ,   RH.OBJETOCUSTO OBJETOCUSTOHIST
               ,   RH.OBJETOCUSTO
               ,   CUSTO.EMPENHO
               ,   MATERIAL.FORNECEDOR
               ,   FINANCEIRO.TIPOCONTASPAGAR
               ,   FINANCEIRO.HISTORICOCONTRATO
               ,   FINANCEIRO.CONTRATO
               ,   FINANCEIRO.TIPOCOBRANCA
               ,   FINANCEIRO.PARCELASCONTASPAGAR
               ,   FINANCEIRO.PARCELASCONTRATO
               ,   FINANCEIRO.TIPOCONTRATO
            WHERE  0=0
            AND    OBJETOCUSTOHIST.COD_OBJETOCUSTO(+)            = HISTORICOCONTRATO.COD_OBJETOCUSTO
            AND    OBJETOCUSTO.COD_OBJETOCUSTO   (+)             = PARCELASCONTASPAGAR.COD_OBJETOCUSTO
            AND    EMPENHO.COD_EMPENHO           (+)             = PARCELASCONTASPAGAR.COD_EMPENHO
            AND    TIPOCONTRATO.COD_TIPOCONTRATO                 = CONTRATO.COD_TIPOCONTRATO
            AND    TIPOCONTRATO.COD_GRUPOEMPRESA                 = CONTRATO.COD_GRUPOEMPRESA
            AND    TIPOCONTRATO.COD_EMPRESA                      = CONTRATO.COD_EMPRESA
            AND    TIPOCONTRATO.COD_FILIAL                       = CONTRATO.COD_FILIAL
            AND    RH.INTERSECAO(PARCELASCONTRATO.DATAINICIO, PARCELASCONTRATO.DATAFINAL, TIPOCONTRATO.DATAINICIO, TIPOCONTRATO.DATAFIM) = 'TRUE'
            AND    FILIAL.COD_GRUPOEMPRESA                       = PARCELASCONTASPAGAR.COD_GRUPOEMPRESA
            AND    FILIAL.COD_EMPRESA                            = PARCELASCONTASPAGAR.COD_EMPRESA
            AND    FILIAL.COD_FILIAL                             = PARCELASCONTASPAGAR.COD_FILIAL
            AND    EMPRESA.COD_GRUPOEMPRESA                      = FILIAL.COD_GRUPOEMPRESA
            AND    EMPRESA.COD_EMPRESA                           = FILIAL.COD_EMPRESA
            AND    RH.INTERSECAO(PARCELASCONTRATO.DATAINICIO, PARCELASCONTRATO.DATAFINAL, FINANCEIRO.HISTORICOCONTRATO.DATAINICIO, FINANCEIRO.HISTORICOCONTRATO.DATATERMINO) = 'TRUE'
            AND    FINANCEIRO.HISTORICOCONTRATO.NUMEROCONTRATO   = CONTRATO.NUMEROCONTRATO
            AND    FINANCEIRO.HISTORICOCONTRATO.COD_GRUPOEMPRESA = CONTRATO.COD_GRUPOEMPRESA
            AND    CONTRATO.NUMEROCONTRATO                       = PARCELASCONTASPAGAR.DOCUMENTO
            AND    CONTRATO.COD_GRUPOEMPRESA                     = PARCELASCONTASPAGAR.COD_GRUPOEMPRESA
            AND    TIPOCOBRANCA.COD_TIPOCOBRANCA (+)             = PARCELASCONTASPAGAR.COD_TIPOCOBRANCA
            AND    RH.INTERSECAO(PARCELASCONTRATO.DATAINICIO, PARCELASCONTRATO.DATAFINAL, TIPOCONTASPAGAR.DATAINICIO, TIPOCONTASPAGAR.DATAFIM) = 'TRUE'
            AND    TIPOCONTASPAGAR.COD_TIPOCONTASPAGAR           = PARCELASCONTRATO.COD_TIPOCONTASPAGAR
            AND    TIPOCONTASPAGAR.COD_FILIAL                    = PARCELASCONTRATO.COD_FILIAL
            AND    TIPOCONTASPAGAR.COD_EMPRESA                   = PARCELASCONTRATO.COD_EMPRESA
            AND    TIPOCONTASPAGAR.COD_GRUPOEMPRESA              = PARCELASCONTRATO.COD_GRUPOEMPRESA
            AND    PARCELASCONTASPAGAR.COD_FORNECEDOR            = FORNECEDOR.COD_FORNECEDOR
            AND    FORNECEDOR.COD_PESSOA                         = PESSOA.COD_PESSOA
            AND    PARCELASCONTASPAGAR.ORCAMENTO_ESTOURADO      <> 'S'
            AND    (
                    ((PARCELASCONTASPAGAR.COD_SITUACAO = RH.C('SITUACAO_AUTORIZANTE')) AND ('S' = 'N')) OR
                    ((PARCELASCONTASPAGAR.COD_SITUACAO IN (RH.C('SITUACAO_AUTORIZANTE'), RH.C('SITUACAOPARCELA'))) AND ('S' = 'S')) OR
                    ((PARCELASCONTASPAGAR.COD_SITUACAO = NVL(RH.C('SIT_REPROVA_ALCADA','S'),0)) AND ('S' = 'S'))
                   )
            AND    (FINANCEIRO.PARCELASCONTASPAGAR.APROVADORCONTRATO IS NULL AND FINANCEIRO.PARCELASCONTASPAGAR.DATAAPROVACAOCONTRATO IS NULL)
            AND    PARCELASCONTASPAGAR.DATAPGTO             IS NULL
            AND    PARCELASCONTASPAGAR.PARCELA              = PARCELASCONTRATO.PARCELA
            AND    PARCELASCONTASPAGAR.DOCUMENTO            = PARCELASCONTRATO.NUMEROCONTRATO
            AND    PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR  = PARCELASCONTRATO.COD_TIPOCONTASPAGAR
            AND    PARCELASCONTASPAGAR.COD_GRUPOEMPRESA     = PARCELASCONTRATO.COD_GRUPOEMPRESA
            AND    PARCELASCONTASPAGAR.DATAVCTO            >= ?
            AND    PARCELASCONTRATO.COD_GRUPOEMPRESA        = 1
            AND    PARCELASCONTRATO.COD_EMPRESA             = 1
            AND    PARCELASCONTRATO.COD_FILIAL              = 1

            UNION ALL

            /* --- SEGUNDA PERNA DO UNION: adiantamentos --- */
            SELECT TO_NUMBER(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA) COD_GRUPOEMPRESA
                 , TO_NUMBER(PARCELASCONTASPAGAR.COD_EMPRESA)      COD_EMPRESA
                 , EMPRESA.NOME NOMEEMPRESA
                 , TO_NUMBER(PARCELASCONTASPAGAR.COD_FILIAL)       COD_FILIAL
                 , FILIAL.NOME NOMEFILIAL
                 , PARCELASCONTASPAGAR.DATAVCTO
                 , TO_NUMBER(PARCELASCONTASPAGAR.DOCUMENTO) DOCUMENTO
                 , TO_NUMBER(PARCELASCONTASPAGAR.PARCELA)   PARCELA
                 , PARCELASCONTASPAGAR.DATAPGTO
                 , (FINANCEIRO.BUSCA_VALORATUALPARCELA(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA,
                                                       PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR,
                                                       PARCELASCONTASPAGAR.DOCUMENTO,
                                                       PARCELASCONTASPAGAR.PARCELA,
                                                       PARCELASCONTASPAGAR.COD_INDICEFINANCEIRO,
                                                       PARCELASCONTASPAGAR.DATAVCTO,
                                                       PARCELASCONTASPAGAR.DATAPGTO,
                                                       PARCELASCONTASPAGAR.VALORPARCELA,
                                                       PARCELASCONTASPAGAR.VALORINDEXADO,
                                                       'T') -
                    (FINANCEIRO.CONSULTA_JUROSDESCONTOPARCELA(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA,
                                                       PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR,
                                                       PARCELASCONTASPAGAR.DOCUMENTO,
                                                       PARCELASCONTASPAGAR.PARCELA,
                                                       PARCELASCONTASPAGAR.DATAVCTO,
                                                       'D')) +
                    (FINANCEIRO.CONSULTA_JUROSDESCONTOPARCELA(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA,
                                                       PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR,
                                                       PARCELASCONTASPAGAR.DOCUMENTO,
                                                       PARCELASCONTASPAGAR.PARCELA,
                                                       PARCELASCONTASPAGAR.DATAVCTO,
                                                       'C'))) VALOR
                 , CASE WHEN nvl(parcelascontrato.valor,0) > 0 THEN
                        ((FINANCEIRO.BUSCA_VALORATUALPARCELA(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA,
                                                       PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR,
                                                       PARCELASCONTASPAGAR.DOCUMENTO,
                                                       PARCELASCONTASPAGAR.PARCELA,
                                                       PARCELASCONTASPAGAR.COD_INDICEFINANCEIRO,
                                                       PARCELASCONTASPAGAR.DATAVCTO,
                                                       PARCELASCONTASPAGAR.DATAPGTO,
                                                       PARCELASCONTASPAGAR.VALORPARCELA,
                                                       PARCELASCONTASPAGAR.VALORINDEXADO,
                                                       'T') -
                         (FINANCEIRO.CONSULTA_JUROSDESCONTOPARCELA(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA,
                                                       PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR,
                                                       PARCELASCONTASPAGAR.DOCUMENTO,
                                                       PARCELASCONTASPAGAR.PARCELA,
                                                       PARCELASCONTASPAGAR.DATAVCTO,
                                                       'D')) +
                        (FINANCEIRO.CONSULTA_JUROSDESCONTOPARCELA(PARCELASCONTASPAGAR.COD_GRUPOEMPRESA,
                                                       PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR,
                                                       PARCELASCONTASPAGAR.DOCUMENTO,
                                                       PARCELASCONTASPAGAR.PARCELA,
                                                       PARCELASCONTASPAGAR.DATAVCTO,
                                                       'C'))) * nvl(parcelascontrato.valor_indice_acordado_ent, financeiro.busca_indicefinanceiro(parcelascontrato.cod_indicefinanceiro, nvl(parcelascontrato.data_indicefinanceiro, PARCELASCONTRATO.datafinal))))
                   ELSE parcelascontaspagar.valorparcela
                   END valor_liquido
                 , TO_NUMBER(PARCELASCONTASPAGAR.COD_SITUACAO) COD_SITUACAO
                 , TO_NUMBER(FINANCEIRO.HISTORICOCONTRATO.FUNCAPROVACAO) COD_FUNC_APROVADOR
                 , TO_NUMBER(nvl(PARCELASCONTASPAGAR.COD_OBJETOCUSTO, historicocontrato.cod_objetocusto)) COD_OBJETOCUSTO
                 , nvl(OBJETOCUSTO.NEGOCIO    , OBJETOCUSTOHIST.NEGOCIO    )||'-'||
                   nvl(OBJETOCUSTO.PROCESSO   , OBJETOCUSTOHIST.PROCESSO   )||'-'||
                   nvl(OBJETOCUSTO.SUBPROCESSO, OBJETOCUSTOHIST.SUBPROCESSO)||'-'||
                   nvl(OBJETOCUSTO.ATIVIDADE  , OBJETOCUSTOHIST.ATIVIDADE  ) || ' - ' ||
                   nvl(OBJETOCUSTO.DESCRICAO  , OBJETOCUSTOHIST.DESCRICAO  ) DESC_OBJETOCUSTO
                 , TO_NUMBER(PARCELASCONTASPAGAR.COD_EMPENHO) COD_EMPENHO
                 , EMPENHO.DESCRICAO DESC_EMPENHO
                 , TO_NUMBER(CONTRATO.COD_TIPOCONTRATO) COD_TIPOCONTRATO
                 , TIPOCONTRATO.DESCRICAO DESC_TIPOCONTRATO
                 , TO_NUMBER(PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR) COD_TIPOCONTASPAGAR
                 , TIPOCONTASPAGAR.DESCRICAO DES_TIPOCONTASPAGAR
                 , TO_NUMBER(FORNECEDOR.COD_FORNECEDOR) COD_FORNECEDOR
                 , PESSOA.NOME NOME_FORNECEDOR
                 , TIPOCOBRANCA.DESCRICAOTIPOCOBRANCA
                 , PARCELASCONTASPAGAR.ORCAMENTO_ESTOURADO
                 , HISTORICOCONTRATO.FIXOVARIAVEL
                 , PARCELASCONTASPAGAR.DATAAPROVACAOCONTRATO DATAAPROVACAO
                 , PARCELASCONTASPAGAR.DATAENTRADA
                 , PARCELASCONTASPAGAR.PROVISAO
                 , CAST(NULL AS VARCHAR2(4000)) JUSTIFICATIVA
                 , CONTRATO.EXIGENOTAFISCAL
                 , CAST(PARCELASCONTASPAGAR.OBSERVACAO AS VARCHAR2(4000)) OBSERVACAO
                 , PARCELASCONTASPAGAR.DATAENTRADA DATACOMPETENCIA
                 , TO_NUMBER(TO_CHAR(TRUNC(SYSDATE),'RRRRMM')) ANOMES
                 , CASE WHEN CONTRATO.PAGARRECEBER = 'P' THEN 'Pagamento' ELSE 'Recebimento' END DESCTIPO_CTR
                 , NVL(PARCELASCONTRATO.USUARIO_CRIACAO, PARCELASCONTASPAGAR.USUARIO) USUARIO_CRIACAO
                 , CONTRATOADIANTAMENTO.VALOR_PARCELA VALOR_ORIGINAL
                 , PARCELASCONTASPAGAR.COD_INDICEFINANCEIRO
                 , (SELECT DESCRICAO FROM FINANCEIRO.INDICEFINANCEIRO WHERE COD_INDICEFINANCEIRO = PARCELASCONTASPAGAR.COD_INDICEFINANCEIRO) DESC_INDICE
            FROM   RH.EMPRESA
               ,   RH.FILIAL
               ,   RH.PESSOA
               ,   RH.OBJETOCUSTO OBJETOCUSTOHIST
               ,   RH.OBJETOCUSTO
               ,   CUSTO.EMPENHO
               ,   MATERIAL.FORNECEDOR
               ,   FINANCEIRO.TIPOCONTASPAGAR
               ,   FINANCEIRO.HISTORICOCONTRATO
               ,   FINANCEIRO.CONTRATO
               ,   FINANCEIRO.TIPOCOBRANCA
               ,   FINANCEIRO.PARCELASCONTRATO
               ,   FINANCEIRO.CONTRATOADIANTAMENTO
               ,   FINANCEIRO.PARCELASCONTASPAGAR
               ,   FINANCEIRO.PARAMETRO_FINANCEIRO
               ,   FINANCEIRO.TIPOCONTRATO
            WHERE  0=0
            AND    OBJETOCUSTOHIST.COD_OBJETOCUSTO(+)       = HISTORICOCONTRATO.COD_OBJETOCUSTO
            AND    OBJETOCUSTO.COD_OBJETOCUSTO   (+)        = PARCELASCONTASPAGAR.COD_OBJETOCUSTO
            AND    EMPENHO.COD_EMPENHO           (+)        = PARCELASCONTASPAGAR.COD_EMPENHO
            AND    TIPOCONTRATO.COD_TIPOCONTRATO            = CONTRATO.COD_TIPOCONTRATO
            AND    TIPOCONTRATO.COD_GRUPOEMPRESA            = CONTRATO.COD_GRUPOEMPRESA
            AND    TIPOCONTRATO.COD_EMPRESA                 = CONTRATO.COD_EMPRESA
            AND    TIPOCONTRATO.COD_FILIAL                  = CONTRATO.COD_FILIAL
            AND    RH.INTERSECAO(PARCELASCONTASPAGAR.DATAVCTO, PARCELASCONTASPAGAR.DATAVCTO, TIPOCONTRATO.DATAINICIO, TIPOCONTRATO.DATAFIM) = 'TRUE'
            AND    FILIAL.COD_GRUPOEMPRESA                  = PARCELASCONTASPAGAR.COD_GRUPOEMPRESA
            AND    FILIAL.COD_EMPRESA                       = PARCELASCONTASPAGAR.COD_EMPRESA
            AND    FILIAL.COD_FILIAL                        = PARCELASCONTASPAGAR.COD_FILIAL
            AND    EMPRESA.COD_GRUPOEMPRESA                 = FILIAL.COD_GRUPOEMPRESA
            AND    EMPRESA.COD_EMPRESA                      = FILIAL.COD_EMPRESA
            AND    RH.INTERSECAO(PARCELASCONTASPAGAR.DATAVCTO, PARCELASCONTASPAGAR.DATAVCTO, HISTORICOCONTRATO.DATAINICIO, HISTORICOCONTRATO.DATATERMINO) = 'TRUE'
            AND    HISTORICOCONTRATO.NUMEROCONTRATO         = PARCELASCONTASPAGAR.DOCUMENTO
            AND    HISTORICOCONTRATO.COD_GRUPOEMPRESA       = PARCELASCONTASPAGAR.COD_GRUPOEMPRESA
            AND    HISTORICOCONTRATO.NUMEROCONTRATO         = CONTRATO.NUMEROCONTRATO
            AND    HISTORICOCONTRATO.COD_GRUPOEMPRESA       = CONTRATO.COD_GRUPOEMPRESA
            AND    CONTRATO.NUMEROCONTRATO                  = PARCELASCONTASPAGAR.DOCUMENTO
            AND    CONTRATO.COD_GRUPOEMPRESA                = PARCELASCONTASPAGAR.COD_GRUPOEMPRESA
            AND    TIPOCOBRANCA.COD_TIPOCOBRANCA (+)        = PARCELASCONTASPAGAR.COD_TIPOCOBRANCA
            AND    RH.INTERSECAO(PARCELASCONTASPAGAR.DATAVCTO, PARCELASCONTASPAGAR.DATAVCTO, TIPOCONTASPAGAR.DATAINICIO, TIPOCONTASPAGAR.DATAFIM) = 'TRUE'
            AND    TIPOCONTASPAGAR.COD_TIPOCONTASPAGAR      = PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR
            AND    TIPOCONTASPAGAR.COD_FILIAL               = PARCELASCONTASPAGAR.COD_FILIAL
            AND    TIPOCONTASPAGAR.COD_EMPRESA              = PARCELASCONTASPAGAR.COD_EMPRESA
            AND    TIPOCONTASPAGAR.COD_GRUPOEMPRESA         = PARCELASCONTASPAGAR.COD_GRUPOEMPRESA
            AND    PARCELASCONTASPAGAR.COD_FORNECEDOR       = FORNECEDOR.COD_FORNECEDOR
            AND    FORNECEDOR.COD_PESSOA                    = PESSOA.COD_PESSOA
            AND    PARCELASCONTRATO.PARCELA             (+)= PARCELASCONTASPAGAR.PARCELA
            AND    PARCELASCONTRATO.NUMEROCONTRATO      (+)= PARCELASCONTASPAGAR.DOCUMENTO
            AND    PARCELASCONTRATO.COD_TIPOCONTASPAGAR (+)= PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR
            AND    PARCELASCONTRATO.COD_GRUPOEMPRESA    (+)= PARCELASCONTASPAGAR.COD_GRUPOEMPRESA
            AND    (
                    ((PARCELASCONTASPAGAR.COD_SITUACAO = RH.C('SITUACAO_AUTORIZANTE')) AND ('S' = 'N')) OR
                    ((PARCELASCONTASPAGAR.COD_SITUACAO IN (RH.C('SITUACAO_AUTORIZANTE'), RH.C('SITUACAOPARCELA'))) AND ('S' = 'S')) OR
                    ((PARCELASCONTASPAGAR.COD_SITUACAO = NVL(RH.C('SIT_REPROVA_ALCADA','S'),0)) AND ('S' = 'S'))
                   )
            AND    CONTRATOADIANTAMENTO.COD_GRUPOEMPRESA    = PARCELASCONTASPAGAR.COD_GRUPOEMPRESA
            AND    CONTRATOADIANTAMENTO.NUMEROCONTRATO      = PARCELASCONTASPAGAR.DOCUMENTO
            AND    CONTRATOADIANTAMENTO.PARCELA             = PARCELASCONTASPAGAR.PARCELA
            AND    PARCELASCONTASPAGAR.ORCAMENTO_ESTOURADO  <> 'S'
            AND    (FINANCEIRO.PARCELASCONTASPAGAR.APROVADORCONTRATO IS NULL AND FINANCEIRO.PARCELASCONTASPAGAR.DATAAPROVACAOCONTRATO IS NULL)
            AND    PARCELASCONTASPAGAR.DATAPGTO             IS NULL
            AND    PARCELASCONTASPAGAR.DATAVCTO            >= ?
            AND    PARCELASCONTASPAGAR.COD_TIPOCONTASPAGAR  IN (PARAMETRO_FINANCEIRO.COD_TIPOCONTRATO_ADIANTAMENTO, PARAMETRO_FINANCEIRO.COD_TIPOCONTRATO_ADIANT_REC)
            AND    PARCELASCONTASPAGAR.COD_FILIAL           = PARAMETRO_FINANCEIRO.COD_FILIAL
            AND    PARCELASCONTASPAGAR.COD_EMPRESA          = PARAMETRO_FINANCEIRO.COD_EMPRESA
            AND    PARCELASCONTASPAGAR.COD_GRUPOEMPRESA     = PARAMETRO_FINANCEIRO.COD_GRUPOEMPRESA
            AND    PARAMETRO_FINANCEIRO.COD_GRUPOEMPRESA    = 1
            AND    PARAMETRO_FINANCEIRO.COD_EMPRESA         = 1
            AND    PARAMETRO_FINANCEIRO.COD_FILIAL          = 1
          ) TMP
          WHERE SEGURANCANOVO.FN_VERIFICASUPERVISORDE(TMP.COD_GRUPOEMPRESA
                                                    , TMP.COD_EMPRESA
                                                    , TMP.COD_FILIAL
                                                    , ?                        /* pn_id_logon */
                                                    , TMP.COD_OBJETOCUSTO
                                                    , TMP.COD_EMPENHO
                                                    , 0
                                                    , 0
                                                    , 8247) = 'T'              /* pn_cod_formulario */
          AND   (? = 0 OR TMP.COD_FUNC_APROVADOR = ?)
        )
        ORDER BY DATAVCTO, DOCUMENTO, PARCELA
        """;

    /**
     * Parcelas sem aprovação que este usuário pode aprovar.
     *
     * @param idLogon       id_logon do ERP, de fc_usuario.id_logon_erp
     * @param dataVcto      só considera parcelas vencendo a partir desta data
     * @param funcAprovador prende ao funcionário aprovador informado; 0 não filtra
     */
    public List<Map<String, Object>> buscarSemAprovacao(int idLogon, LocalDate dataVcto, int funcAprovador) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            Date data = Date.valueOf(dataVcto);
            ps.setInt(1, idLogon);          // fn_funcionarioaprovaetapa
            ps.setDate(2, data);            // perna 1 do UNION
            ps.setDate(3, data);            // perna 2 do UNION
            ps.setInt(4, idLogon);          // fn_verificasupervisorde
            ps.setInt(5, funcAprovador);
            ps.setInt(6, funcAprovador);

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar parcelas de contrato sem aprovação (idLogon=" + idLogon
                    + ", dataVcto=" + dataVcto + ", funcAprovador=" + funcAprovador
                    + "): " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de parcelas para aprovação: " + e.getMessage(), e);
        }
    }
}
