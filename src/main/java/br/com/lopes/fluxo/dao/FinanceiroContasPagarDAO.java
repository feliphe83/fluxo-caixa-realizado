package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DAO da consulta de contas a pagar (fluxo de caixa a realizar) do
 * financeiro, com valores líquidos (baixas/acréscimos/descontos) e o
 * histórico de alterações de vencimento (até 10 por parcela) — usada como
 * fonte de dados do chatbot (via n8n) para perguntas financeiras.
 *
 * Período de vencimento é obrigatório; período de entrada e fornecedor são
 * opcionais. Usa a conexão do fluxo de caixa (usuário cpd), que já tem os
 * grants do schema financeiro.
 */
public class FinanceiroContasPagarDAO {

    private static final Logger LOG = Logger.getLogger(FinanceiroContasPagarDAO.class.getName());

    /** Teto absoluto de linhas devolvidas (protege memória do Tomcat/export). */
    private static final int MAX_TOTAL = 20000;

    private static final String FILTRO_ENTRADA =
        " and nvl(parcelascontaspagar.dataentradanf, parcelascontaspagar.dataentrada) between to_date(?, 'YYYY-MM-DD') and to_date(?, 'YYYY-MM-DD')\n";

    private static final String SQL = """
        select * from (
        select
            q.conta_fluxo,
            q.desc_fluxo,
            q.cod_grupoempenho,
            q.desc_grupoempenho,
            q.cod_empenho,
            q.descricao_empenho,
            q.cod_fornecedor,
            q.nome,
            q.documento,
            q.cod_tipocontaspagar,
            q.desc_contas_pagar,
            q.dataentrada,
            q.datavcto,
            q.valor,
            q.desc_origem,
            q.provisao,
            q.usuario,
            q.data_criacao,
            q.datavcto_orig,
            q.parcela,

            -- Alterações de vencimento (até 10)
            a.datavcto_ant_1, a.datavcto_novo_1, a.usuario_1,
            a.datavcto_ant_2, a.datavcto_novo_2, a.usuario_2,
            a.datavcto_ant_3, a.datavcto_novo_3, a.usuario_3,
            a.datavcto_ant_4, a.datavcto_novo_4, a.usuario_4,
            a.datavcto_ant_5, a.datavcto_novo_5, a.usuario_5,
            a.datavcto_ant_6, a.datavcto_novo_6, a.usuario_6,
            a.datavcto_ant_7, a.datavcto_novo_7, a.usuario_7,
            a.datavcto_ant_8, a.datavcto_novo_8, a.usuario_8,
            a.datavcto_ant_9, a.datavcto_novo_9, a.usuario_9,
            a.datavcto_ant_10, a.datavcto_novo_10, a.usuario_10

        from (
            SELECT
                origem,
                desc_origem,
                conta_fluxo,
                desc_fluxo,
                cod_grupoempenho,
                desc_grupoempenho,
                cod_empenho,
                Descricao_empenho,
                cod_fornecedor,
                nome,
                documento,
                cod_tipocontaspagar,
                desc_contas_pagar,
                dataentrada,
                datavcto,
                (VALORPARCELA - VLR_BAIXA + acrescimos - descontos) valor,
                provisao,
                usuario,
                data_criacao,
                datavcto_orig,
                parcela

            from (
                select
                    (select t.descricao from financeiro.tipocontaspagar t where t.cod_tipocontaspagar = tabela.cod_tipocontaspagar and t.datafim is null and t.cod_empresa = 1 and t.cod_filial = 1) desc_contas_pagar,
                    (select e.cod_grupoempenho from custo.empenho e where e.cod_empenho = tabela.cod_empenho) cod_grupoempenho,
                    (select gg.descricao from custo.empenho e, custo.grupoempenho gg where e.cod_empenho = tabela.cod_empenho and e.cod_grupoempenho = gg.cod_grupoempenho) desc_grupoempenho,
                    (select flc.cod_contafluxo from financeiro.fluxoempenho fl, financeiro.fluxoconta flc where fl.cod_planofluxo = 16 and fl.cod_empenho = tabela.cod_empenho and fl.cod_planofluxo = flc.cod_planofluxo and fl.cod_contafluxo = flc.cod_contafluxo) conta_fluxo,
                    (select flc.descricaoconta from financeiro.fluxoempenho fl, financeiro.fluxoconta flc where fl.cod_planofluxo = 16 and fl.cod_empenho = tabela.cod_empenho and fl.cod_planofluxo = flc.cod_planofluxo and fl.cod_contafluxo = flc.cod_contafluxo) desc_fluxo,
                    (select g.descricao from custo.empenho e, custo.grupoempenho g where e.cod_empenho = tabela.cod_empenho and e.cod_grupoempenho = g.cod_grupoempenho) grupoempenho,
                    (select o.descricao from financeiro.origem o where o.origem = tabela.origem) desc_origem,
                    tabela.numero_integracao,
                    tabela.cod_tipocontaspagar,
                    tabela.usuario,
                    tabela.data_criacao,
                    tabela.datavcto_orig,
                    tabela.cod_grupoempresa,
                    tabela.cod_empresa,
                    tabela.cod_filial,
                    tabela.documento,
                    tabela.parcela,
                    tabela.usuariopgto,
                    tabela.cod_situacao,
                    tabela.num_lancamentobancario,
                    tabela.valorparcela vlr_indice,
                    decode(tabela.datapgto, null,
                        (tabela.valorparcela * financeiro.busca_indicefinanceiro(tabela.cod_indicefinanceiro, nvl(NULL, tabela.datavcto))),
                        financeiro.Busca_BaixaParcelaVr_Liquido(tabela.COD_GRUPOEMPRESA, tabela.COD_TIPOCONTASPAGAR, tabela.DOCUMENTO, tabela.PARCELA)
                    ) valorparcela,
                    financeiro.busca_baixaparcelavr_liquido(tabela.cod_grupoempresa, tabela.cod_tipocontaspagar, tabela.documento, tabela.parcela) vlr_baixa,
                    financeiro.consulta_jurosdescontoparcela(tabela.cod_grupoempresa, tabela.cod_tipocontaspagar, tabela.documento, tabela.parcela, decode(tabela.data_indicefinanceiro, null, nvl(NULL, tabela.datavcto), tabela.data_indicefinanceiro), 'C') Acrescimos,
                    financeiro.consulta_jurosdescontoparcela(tabela.cod_grupoempresa, tabela.cod_tipocontaspagar, tabela.documento, tabela.parcela, decode(tabela.data_indicefinanceiro, null, nvl(NULL, tabela.datavcto), tabela.data_indicefinanceiro), 'D') Descontos,
                    tabela.datavcto,
                    tabela.datapgto,
                    tabela.cod_indicefinanceiro,
                    tabela.provisao,
                    geral.fn_obtem_nome(tabela.cod_fornecedor, tabela.cpf) nome,
                    tabela.origem,
                    tabela.cod_fornecedor,
                    tabela.cod_empenho,
                    (select empenho.descricao from custo.empenho where empenho.cod_empenho = tabela.cod_empenho) Descricao_empenho,
                    tabela.dataentrada
                from   (
                         --BAIXADAS
                         select parcelascontaspagar.numero_integracao
                              , parcelascontaspagar.cod_tipocontaspagar
                              , parcelascontaspagar.usuario
                              , parcelascontaspagar.data_criacao
                              , parcelascontaspagar.datavcto_orig
                              , parcelascontaspagar.cod_grupoempresa
                              , parcelascontaspagar.cod_empresa
                              , parcelascontaspagar.cod_filial
                              , parcelascontaspagar.documento
                              , parcelascontaspagar.parcela
                              , parcelascontaspagar.usuariopgto
                              , parcelascontaspagar.cod_situacao
                              , parcelascontaspagar.num_lancamentobancario
                              , parcelascontaspagar.datavcto
                              , parcelascontaspagar.datapgto
                              , parcelascontaspagar.cod_indicefinanceiro
                              , parcelascontaspagar.provisao
                              , parcelascontaspagar.origem
                              , parcelascontaspagar.cod_fornecedor
                              , parcelascontaspagar.cod_empenho
                              , parcelascontaspagar.cpf
                              , parcelascontaspagar.valorparcela
                              , parcelascontaspagar.valorindexado
                              , parcelascontaspagar.data_indicefinanceiro
                              , parcelascontaspagar.dataentrada
                         from   financeiro.parcelascontaspagar parcelascontaspagar
                         where  parcelascontaspagar.pagarreceber = 'P'
                         and    parcelascontaspagar.datavcto between to_date(?, 'YYYY-MM-DD') and to_date(?, 'YYYY-MM-DD')
                         /*FILTRO_ENTRADA*/
                         and    parcelascontaspagar.parcelabaixada = 'S'
                         and    parcelascontaspagar.cod_filial = 1
                         and    parcelascontaspagar.cod_empresa = 1
                         and    parcelascontaspagar.cod_grupoempresa = 1

                         union all

                         --NAO APROVADAS / A PAGAR
                         select pcp.numero_integracao
                              , pcp.cod_tipocontaspagar
                              , pcp.usuario
                              , pcp.data_criacao
                              , pcp.datavcto_orig
                              , pcp.cod_grupoempresa
                              , pcp.cod_empresa
                              , pcp.cod_filial
                              , pcp.documento
                              , pcp.parcela
                              , pcp.usuariopgto
                              , pcp.cod_situacao
                              , pcp.num_lancamentobancario
                              , pcp.datavcto
                              , pcp.datapgto
                              , pcp.cod_indicefinanceiro
                              , pcp.provisao
                              , pcp.origem
                              , pcp.cod_fornecedor
                              , pcp.cod_empenho
                              , pcp.cpf
                              , pcp.valorparcela
                              , pcp.valorindexado
                              , pcp.data_indicefinanceiro
                              , pcp.dataentrada
                         from  (
                                select parcelascontaspagar.cod_grupoempresa
                                     , parcelascontaspagar.cod_tipocontaspagar
                                     , parcelascontaspagar.documento
                                     , parcelascontaspagar.parcela
                                from    financeiro.situacaoparcelascontaspagar
                                      , financeiro.parcelascontaspagar
                                where  parcelascontaspagar.pagarreceber = 'P'
                                and    parcelascontaspagar.cod_situacao <> 13
                                and    parcelascontaspagar.datavcto between to_date(?, 'YYYY-MM-DD') and to_date(?, 'YYYY-MM-DD')
                                /*FILTRO_ENTRADA*/
                                and    parcelascontaspagar.cod_situacao            = situacaoparcelascontaspagar.cod_situacao
                                and    situacaoparcelascontaspagar.liberapagamento = 'N'
                                and    nvl(situacaoparcelascontaspagar.visualizar_parcela,'S') = 'S'
                                and    parcelascontaspagar.cod_filial = 1
                                and    parcelascontaspagar.cod_empresa = 1
                                and    parcelascontaspagar.cod_grupoempresa = 1

                                union

                                select parcelascontaspagar.cod_grupoempresa
                                     , parcelascontaspagar.cod_tipocontaspagar
                                     , parcelascontaspagar.documento
                                     , parcelascontaspagar.parcela
                                from    financeiro.situacaoparcelascontaspagar
                                      , financeiro.parcelascontaspagar
                                where  parcelascontaspagar.pagarreceber = 'P'
                                and    parcelascontaspagar.cod_situacao = 13
                                and    parcelascontaspagar.datavcto between to_date(?, 'YYYY-MM-DD') and to_date(?, 'YYYY-MM-DD')
                                /*FILTRO_ENTRADA*/
                                and    parcelascontaspagar.cod_situacao            = situacaoparcelascontaspagar.cod_situacao
                                and    situacaoparcelascontaspagar.liberapagamento = 'N'
                                and    nvl(situacaoparcelascontaspagar.visualizar_parcela,'S') = 'S'
                                and    parcelascontaspagar.cod_filial = 1
                                and    parcelascontaspagar.cod_empresa = 1
                                and    parcelascontaspagar.cod_grupoempresa = 1

                                union

                                select parcelascontaspagar.cod_grupoempresa
                                     , parcelascontaspagar.cod_tipocontaspagar
                                     , parcelascontaspagar.documento
                                     , parcelascontaspagar.parcela
                                from   financeiro.situacaoparcelascontaspagar
                                     , financeiro.parcelascontaspagar
                                where  parcelascontaspagar.pagarreceber = 'P'
                                and    parcelascontaspagar.datavcto between to_date(?, 'YYYY-MM-DD') and to_date(?, 'YYYY-MM-DD')
                                /*FILTRO_ENTRADA*/
                                and    parcelascontaspagar.cod_situacao            = situacaoparcelascontaspagar.cod_situacao
                                and    situacaoparcelascontaspagar.liberapagamento = 'S'
                                and    nvl(situacaoparcelascontaspagar.visualizar_parcela,'S') = 'S'
                                and    parcelascontaspagar.datapgto                is null
                                and    parcelascontaspagar.cod_filial = 1
                                and    parcelascontaspagar.cod_empresa = 1
                                and    parcelascontaspagar.cod_grupoempresa = 1
                                ) aux
                             , financeiro.parcelascontaspagar pcp
                         where pcp.parcela             = aux.parcela
                         and   pcp.documento           = aux.documento
                         and   pcp.cod_tipocontaspagar = aux.cod_tipocontaspagar
                         and   pcp.cod_grupoempresa    = aux.cod_grupoempresa
                 ) tabela
                 where tabela.cod_tipocontaspagar not in (817,789)
            )
        ) q
        left join (
            -- Alterações de vencimento (log), pivotadas em até 10 colunas
            select b.documento,
                   b.parcela,
                   b.cod_tipocontaspagar,
                   max(case when rn = 1 then datavcto_anterior end) as datavcto_ant_1,
                   max(case when rn = 1 then datavcto end)         as datavcto_novo_1,
                   max(case when rn = 1 then logon end)            as usuario_1,
                   max(case when rn = 2 then datavcto_anterior end) as datavcto_ant_2,
                   max(case when rn = 2 then datavcto end)         as datavcto_novo_2,
                   max(case when rn = 2 then logon end)            as usuario_2,
                   max(case when rn = 3 then datavcto_anterior end) as datavcto_ant_3,
                   max(case when rn = 3 then datavcto end)         as datavcto_novo_3,
                   max(case when rn = 3 then logon end)            as usuario_3,
                   max(case when rn = 4 then datavcto_anterior end) as datavcto_ant_4,
                   max(case when rn = 4 then datavcto end)         as datavcto_novo_4,
                   max(case when rn = 4 then logon end)            as usuario_4,
                   max(case when rn = 5 then datavcto_anterior end) as datavcto_ant_5,
                   max(case when rn = 5 then datavcto end)         as datavcto_novo_5,
                   max(case when rn = 5 then logon end)            as usuario_5,
                   max(case when rn = 6 then datavcto_anterior end) as datavcto_ant_6,
                   max(case when rn = 6 then datavcto end)         as datavcto_novo_6,
                   max(case when rn = 6 then logon end)            as usuario_6,
                   max(case when rn = 7 then datavcto_anterior end) as datavcto_ant_7,
                   max(case when rn = 7 then datavcto end)         as datavcto_novo_7,
                   max(case when rn = 7 then logon end)            as usuario_7,
                   max(case when rn = 8 then datavcto_anterior end) as datavcto_ant_8,
                   max(case when rn = 8 then datavcto end)         as datavcto_novo_8,
                   max(case when rn = 8 then logon end)            as usuario_8,
                   max(case when rn = 9 then datavcto_anterior end) as datavcto_ant_9,
                   max(case when rn = 9 then datavcto end)         as datavcto_novo_9,
                   max(case when rn = 9 then logon end)            as usuario_9,
                   max(case when rn = 10 then datavcto_anterior end) as datavcto_ant_10,
                   max(case when rn = 10 then datavcto end)         as datavcto_novo_10,
                   max(case when rn = 10 then logon end)            as usuario_10
            from (
                select b.*,
                       row_number() over (partition by b.documento, b.parcela order by b.id_pactpa_log) as rn
                from (
                    select
                        parclog.documento,
                        parclog.parcela,
                        parclog.cod_tipocontaspagar,
                        parclog.id_pactpa_log,
                        parclog.datavcto,
                        lag(parclog.datavcto) over (
                            partition by parclog.documento, parclog.parcela
                            order by parclog.id_pactpa_log
                        ) as datavcto_anterior,
                        parclog.logon
                    from financeiro.parcelascontaspagar_log parclog
                    where parclog.pagarreceber = 'P'
                      and parclog.cod_grupoempresa = 1
                      and parclog.cod_empresa = 1
                      and parclog.cod_filial = 1
                ) b
                where (b.datavcto_anterior is null and b.datavcto is not null)
                   or (b.datavcto_anterior is not null and b.datavcto is null)
                   or (b.datavcto_anterior <> b.datavcto)
            ) b
            group by b.documento, b.parcela, b.cod_tipocontaspagar
        ) a
          on a.documento = q.documento
         and a.parcela   = q.parcela
         and a.cod_tipocontaspagar = q.cod_tipocontaspagar
        order by q.datavcto, q.documento, q.parcela
        ) where nvl(valor, 0) <> 0
          and rownum <= %d
        /*FILTRO_FORNECEDOR*/
        """.formatted(MAX_TOTAL);

    /**
     * @param dataIniVcto    obrigatório, yyyy-MM-dd (vencimento >=)
     * @param dataFimVcto    obrigatório, yyyy-MM-dd (vencimento <=)
     * @param dataIniEntrada opcional (com dataFimEntrada), yyyy-MM-dd
     * @param dataFimEntrada opcional (com dataIniEntrada), yyyy-MM-dd
     * @param fornecedor     opcional, trecho do nome do fornecedor
     */
    public List<Map<String, Object>> buscar(String dataIniVcto, String dataFimVcto,
                                            String dataIniEntrada, String dataFimEntrada,
                                            String fornecedor) {

        boolean temEntrada = dataIniEntrada != null && !dataIniEntrada.isBlank()
                          && dataFimEntrada != null && !dataFimEntrada.isBlank();

        String sql = SQL.replace("/*FILTRO_ENTRADA*/", temEntrada ? FILTRO_ENTRADA : "");

        boolean temFornecedor = fornecedor != null && !fornecedor.isBlank();
        sql = sql.replace("/*FILTRO_FORNECEDOR*/",
                temFornecedor ? "and upper(nome) like '%'||upper(?)||'%'" : "");

        // O período de vencimento (e o de entrada, se houver) repete em cada um
        // dos 4 blocos da união, na ordem em que os ? aparecem no SQL.
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            params.add(dataIniVcto.trim());
            params.add(dataFimVcto.trim());
            if (temEntrada) {
                params.add(dataIniEntrada.trim());
                params.add(dataFimEntrada.trim());
            }
        }
        if (temFornecedor) params.add(fornecedor.trim());

        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar contas a pagar: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de contas a pagar: " + e.getMessage(), e);
        }
    }
}
