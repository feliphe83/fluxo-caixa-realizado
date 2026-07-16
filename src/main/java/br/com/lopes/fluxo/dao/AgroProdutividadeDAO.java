package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.AgroOracleConnectionUtil;
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
 * DAO da consulta de produtividade agrícola por ordem de colheita
 * (agricola.ordem_corte_unica e tabelas relacionadas): TCH realizado e
 * estimado, área cortada, produção realizada, ATR médio — usada como fonte
 * de dados do Dr. Alfredo (chat) para perguntas de produtividade/colheita.
 *
 * Não usa a geral.fn_autorizacao_empresa da consulta original do ERP: aquela
 * função valida a permissão do usuário logado no ERP, e a intranet conecta
 * com usuário de serviço próprio (empresa fixada em 1/1/1, como no original).
 *
 * Safra é obrigatória. Fazenda, talhão, frente, tipo de corte, variedade e
 * período da ordem de colheita são filtros opcionais, mas recomendados: uma
 * safra inteira pode ter milhares de ordens de colheita.
 *
 * Por padrão (agrupar nulo/vazio ou "geral"), devolve um único total: área
 * cortada, produção e TCH/ATR médios ponderados de toda a safra filtrada —
 * é o formato certo para "qual foi a produtividade da safra". Com
 * agrupar=fazenda, talhao ou variedade, a mesma soma/ponderação sai
 * quebrada por dimensão (para "produtividade por fazenda" ou "top N mais
 * produtivo"). O detalhamento linha a linha só entra com agrupar=detalhado,
 * pedido explicitamente — nesse modo o agente só recebe uma amostra
 * truncada e não deve tentar somar/ranquear a partir dela.
 */
public class AgroProdutividadeDAO {

    private static final Logger LOG = Logger.getLogger(AgroProdutividadeDAO.class.getName());

    /** Teto absoluto de linhas devolvidas (protege memória do Tomcat/export). */
    private static final int MAX_TOTAL = 20000;

    private static final String FROM_WHERE_BASE = """
        from   rh.pessoa pessoaAgenc
             , material.fornecedor fornecAgenc
             , rh.agenciador
             , agricola.vw_destinotalhao DestinoTalhao
             , agricola.ambiente_producao
             , agricola.ordem_colheita
             , agricola.irrigacaotipo
             , agricola.tipo_corte
             , agricola.idade_cana
             , agricola.frente
             , agricola.tipocana
             , agricola.tipo_solo
             , rh.pessoa
             , material.fornecedor
             , agricola.tipofazenda
             , agricola.cultura cultura
             , agricola.cultura culturatalhao
             , agricola.variedade
             , agricola.tipovariedade tipovariedade
             , agricola.talhao
             , agricola.regiao_agricola
             , agricola.historico_fazenda
             , agricola.fazenda
             , agricola.zona
             , agricola.finalidade_corte
             , agricola.liberacao_corte
             , agricola.ordem_corte_unica
             , agricola.setor_agricola
        where  pessoaAgenc.cod_pessoa              (+)= fornecAgenc.cod_pessoa
        and    fornecAgenc.cod_fornecedor          (+)= agenciador.cod_fornecedor

        and    agenciador.cod_grupoempresa         (+)= ordem_colheita.cod_grupoempresa
        and    agenciador.cod_agenciador           (+)= ordem_colheita.cod_agenciador

        and    cultura.cod_cultura                 (+)= variedade.cod_cultura
        and    culturatalhao.cod_cultura           (+)= talhao.cod_cultura

        and    tipovariedade.cod_tipovariedade     (+)= variedade.cod_tipovariedade
        and    variedade.cod_variedade                = nvl(ordem_corte_unica.cod_variedade,talhao.cod_variedade)

        and    ordem_colheita.cod_grupoempresa        = ordem_corte_unica.cod_grupoempresa
        and    ordem_colheita.cod_empresa             = ordem_corte_unica.cod_empresa
        and    ordem_colheita.cod_filial              = ordem_corte_unica.cod_filial
        and    ordem_colheita.cod_safra               = ordem_corte_unica.cod_safra
        and    ordem_colheita.numero_ordem            = ordem_corte_unica.nr_ordem_colheita

        and    irrigacaotipo.cod_tipoirrigacao    (+) = talhao.cod_tipoirrigacao

        AND    tipocana.cod_tipocana                  = ordem_corte_unica.cod_tipocana

        and    tipo_corte.cod_tipocorte               = ordem_corte_unica.cod_tipocorte
        and    idade_cana.cod_idade_cana              = talhao.numerocorte

        and    tipo_solo.cod_tiposolo                 = talhao.cod_tiposolo

        and    frente.cod_frente                      = ordem_corte_unica.cod_frente
        and    pessoa.cod_pessoa                      = fornecedor.cod_pessoa
        and    fornecedor.cod_fornecedor              = historico_fazenda.cod_fornecedor
        and    tipofazenda.cod_tipofazenda            = historico_fazenda.cod_tipofazenda

        and    setor_agricola.id_setoragricola        = historico_fazenda.id_setoragricola

        and    talhao.cod_fazenda                     = liberacao_corte.cod_fazenda
        and    talhao.zona                            = liberacao_corte.zona
        and    talhao.cod_talhao                      = liberacao_corte.cod_talhao
        and    talhao.cod_safradetalhe                = liberacao_corte.cod_safradetalhe
        and    talhao.cod_safra                       = liberacao_corte.cod_safra

        and    regiao_agricola.cod_regiaoagricola (+)     = historico_fazenda.cod_regiaoagricola
        and    rh.intersecao(ordem_corte_unica.data_ordem,ordem_corte_unica.data_ordem,historico_fazenda.data_inicio,historico_fazenda.data_fim) = 'TRUE'
        and    historico_fazenda.cod_fazenda          = fazenda.cod_fazenda

        and    fazenda.cod_fazenda                    = liberacao_corte.cod_fazenda
        and    finalidade_corte.cod_grupoempresa      = liberacao_corte.cod_grupoempresa
        and    finalidade_corte.cod_empresa           = liberacao_corte.cod_empresa
        and    finalidade_corte.cod_filial            = liberacao_corte.cod_filial
        and    finalidade_corte.cod_finalidade        = liberacao_corte.cod_finalidade
        and    liberacao_corte.cod_grupoempresa       = ordem_corte_unica.cod_grupoempresa
        and    liberacao_corte.cod_empresa            = ordem_corte_unica.cod_empresa
        and    liberacao_corte.cod_filial             = ordem_corte_unica.cod_filial
        and    liberacao_corte.cod_safradetalhe       = ordem_corte_unica.cod_safradetalhe
        and    liberacao_corte.cod_safra              = ordem_corte_unica.cod_safra
        and    liberacao_corte.cod_frente             = ordem_corte_unica.cod_frente
        and    liberacao_corte.data_liberacao         = ordem_corte_unica.data_liberacao
        and    liberacao_corte.numero_liberacao       = ordem_corte_unica.numero_liberacao

        and    liberacao_corte.cod_Fazenda            = ordem_corte_unica.cod_fazenda
        and    liberacao_corte.Zona                   = ordem_corte_unica.zona
        and    liberacao_corte.Cod_talhao             = ordem_corte_unica.cod_talhao

        and ordem_corte_unica.cod_filial = 1
        and ordem_corte_unica.cod_empresa = 1
        and ordem_corte_unica.cod_grupoempresa = 1

        and    ordem_corte_unica.cod_safra            = ?

        AND    zona.cod_fazenda = fazenda.cod_fazenda
        AND    talhao.zona = zona.cod_zona

        AND (liberacao_corte.cod_finalidade    = 1)

        and DestinoTalhao.destino          (+)= talhao.Destino_talhao
        and ambiente_producao.id_ambproduc (+)= talhao.cod_AmbienteProd

        and nvl(round(decode(ordem_corte_unica.area_corte,0,0,((decode(ordem_colheita.data_encerramento,null,nvl(ordem_corte_unica.producao_estimada,0),nvl(ordem_corte_unica.producao_realizada,0)) + nvl(ordem_corte_unica.pesomuda,0)) / ordem_corte_unica.area_corte)),2),0) between '0' and '999999'
        /*FILTROS*/
        """;

    private static final String COLUNAS_DETALHE = """
        select
             regiao_agricola.cod_regiaoagricola,
             regiao_agricola.descricao descricao_regiao,
             historico_fazenda.cod_fornecedor
             , pessoa.nome desc_fornecedor
             , tipofazenda.cod_tipofazenda
             , tipofazenda.descricao desc_tipofazenda
             , liberacao_corte.cod_fazenda
             , fazenda.descricao desc_fazenda
             , liberacao_corte.cod_talhao talhao
             , ordem_corte_unica.cod_frente
             , tipocana.descricao desc_tipocana
             , nvl(variedade.cod_cultura,talhao.cod_cultura) cod_cultura
             , nvl(cultura.desc_cultura,culturatalhao.desc_cultura) desc_cultura
             , variedade.cod_variedade
             , variedade.descricao desc_variedade
             , tipovariedade.cod_tipovariedade
             , tipovariedade.descricao desc_tipovariedade
             , talhao.numerocorte
             , idade_cana.descricao desc_corte
             , ordem_corte_unica.cod_tipocorte
             , tipo_corte.descricao desc_tipocorte
             , (ordem_corte_unica.nr_ordem_colheita) numero_ordem
             , ordem_corte_unica.data_ordem data_ordem
             , ordem_colheita.data_encerramento
             , talhao.cod_tiposolo
             , tipo_solo.descricao desc_tiposolo
             , (decode(0,'S',ordem_corte_unica.area_corte,decode(ordem_colheita.data_encerramento,NULL,0,ordem_corte_unica.area_corte))) areacortada
             , (decode(0,'S',nvl(ordem_corte_unica.producao_realizada,0)+ nvl(ordem_corte_unica.pesomuda,0)
                       ,decode(ordem_colheita.data_encerramento,NULL,0,nvl(ordem_corte_unica.producao_realizada,0)+nvl(ordem_corte_unica.pesomuda,0)))) producaorealizada
             , NVL(round(DECODE(decode(0,'S',ordem_corte_unica.area_corte,decode(ordem_colheita.data_encerramento,NULL,0,ordem_corte_unica.area_corte))
                       ,0
                       ,0
                       , (decode(0,'S',nvl(ordem_corte_unica.producao_realizada,0)+ nvl(ordem_corte_unica.pesomuda,0)
                            ,decode(ordem_colheita.data_encerramento,NULL,0,nvl(ordem_corte_unica.producao_realizada,0)+nvl(ordem_corte_unica.pesomuda,0)))))/(decode(0,'S',ordem_corte_unica.area_corte,decode(ordem_colheita.data_encerramento,NULL,NULL,ordem_corte_unica.area_corte))),2),0) tchrealizad
             , nvl(AGRICOLA.FN_IDADEVEGETATIVA(talhao.cod_safra
                                                , talhao.cod_fazenda
                                                , talhao.zona
                                                , talhao.cod_talhao
                                                , talhao.cod_safradetalhe
                                                , talhao.data_corte_safra_anterior
                                                , talhao.dataplantio
                                                , talhao.DATAENCERRAMENTO
                                                ),0) idade_veg_corte
             , irrigacaotipo.descricao desc_tipoirrigacao
             , decode((nvl(talhao.areaplantada, 0) * nvl(talhao.rendimentoagricola, 0)), 0
                                                                                       , 0
                                                                                       , (nvl(talhao.areaplantada, 0) *
                                                                                          nvl(talhao.rendimentoagricola, 0) *
                                                                                          agricola.fn_distancia(ordem_corte_unica.COD_GRUPOEMPRESA, ordem_corte_unica.COD_EMPRESA, ordem_corte_unica.COD_FILIAL, talhao.cod_fazenda, talhao.zona, talhao.cod_talhao, 'T')
                                                                                          ) /
                                                                                          (nvl(talhao.areaplantada, 0) * nvl(talhao.rendimentoagricola, 0))
                     ) raio_medio
             , DECODE(0
                 , 0
                 , ordem_corte_unica.producao_estimada
                 , ordem_corte_unica.area_corte *
                   ordem_corte_unica.area_corte *(nvl((agricola.fn_estimativatalhao(ordem_corte_unica.cod_grupoempresa,
                                                                                       ordem_corte_unica.cod_empresa,
                                                                                       ordem_corte_unica.cod_filial,
                                                                                       talhao.cod_safra,
                                                                                       talhao.cod_fazenda,
                                                                                       talhao.zona,
                                                                                       talhao.cod_talhao,
                                                                                       0,
                                                                                       'TCH')), ORDEM_CORTE_UNICA.TCH_ESTIMADO)) / ordem_corte_unica.area_corte
                  ) producaoestimada
             , DECODE(0
                , 0
                , round(decode(ordem_corte_unica.area_corte,0,0,ordem_corte_unica.producao_estimada/ordem_corte_unica.area_corte),2)
                , ordem_corte_unica.area_corte *(nvl((agricola.fn_estimativatalhao(ordem_corte_unica.cod_grupoempresa,
                                                                                       ordem_corte_unica.cod_empresa,
                                                                                       ordem_corte_unica.cod_filial,
                                                                                       talhao.cod_safra,
                                                                                       talhao.cod_fazenda,
                                                                                       talhao.zona,
                                                                                       talhao.cod_talhao,
                                                                                       0,
                                                                                       'TCH')), ORDEM_CORTE_UNICA.TCH_ESTIMADO)) / ordem_corte_unica.area_corte
                 )
           tchestimado
             , nvl(talhao.areaproducao, 0) areaproducao
             , nvl(talhao.areaplantada, 0) areaplantada
             , nvl(talhao.rendimentoagricola, 0) rendimentoagricola
             , (SELECT NVL(ROUND(SUM(decode(tipocana.imprime_posqueima,'N',0,itensentradacana.qtdehorasposqueima)
                                  *
                                  itensentradacana.pesoliquido) / SUM(itensentradacana.pesoliquido), 2), 0)
                FROM   agricola.itensentradacana itensentradacana
                WHERE  itensentradacana.cod_grupoempresa       = ordem_corte_unica.cod_grupoempresa
                AND    itensentradacana.cod_empresa            = ordem_corte_unica.cod_empresa
                AND    itensentradacana.cod_filial             = ordem_corte_unica.cod_filial
                AND    itensentradacana.cod_safra              = ordem_corte_unica.cod_safra
                AND    itensentradacana.numeroordemcorte       = ordem_corte_unica.numero_ordem
                AND    itensentradacana.pesoliquido            > 0
               ) qtdehorasposqueima

             , (SELECT NVL(SUM(itensentradacana.pesoliquido), 0)
                FROM   agricola.itensentradacana itensentradacana
                WHERE  itensentradacana.cod_grupoempresa       = ordem_corte_unica.cod_grupoempresa
                AND    itensentradacana.cod_empresa            = ordem_corte_unica.cod_empresa
                AND    itensentradacana.cod_filial             = ordem_corte_unica.cod_filial
                AND    itensentradacana.cod_safra              = ordem_corte_unica.cod_safra
                AND    itensentradacana.numeroordemcorte       = ordem_corte_unica.numero_ordem
               ) pesoliquido
             , liberacao_corte.cod_fazenda||'-'||liberacao_corte.zona||'-'||liberacao_corte.cod_talhao FazZonaTalhao
             , nvl(round((SELECT SUM(analise_pcts.Atr * analise_pcts.pesoliquido) / SUM(analise_pcts.pesoliquido)
                        FROM agricola.analise_pcts
                           , agricola.itensentradacana
                       WHERE analise_pcts.cod_grupoempresa     = itensentradacana.cod_grupoempresa
                         AND analise_pcts.cod_empresa          = itensentradacana.cod_empresa
                         AND analise_pcts.cod_filial           = itensentradacana.cod_filial
                         AND analise_pcts.cod_safra            = itensentradacana.cod_safra
                         AND analise_pcts.cod_entradacana      = itensentradacana.cod_entradacana
                         AND analise_pcts.seq_itensentradacana = itensentradacana.seq_itensentradacana
                         AND itensentradacana.cod_grupoempresa = ordem_corte_unica.cod_grupoempresa
                         AND itensentradacana.cod_empresa      = ordem_corte_unica.cod_empresa
                         AND itensentradacana.cod_filial       = ordem_corte_unica.cod_filial
                         AND itensentradacana.cod_safra        = ordem_corte_unica.cod_safra
                         AND itensentradacana.numeroordemcorte = ordem_corte_unica.numero_ordem ),4),0) ATR_Media
        """;

    private static final String SQL_DETALHADO = """
        select * from (
        %s
        %s
        order by ordem_corte_unica.nr_ordem_colheita
        ) where rownum <= %d
        """.formatted(COLUNAS_DETALHE, FROM_WHERE_BASE, MAX_TOTAL);

    /** Média de TCH/ATR ponderada pela produção/peso — não a média simples das linhas. */
    private static final String SQL_POR_FAZENDA = """
        select * from (
        select cod_fazenda, desc_fazenda
             , round(sum(areacortada), 2) area_cortada
             , round(sum(producaorealizada), 2) producao_realizada_ton
             , round(decode(sum(areacortada),0,0,sum(producaorealizada)/sum(areacortada)),2) tch_medio
             , round(decode(sum(pesoliquido),0,0,sum(ATR_Media*pesoliquido)/sum(pesoliquido)),4) atr_medio
             , count(*) qtde_ordens
        from ( %s %s )
        group by cod_fazenda, desc_fazenda
        order by tch_medio desc
        ) where rownum <= %d
        """.formatted(COLUNAS_DETALHE, FROM_WHERE_BASE, MAX_TOTAL);

    private static final String SQL_POR_TALHAO = """
        select * from (
        select cod_fazenda, desc_fazenda, talhao
             , round(sum(areacortada), 2) area_cortada
             , round(sum(producaorealizada), 2) producao_realizada_ton
             , round(decode(sum(areacortada),0,0,sum(producaorealizada)/sum(areacortada)),2) tch_medio
             , round(decode(sum(pesoliquido),0,0,sum(ATR_Media*pesoliquido)/sum(pesoliquido)),4) atr_medio
             , count(*) qtde_ordens
        from ( %s %s )
        group by cod_fazenda, desc_fazenda, talhao
        order by tch_medio desc
        ) where rownum <= %d
        """.formatted(COLUNAS_DETALHE, FROM_WHERE_BASE, MAX_TOTAL);

    private static final String SQL_POR_VARIEDADE = """
        select * from (
        select cod_variedade, desc_variedade
             , round(sum(areacortada), 2) area_cortada
             , round(sum(producaorealizada), 2) producao_realizada_ton
             , round(decode(sum(areacortada),0,0,sum(producaorealizada)/sum(areacortada)),2) tch_medio
             , round(decode(sum(pesoliquido),0,0,sum(ATR_Media*pesoliquido)/sum(pesoliquido)),4) atr_medio
             , count(*) qtde_ordens
        from ( %s %s )
        group by cod_variedade, desc_variedade
        order by tch_medio desc
        ) where rownum <= %d
        """.formatted(COLUNAS_DETALHE, FROM_WHERE_BASE, MAX_TOTAL);

    /** Total geral (uma única linha, sem quebra por dimensão) — usado quando a pergunta não pede detalhamento. */
    private static final String SQL_GERAL = """
        select
               round(sum(areacortada), 2) area_cortada
             , round(sum(producaorealizada), 2) producao_realizada_ton
             , round(decode(sum(areacortada),0,0,sum(producaorealizada)/sum(areacortada)),2) tch_medio
             , round(decode(sum(pesoliquido),0,0,sum(ATR_Media*pesoliquido)/sum(pesoliquido)),4) atr_medio
             , count(*) qtde_ordens
        from ( %s %s )
        """.formatted(COLUNAS_DETALHE, FROM_WHERE_BASE);

    /**
     * @param codSafra    obrigatório
     * @param codFazenda  opcional
     * @param codTalhao   opcional
     * @param codFrente   opcional
     * @param codTipoCorte opcional
     * @param variedade   opcional, trecho da descrição da variedade
     * @param dataIni     opcional (com dataFim), yyyy-MM-dd — data da ordem de colheita
     * @param dataFim     opcional (com dataIni), yyyy-MM-dd
     * @param agrupar     opcional: null/"geral" (padrão — total único, sem
     *                    quebra), "fazenda", "talhao" ou "variedade" (soma
     *                    área/produção e pondera TCH/ATR por dimensão) ou
     *                    "detalhado" (linha a linha, só quando pedido
     *                    explicitamente)
     */
    public List<Map<String, Object>> buscar(String codSafra, Integer codFazenda, Integer codTalhao,
                                            Integer codFrente, Integer codTipoCorte, String variedade,
                                            String dataIni, String dataFim, String agrupar) {

        StringBuilder filtros = new StringBuilder();
        List<Object> params = new ArrayList<>();
        params.add(codSafra);

        if (codFazenda != null) {
            filtros.append(" and ordem_corte_unica.cod_fazenda = ?\n");
            params.add(codFazenda);
        }
        if (codTalhao != null) {
            filtros.append(" and ordem_corte_unica.cod_talhao = ?\n");
            params.add(codTalhao);
        }
        if (codFrente != null) {
            filtros.append(" and ordem_corte_unica.cod_frente = ?\n");
            params.add(codFrente);
        }
        if (codTipoCorte != null) {
            filtros.append(" and ordem_corte_unica.cod_tipocorte = ?\n");
            params.add(codTipoCorte);
        }
        if (variedade != null && !variedade.isBlank()) {
            filtros.append(" and upper(variedade.descricao) like '%'||upper(?)||'%'\n");
            params.add(variedade.trim());
        }
        if (dataIni != null && !dataIni.isBlank() && dataFim != null && !dataFim.isBlank()) {
            filtros.append(" and ordem_corte_unica.data_ordem >= to_date(?, 'YYYY-MM-DD')\n");
            filtros.append(" and ordem_corte_unica.data_ordem <= to_date(?, 'YYYY-MM-DD')\n");
            params.add(dataIni.trim());
            params.add(dataFim.trim());
        }

        String template = switch (agrupar == null ? "" : agrupar.trim().toLowerCase()) {
            case "fazenda" -> SQL_POR_FAZENDA;
            case "talhao" -> SQL_POR_TALHAO;
            case "variedade" -> SQL_POR_VARIEDADE;
            case "detalhado" -> SQL_DETALHADO;
            default -> SQL_GERAL;
        };
        String sql = template.replace("/*FILTROS*/", filtros.toString());

        try (Connection conn = AgroOracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar produtividade: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de produtividade: " + e.getMessage(), e);
        }
    }
}
