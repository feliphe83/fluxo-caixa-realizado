package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.AgroOracleConnectionUtil;
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
 * DAO da consulta de apontamento de insumos agrícolas (agricola.vw_apontamento,
 * tipoinsumo = 'M'), usada como fonte de dados do chatbot agrícola (via n8n).
 *
 * Filtro de período (DATA BETWEEN) é obrigatório; filtro por safra é opcional.
 */
public class AgricolaInsumoDAO {

    private static final Logger LOG = Logger.getLogger(AgricolaInsumoDAO.class.getName());

    private static final String SQL = """
        SELECT ano
             , numero
             , PROCESSO PROCESSO
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
             , COD_REGIAOAGRICOLA, DESC_REGIAOAGRICOLA1
             , cod_talhao
             , zona
             , COD_INSUMO COD_INSUMO
             , DESC_INSUMO
             , sum(AREA) area
             , sum(UTILIZACAO) UTILIZACAO
             , UNIDADE_UTILIZACAO
             , decode(SUM(area),0,0,  SUM(UTILIZACAO) / SUM(area ) ) Media
             , decode(SUM(area),0,0,  SUM(valor_realizado) / SUM(area ) ) CustoHA
             , nvl(decode(SUM(utilizacao),0,0, SUM(valor_realizado) / SUM(utilizacao ) ), 0) CustoMedio
             , ano_ordemservico , nr_ordemservico
        FROM (

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
                   WHERE  o.cod_grupoempresa   = agricola.pkg_planoagricola.fn_obter_grupoempresa_view
                   AND    o.cod_empresa        = agricola.pkg_planoagricola.fn_obter_empresa_view
                   AND    o.cod_filial         = agricola.pkg_planoagricola.fn_obter_filial_view
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
                    where  conversao_unidade.cod_grupoempresa     = agricola.pkg_planoagricola.fn_obter_grupoempresa_view
                    and    conversao_unidade.cod_empresa          = agricola.pkg_planoagricola.fn_obter_empresa_view
                    and    conversao_unidade.cod_filial           = agricola.pkg_planoagricola.fn_obter_filial_view
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
                                  where  conversao_unidade.cod_grupoempresa     = agricola.pkg_planoagricola.fn_obter_grupoempresa_view
                                  and    conversao_unidade.cod_empresa          = agricola.pkg_planoagricola.fn_obter_empresa_view
                                  and    conversao_unidade.cod_filial           = agricola.pkg_planoagricola.fn_obter_filial_view
                                  and    conversao_unidade.cod_unidade_original = vw_ordemservico.cod_unidade
                                  and    conversao_unidade.cod_tipofazenda      = vw_ordemservico.cod_tipofazenda
                                  and    conversao_unidade.cod_objetocusto      = vw_ordemservico.cod_objetocusto
                                  and    conversao_unidade.cod_operacao         = vw_ordemservico.cod_operacao
                                  and    conversao_unidade.tipo_operacao        = vw_ordemservico.tipo_operacao), vw_ordemservico.utilizacao),0,0,
                      area /
                            nvl((select material.f_calcula_expressao(replace(vw_ordemservico.utilizacao,',','.')
                                                                  || conversao_unidade.formula_conversao)
                                 from   agricola.conversao_unidade
                                 where  conversao_unidade.cod_grupoempresa     = agricola.pkg_planoagricola.fn_obter_grupoempresa_view
                                 and    conversao_unidade.cod_empresa          = agricola.pkg_planoagricola.fn_obter_empresa_view
                                 and    conversao_unidade.cod_filial           = agricola.pkg_planoagricola.fn_obter_filial_view
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
                                 where  conversao_unidade.cod_grupoempresa     = agricola.pkg_planoagricola.fn_obter_grupoempresa_view
                                 and    conversao_unidade.cod_empresa          = agricola.pkg_planoagricola.fn_obter_empresa_view
                                 and    conversao_unidade.cod_filial           = agricola.pkg_planoagricola.fn_obter_filial_view
                                 and    conversao_unidade.cod_unidade_original = vw_ordemservico.cod_unidade
                                 and    conversao_unidade.cod_tipofazenda      = vw_ordemservico.cod_tipofazenda
                                 and    conversao_unidade.cod_objetocusto      = vw_ordemservico.cod_objetocusto
                                 and    conversao_unidade.cod_operacao         = vw_ordemservico.cod_operacao
                                 and    conversao_unidade.tipo_operacao        = vw_ordemservico.tipo_operacao), vw_ordemservico.utilizacao)
                               / area)
                     ) rendimento_unid_hr

            , nvl((select conversao_unidade.cod_unidade_destino
                    from   agricola.conversao_unidade
                    where  conversao_unidade.cod_grupoempresa     = agricola.pkg_planoagricola.fn_obter_grupoempresa_view
                    and    conversao_unidade.cod_empresa          = agricola.pkg_planoagricola.fn_obter_empresa_view
                    and    conversao_unidade.cod_filial           = agricola.pkg_planoagricola.fn_obter_filial_view
                    and    conversao_unidade.cod_unidade_original = vw_ordemservico.cod_unidade
                    and    conversao_unidade.cod_tipofazenda      = vw_ordemservico.cod_tipofazenda
                    and    conversao_unidade.cod_objetocusto      = vw_ordemservico.cod_objetocusto
                    and    conversao_unidade.cod_operacao         = vw_ordemservico.cod_operacao
                    and    conversao_unidade.tipo_operacao        = vw_ordemservico.tipo_operacao), vw_ordemservico.cod_unidade) unidade_rendimento

             , nvl((select conversao_unidade.cod_unidade_destino
                    from   agricola.conversao_unidade
                    where  conversao_unidade.cod_grupoempresa     = agricola.pkg_planoagricola.fn_obter_grupoempresa_view
                    and    conversao_unidade.cod_empresa          = agricola.pkg_planoagricola.fn_obter_empresa_view
                    and    conversao_unidade.cod_filial           = agricola.pkg_planoagricola.fn_obter_filial_view
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
         WHERE DATA                BETWEEN ?  AND ?
          AND vw_ordemservico.tipoinsumo = 'M'
           AND vw_ordemservico.cod_grupoempresa                = parametros_empresa_agricola.cod_grupoempresa_agr
           AND vw_ordemservico.cod_empresa                     = parametros_empresa_agricola.cod_empresa_agr
           AND vw_ordemservico.cod_filial                      = parametros_empresa_agricola.cod_filial_agr
           AND parametros_empresa_agricola.cod_grupoempresa    = 1
           AND parametros_empresa_agricola.cod_empresa         = 1
           AND parametros_empresa_agricola.cod_filial          = 1
          %s
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
            , COD_REGIAOAGRICOLA, DESC_REGIAOAGRICOLA1
            , ano
            , numero, ano_ordemservico , nr_ordemservico
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
        """;

    /**
     * @param codSafra opcional — se informado, filtra também por
     *                 vw_ordemservico.cod_safra.
     */
    public List<Map<String, Object>> buscar(LocalDate dataIni, LocalDate dataFim, String codSafra) {
        boolean comSafra = codSafra != null && !codSafra.isBlank();
        String filtroSafra = comSafra ? "AND vw_ordemservico.cod_safra = ?" : "";
        String sql = SQL.replace("%s", filtroSafra);

        try (Connection conn = AgroOracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(dataIni));
            ps.setDate(2, Date.valueOf(dataFim));
            if (comSafra) {
                ps.setString(3, codSafra);
            }

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar insumos agrícolas: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de insumos agrícolas", e);
        }
    }
}
