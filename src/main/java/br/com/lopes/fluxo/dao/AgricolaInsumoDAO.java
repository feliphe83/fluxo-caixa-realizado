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
 * DAO da consulta de insumos agrícolas aplicados (agricola.vw_apontamento,
 * tipoinsumo = 'M'), agregada por processo/atividade/operação/fazenda/talhão/
 * insumo/data — usada como fonte de dados do chatbot agrícola (via n8n) para
 * perguntas sobre insumos (adubo, herbicida etc.).
 *
 * Filtro por safra é obrigatório. Fazenda, período e descrição do insumo são
 * opcionais, mas recomendados: a safra inteira pode ter dezenas de milhares
 * de linhas.
 *
 * Com agrupar=insumo, a utilização/área/custo vêm somadas direto no banco
 * por insumo (ordenado do maior para o menor consumo), com custo ponderado
 * (valor/utilização, não a média simples das linhas) — necessário para
 * perguntas de "total por insumo", já que o modo detalhado só expõe uma
 * amostra truncada (linha a linha, por dia/talhão) ao agente.
 */
public class AgricolaInsumoDAO {

    private static final Logger LOG = Logger.getLogger(AgricolaInsumoDAO.class.getName());

    /** Teto absoluto de linhas devolvidas (protege memória do Tomcat/export). */
    private static final int MAX_TOTAL = 20000;

    // Nível intermediário: mesma consulta original, agregada por dia/talhão/
    // insumo/etc., com o acréscimo de sum(valor_realizado) — necessário para
    // o modo agrupar=insumo poder somar o custo corretamente num segundo
    // nível de agregação (média ponderada, não média das médias).
    private static final String SUBQUERY_DETALHE = """
        SELECT ano
             , numero
             ,PROCESSO PROCESSO
               , DESC_PROCESSO
               , SUBPROCESSO SUBPROCESSO
               , DESC_SUBPROCESSO
               , COD_ATIVIDADE COD_ATIVIDADE
               , DESC_ATIVIDADE
               , COD_OPERACAO1
               , DESC_OPERACAO
               , data
               , COD_FAZENDA COD_FAZENDA
               , DESC_FAZENDA
               ,COD_REGIAOAGRICOLA
        ,DESC_REGIAOAGRICOLA1
               , cod_talhao
               , zona
              -- , FazZonaTalhaoArea

               , COD_INSUMO COD_INSUMO
               , DESC_INSUMO
               , sum(AREA) area
               , sum(UTILIZACAO) UTILIZACAO
               , UNIDADE_UTILIZACAO



              , decode(SUM(area),0,0,  SUM(UTILIZACAO) / SUM(area ) ) Media
            , decode(SUM(area),0,0,  SUM(valor_realizado) / SUM(area ) ) CustoHA
             , nvl(decode(SUM(utilizacao),0,0, SUM(valor_realizado) / SUM(utilizacao ) ), 0) CustoMedio
             , sum(valor_realizado) valor_total
             ,ano_ordemservico , nr_ordemservico
        from   (

        SELECT ano
             , numero
             , item
             , chaveArea
             , area
             , decode(lag(to_number(ano|| numero||item||cod_fazenda||zona||cod_talhao),1,0) over(partition BY chaveArea order by chaveArea, decode(tipo_insumo  ,'E',areaequipamento,'M',areamaterial,'C',areacargo)),0,decode(controle_area,'E',areaequipamento
                                                                                                                                                                                                                                             ,'M',areamaterial
                                                                                                                                                                                                                                ,'C',areacargo),null) xAREA
             , mesano
             , mesano  DESC_MESANO1
             , DESC_MESANO2
             , cod_tipofazenda
             , desc_tipofazenda1
             , desc_tipofazenda2
             , COD_FORNECEDOR
             , DESC_FORNECEDOR1
             , DESC_FORNECEDOR2
             , COD_TIPOIRRIGACAO
             , DESC_TIPOIRRIGACAO1
             , DESC_TIPOIRRIGACAO2
             , COD_REGIAOAGRICOLA
             , DESC_REGIAOAGRICOLA1
             , DESC_REGIAOAGRICOLA2
             , negocio
             , desc_negocio1
             , desc_negocio2
             , processo
             , desc_processo
             , desc_processo1
             , desc_processo2
             , subprocesso
             , desc_subprocesso
             , desc_subprocesso1
             , desc_subprocesso2
             , cod_atividade
             , desc_atividade
             , desc_atividade1
             , desc_atividade2
             , cod_operacao
             , desc_operacao
             , desc_operacao1
             , desc_operacao2
             , tipo_operacao
             , cod_operacao||tipo_operacao Chaveoperacao
             , COD_OPERACAO1
             , tipo_insumo
             , desc_tipoinsumo1
             , desc_tipoinsumo2
             , cod_insumo
             , desc_insumo
             , desc_insumo1
             , desc_insumo2
             , fazzonatalhao1
             , fazzonatalhao2
             , FazZona1
             , zona
             , cod_talhao
             , cod_fazenda
             , desc_fazenda
             , desc_fazenda1
             , desc_fazenda2
             , utilizacao utilizacao_old
             , NVL(to_char(trunc(vw_ordemservico.DATA)-( SELECT trunc(max(o.data_encerramento))
                   FROM   agricola.ordem_corte_unica o
                   WHERE  o.cod_grupoempresa   = vw_ordemservico.cod_grupoempresa
                   AND    o.cod_empresa        = vw_ordemservico.cod_empresa
                   AND    o.cod_filial         = vw_ordemservico.cod_filial
                   AND    o.cod_fazenda        = vw_ordemservico.cod_fazenda
                   AND    o.zona               = vw_ordemservico.zona
                   AND    o.cod_talhao         = vw_ordemservico.cod_talhao
                   AND    o.data_encerramento <= vw_ordemservico.data)),'--') defasagem
             , DESC_BLOCO1
             , DESC_BLOCO2
             , COD_DESTINO
             , DESC_DESTINO
             , DESC_DESTINO2
             , cod_ambienteprod
             , DESC_AMBIENTEPROD
             , DESC_AMBIENTEPROD2
             , cod_cidadeFazenda
             , desc_cidadeFazenda1
             , desc_cidadeFazenda2
             , valor_unitario_realizado
             , valor_realizado

             , nvl((select material.f_calcula_expressao(replace(vw_ordemservico.utilizacao,',','.')
                                                     || conversao_unidade.formula_conversao)
                    from   agricola.conversao_unidade
                    where  conversao_unidade.cod_grupoempresa     = vw_ordemservico.cod_grupoempresa
                    and    conversao_unidade.cod_empresa          = vw_ordemservico.cod_empresa
                    and    conversao_unidade.cod_filial           = vw_ordemservico.cod_filial
                    and    conversao_unidade.cod_unidade_original = vw_ordemservico.cod_unidade
                    and    conversao_unidade.cod_tipofazenda      = vw_ordemservico.cod_tipofazenda
                    and    conversao_unidade.cod_objetocusto      = vw_ordemservico.cod_objetocusto
                    and    conversao_unidade.cod_operacao         = vw_ordemservico.cod_operacao

                    and    conversao_unidade.tipo_operacao        = vw_ordemservico.tipo_operacao), vw_ordemservico.utilizacao) utilizacao

             , decode(area,0
                    , 0
                    , decode(nvl((select material.f_calcula_expressao(replace(vw_ordemservico.utilizacao,',','.')
                                                                   || conversao_unidade.formula_conversao)
                                  from   agricola.conversao_unidade
                                  where  conversao_unidade.cod_grupoempresa     = vw_ordemservico.cod_grupoempresa
                                  and    conversao_unidade.cod_empresa          = vw_ordemservico.cod_empresa
                                  and    conversao_unidade.cod_filial           = vw_ordemservico.cod_filial
                                  and    conversao_unidade.cod_unidade_original = vw_ordemservico.cod_unidade
                                  and    conversao_unidade.cod_tipofazenda      = vw_ordemservico.cod_tipofazenda
                                  and    conversao_unidade.cod_objetocusto      = vw_ordemservico.cod_objetocusto
                                  and    conversao_unidade.cod_operacao         = vw_ordemservico.cod_operacao
                                  and    conversao_unidade.tipo_operacao        = vw_ordemservico.tipo_operacao), vw_ordemservico.utilizacao),0,0,
                      area /
                            nvl((select material.f_calcula_expressao(replace(vw_ordemservico.utilizacao,',','.')
                                                                  || conversao_unidade.formula_conversao)
                                 from   agricola.conversao_unidade
                                 where  conversao_unidade.cod_grupoempresa     = vw_ordemservico.cod_grupoempresa
                                 and    conversao_unidade.cod_empresa          = vw_ordemservico.cod_empresa
                                 and    conversao_unidade.cod_filial           = vw_ordemservico.cod_filial
                                 and    conversao_unidade.cod_unidade_original = vw_ordemservico.cod_unidade
                                 and    conversao_unidade.cod_tipofazenda      = vw_ordemservico.cod_tipofazenda
                                 and    conversao_unidade.cod_objetocusto      = vw_ordemservico.cod_objetocusto
                                 and    conversao_unidade.cod_operacao         = vw_ordemservico.cod_operacao
                                 and    conversao_unidade.tipo_operacao        = vw_ordemservico.tipo_operacao), vw_ordemservico.utilizacao))
                     ) rendimento_hr_unid

             , decode(area,0
                    , 0
                    , decode(area,0,0,
                      nvl((select material.f_calcula_expressao(replace(vw_ordemservico.utilizacao,',','.')
                                                                  || conversao_unidade.formula_conversao)
                                 from   agricola.conversao_unidade
                                 where  conversao_unidade.cod_grupoempresa     = vw_ordemservico.cod_grupoempresa
                                 and    conversao_unidade.cod_empresa          = vw_ordemservico.cod_empresa
                                 and    conversao_unidade.cod_filial           = vw_ordemservico.cod_filial
                                 and    conversao_unidade.cod_unidade_original = vw_ordemservico.cod_unidade
                                 and    conversao_unidade.cod_tipofazenda      = vw_ordemservico.cod_tipofazenda
                                 and    conversao_unidade.cod_objetocusto      = vw_ordemservico.cod_objetocusto
                                 and    conversao_unidade.cod_operacao         = vw_ordemservico.cod_operacao
                                 and    conversao_unidade.tipo_operacao        = vw_ordemservico.tipo_operacao), vw_ordemservico.utilizacao)
                               / area)
                     ) rendimento_unid_hr

            , nvl((select conversao_unidade.cod_unidade_destino
                    from   agricola.conversao_unidade
                    where  conversao_unidade.cod_grupoempresa     = vw_ordemservico.cod_grupoempresa
                    and    conversao_unidade.cod_empresa          = vw_ordemservico.cod_empresa
                    and    conversao_unidade.cod_filial           = vw_ordemservico.cod_filial
                    and    conversao_unidade.cod_unidade_original = vw_ordemservico.cod_unidade
                    and    conversao_unidade.cod_tipofazenda      = vw_ordemservico.cod_tipofazenda
                    and    conversao_unidade.cod_objetocusto      = vw_ordemservico.cod_objetocusto
                    and    conversao_unidade.cod_operacao         = vw_ordemservico.cod_operacao
                    and    conversao_unidade.tipo_operacao        = vw_ordemservico.tipo_operacao), vw_ordemservico.cod_unidade) unidade_rendimento

             , nvl((select conversao_unidade.cod_unidade_destino
                    from   agricola.conversao_unidade
                    where  conversao_unidade.cod_grupoempresa     = vw_ordemservico.cod_grupoempresa
                    and    conversao_unidade.cod_empresa          = vw_ordemservico.cod_empresa
                    and    conversao_unidade.cod_filial           = vw_ordemservico.cod_filial
                    and    conversao_unidade.cod_unidade_original = vw_ordemservico.cod_unidade
                    and    conversao_unidade.cod_tipofazenda      = vw_ordemservico.cod_tipofazenda
                    and    conversao_unidade.cod_objetocusto      = vw_ordemservico.cod_objetocusto
                    and    conversao_unidade.cod_operacao         = vw_ordemservico.cod_operacao
                    and    conversao_unidade.tipo_operacao        = vw_ordemservico.tipo_operacao), vw_ordemservico.cod_unidade) unidade_utilizacao

             , cod_unidade unidade_utilizacao_old
             , vazao
             , round(decode(operacao_irrigacao,'S',vazao * utilizacao,0),3) qtde_irrigacao
             , cod_meioaplicacao
             , desc_meioaplicacao1
             , desc_meioaplicacao2
             , data
             , data data_apontamento
           --  , 'Data: '||data desc_data
             , cod_funcionario
             , desc_responsavel1
             , desc_responsavel2
             , areaplantada area_plantada_vlr
             , areaproducao area_producao_vlr
            , '   Área Plantada: '||areaplantada area_plantada
           , '   Área Produção: '||areaproducao area_producao
            , cod_fazenda||' - '||zona||' - '||cod_talhao|| '   Área Plantada: '||areaplantada||'   Área Produção: '||areaproducao FazZonaTalhaoArea
             , cod_familia
             , Desc_Familia1
             , Desc_Familia2
             , cod_grupomaterial
             , Desc_GrupoMaterial1
             , Desc_GrupoMaterial2 Desc_GrupoMaterial2
             , cod_idade_cana
             , desc_idadecana
             , desc_idadecana2
             , FAZZONA2
             , decode(lag(to_number(ano|| numero||item||cod_fazenda||zona||cod_talhao),1,0) over(partition by PROCESSO, chaveArea order by chaveArea, decode(tipo_insumo  ,'E',areaequipamento,'M',areamaterial,'C',areacargo)),0,
               decode(controle_area,'E',areaequipamento,'M',areamaterial,'C',areacargo),null) xAREAPROCESSO, decode(lag(to_number(ano|| numero||item||cod_fazenda||zona||cod_talhao),1,0) over(partition by PROCESSO, SUBPROCESSO, chaveArea order by chaveArea,
               decode(tipo_insumo  ,'E',areaequipamento,'M',areamaterial,'C',areacargo)),0,decode(controle_area,'E',areaequipamento,'M',areamaterial,'C',areacargo),null) xAREASUBPROCESSO, decode(lag(to_number(ano|| numero||item||cod_fazenda||zona||cod_talhao),1,0) over(partition by PROCESSO, SUBPROCESSO, COD_ATIVIDADE, chaveArea order by chaveArea, decode(tipo_insumo  ,'E',areaequipamento,'M',areamaterial,'C',areacargo)),0,decode(controle_area,'E',areaequipamento,'M',areamaterial,'C',areacargo),null) xAREAATIVIDADE,
               decode(lag(to_number(ano|| numero||item||cod_fazenda||zona||cod_talhao),1,0) over(partition by PROCESSO, SUBPROCESSO, COD_ATIVIDADE, COD_OPERACAO1||'-'||TIPO_OPERACAO, chaveArea order by chaveArea, decode(tipo_insumo  ,'E',areaequipamento,'M',areamaterial,'C',areacargo)),0,decode(controle_area,'E',areaequipamento,'M',areamaterial,'C',areacargo),null) xAREACHAVEOPERACAO, decode(lag(to_number(ano|| numero||item||cod_fazenda||zona||cod_talhao),1,0) over(partition by PROCESSO, SUBPROCESSO, COD_ATIVIDADE, COD_OPERACAO1||'-'||TIPO_OPERACAO, COD_FAZENDA, chaveArea order by chaveArea,
               decode(tipo_insumo  ,'E',areaequipamento,'M',areamaterial,'C',areacargo)),0,decode(tipo_insumo,'E',areaequipamento,'M',areamaterial,'C',areacargo),null) xAREAFAZENDA, decode(lag(to_number(ano|| numero||item||cod_fazenda||zona||cod_talhao),1,0) over(partition by PROCESSO, SUBPROCESSO, COD_ATIVIDADE, COD_OPERACAO1||'-'||TIPO_OPERACAO, COD_FAZENDA, FazZona1, chaveArea order by chaveArea, decode(tipo_insumo  ,'E',areaequipamento,'M',areamaterial,'C',areacargo)),0,decode(tipo_insumo,'E',areaequipamento,'M',areamaterial,'C',areacargo),null) xAREAFazZona, decode(lag(to_number(ano|| numero||item||cod_fazenda||zona||cod_talhao),1,0) over(partition by PROCESSO, SUBPROCESSO, COD_ATIVIDADE, COD_OPERACAO1||'-'||TIPO_OPERACAO, COD_FAZENDA, FazZona1, COD_FAZENDA||'-'||zona||'-'||cod_talhao, chaveArea order by chaveArea,
               decode(tipo_insumo  ,'E',areaequipamento,'M',areamaterial,'C',areacargo)),0,decode(tipo_insumo,'E',areaequipamento,'M',areamaterial,'C',areacargo),null) xAREAFazZonaTalhao, decode(lag(to_number(ano|| numero||item||cod_fazenda||zona||cod_talhao),1,0) over(partition by PROCESSO, SUBPROCESSO, COD_ATIVIDADE, COD_OPERACAO1||'-'||TIPO_OPERACAO, COD_FAZENDA, FazZona1, COD_FAZENDA||'-'||zona||'-'||cod_talhao, COD_INSUMO, chaveArea order by chaveArea, decode(tipo_insumo  ,'E',areaequipamento,'M',areamaterial,'C',areacargo)),0,decode(tipo_insumo,'E',areaequipamento,'M',areamaterial,'C',areacargo),null) xAREAINSUMO
             , COD_SETORAGRICOLA
             , DESC_SETORAGRICOLA1
             , DESC_SETORAGRICOLA2
             , cod_variedade
             , desc_variedade1
             , desc_variedade2
             , cod_cultura
             , desc_cultura1
             , desc_cultura2
             , cod_safra
             , desc_safra1
             , desc_safra2
             , cod_safradetalhe
             , desc_safradetalhe1
             , desc_safradetalhe2 ,ano_ordemservico , nr_ordemservico
             FROM agricola.vw_apontamento              vw_ordemservico
             , agricola.parametros_empresa_agricola parametros_empresa_agricola
         WHERE vw_ordemservico.tipoinsumo = 'M'
           AND vw_ordemservico.cod_grupoempresa                = parametros_empresa_agricola.cod_grupoempresa_agr
           AND vw_ordemservico.cod_empresa                     = parametros_empresa_agricola.cod_empresa_agr
           AND vw_ordemservico.cod_filial                      = parametros_empresa_agricola.cod_filial_agr
           AND parametros_empresa_agricola.cod_grupoempresa    = 1
           AND parametros_empresa_agricola.cod_empresa         = 1
           AND parametros_empresa_agricola.cod_filial          = 1
            and vw_ordemservico.cod_safra = ?
            /*FILTROS*/
        )

        group by
               desc_insumo1
             , DEFASAGEM
             , UNIDADE_UTILIZACAO
             , UNIDADE_RENDIMENTO
             , PROCESSO
             , DESC_PROCESSO2
             , DESC_PROCESSO1
             , DESC_PROCESSO
             , SUBPROCESSO
             , DESC_SUBPROCESSO2
             , DESC_SUBPROCESSO1
             , DESC_SUBPROCESSO
             , COD_ATIVIDADE
             , DESC_ATIVIDADE2
             , DESC_ATIVIDADE1
             , DESC_ATIVIDADE
             , COD_OPERACAO1
             , TIPO_OPERACAO
             , DESC_OPERACAO2
             , DESC_OPERACAO1
             , DESC_OPERACAO
             , COD_FAZENDA
             , DESC_FAZENDA2
             , DESC_FAZENDA1
             , DESC_FAZENDA
             , FAZZONA1
             , COD_FAZENDA
             , ZONA
             , COD_TALHAO
           , FazZonaTalhaoArea
             , COD_INSUMO
             , DESC_INSUMO
             , DESC_INSUMO2
             , DESC_INSUMO1
            , data
            ,COD_REGIAOAGRICOLA, DESC_REGIAOAGRICOLA1
            ,ano
            ,numero ,ano_ordemservico , nr_ordemservico
        """;

    private static final String SQL_DETALHADO = """
        select * from (
        %s
        Order by PROCESSO
               , SUBPROCESSO
               , COD_ATIVIDADE
               , COD_OPERACAO1
               , TIPO_OPERACAO
               , COD_FAZENDA
               , FazZona1
               , COD_FAZENDA
               , ZONA
               , COD_TALHAO
               , COD_INSUMO
        ) where rownum <= %d
        """.formatted(SUBQUERY_DETALHE, MAX_TOTAL);

    /** Utilização/área/custo somados por insumo — custo ponderado (valor/utilização). */
    private static final String SQL_POR_INSUMO = """
        select * from (
        select cod_insumo, desc_insumo, unidade_utilizacao
             , round(sum(area), 2) area_total
             , round(sum(utilizacao), 2) utilizacao_total
             , round(sum(valor_total), 2) valor_total
             , round(decode(sum(area),0,0,sum(valor_total)/sum(area)),2) custo_ha_medio
             , round(decode(sum(utilizacao),0,0,sum(valor_total)/sum(utilizacao)),2) custo_medio
        from ( %s )
        group by cod_insumo, desc_insumo, unidade_utilizacao
        order by utilizacao_total desc
        ) where rownum <= %d
        """.formatted(SUBQUERY_DETALHE, MAX_TOTAL);

    /**
     * @param codSafra   obrigatório
     * @param codFazenda opcional
     * @param dataIni    opcional, formato yyyy-MM-dd (data do apontamento >=)
     * @param dataFim    opcional, formato yyyy-MM-dd (data do apontamento <=)
     * @param insumo     opcional, trecho da descrição do insumo (busca contém,
     *                   sem diferenciar maiúsculas)
     * @param agrupar    opcional: null/"detalhado" (linha a linha, por dia/
     *                   talhão) ou "insumo" (soma utilização/área/custo por
     *                   insumo — use para "total aplicado de X")
     */
    public List<Map<String, Object>> buscar(String codSafra, Integer codFazenda,
                                            String dataIni, String dataFim, String insumo, String agrupar) {

        StringBuilder filtros = new StringBuilder();
        List<Object> params = new ArrayList<>();
        params.add(codSafra);

        if (codFazenda != null) {
            filtros.append(" and vw_ordemservico.cod_fazenda = ?\n");
            params.add(codFazenda);
        }
        if (dataIni != null && !dataIni.isBlank()) {
            filtros.append(" and trunc(vw_ordemservico.data) >= to_date(?, 'YYYY-MM-DD')\n");
            params.add(dataIni.trim());
        }
        if (dataFim != null && !dataFim.isBlank()) {
            filtros.append(" and trunc(vw_ordemservico.data) <= to_date(?, 'YYYY-MM-DD')\n");
            params.add(dataFim.trim());
        }
        if (insumo != null && !insumo.isBlank()) {
            filtros.append(" and upper(vw_ordemservico.desc_insumo) like '%'||upper(?)||'%'\n");
            params.add(insumo.trim());
        }

        String template = "insumo".equalsIgnoreCase(agrupar == null ? "" : agrupar.trim())
                ? SQL_POR_INSUMO : SQL_DETALHADO;
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
            LOG.log(Level.SEVERE, "Erro ao buscar insumos: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de insumos: " + e.getMessage(), e);
        }
    }
}
