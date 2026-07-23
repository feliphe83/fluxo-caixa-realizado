package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.AgroOracleConnectionUtil;
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
 * DAO da consulta de cadastro de talhão (agricola.talhao e tabelas
 * relacionadas: fazenda, variedade, cultura, safra, solo, irrigação etc.),
 * usada como fonte de dados do chatbot agrícola (via n8n).
 *
 * Filtro por safra é obrigatório, igual à consulta original. Restrita a
 * fazenda própria (cod_tipofazenda = 1) — o Dr. Alfredo não deve responder
 * sobre talhão de fazenda de terceiro.
 */
public class AgricolaTalhaoDAO {

    private static final Logger LOG = Logger.getLogger(AgricolaTalhaoDAO.class.getName());

    private static final String SQL = """
        select
        desc_tipofazenda
        , cod_fazenda
             , desc_fazenda
             , cod_talhao
             , descricao_talhao
             , desc_cultura
             , areaproducao
             , areaplantada areatotal
             , idade_cana
             , IdadeVegetativa idade_cana
             , data_ultimo_corte1
              , desc_variedade
             , desc_tiposolo
             , desc_topografia
             , dataplantio1
             , espacamento1
             , desc_tipoirrigacao
             , zona
             , dataplantio
             , TIPO_CORTE
             , cod_regiaoagricola
             , desc_regiao

        from (
            select talhao.cod_fazenda    || '-' || talhao.zona || '-' || talhao.cod_talhao                  faz_zona_talhao
                 , 'Fornecedor: '        || fornecedor.cod_fornecedor   || ' - ' || pessoa.nome             desc_fornecedor
                 , 'Tipo Fdo Agricola: ' || tipofazenda.cod_tipofazenda || ' - ' || tipofazenda.descricao   desc_tipofazenda
                 , fazenda.descricao       desc_fazenda
                 , tipo_corte.descricao    desc_tipocorte

                 , nvl(cultura.desc_cultura,culturatalhao.desc_cultura) desc_cultura
                 , tipovariedade.COD_TIPOVARIEDADE                                                                 COD_TIPOVARIEDADE
                 , 'Tipo Variedade: '    || tipovariedade.descricao                                                desc_tipovariedade
                 ,  variedade.descricao                                                    desc_variedade

                 ,  tipo_solo.descricao         desc_tiposolo
                 ,  topografia_talhao.descricao desc_topografia
                 ,  irrigacaotipo.descricao     desc_tipoirrigacao
                 , 'Fonte de Água: '     || irrigacaotipo.id_irrigfonte     || ' - ' || irrigacaofonte.descricao    desc_FonteAgua
                 , 'Setor: '             || setor_agricola.id_setoragricola || ' - ' || setor_agricola.descricao    desc_setor

                 , variedade.descricao variedade

                 , talhao.descricao_talhao

                 , talhao.ruas                                                                                      ruass

                 , 'Distância: '||agricola.fn_distancia(1, 1, 1, talhao.cod_fazenda, talhao.zona, talhao.cod_talhao, 'T',null,null, talhao.cod_safra) distanciaa
                 , agricola.fn_distancia(1,1, 1, talhao.cod_fazenda, talhao.zona, talhao.cod_talhao, 'T',null,null, talhao.cod_safra)                distanciaa1

                 , Talhao.dataplantio dataplantio1
                 ,  to_char(talhao.espacamento, '99G990D99') espacamento1
                 , talhao.data_corte_safra_anterior         data_ultimo_corte1
                 , talhao.data_corte_safra_anterior                           data_ultimo_corte

                 , decode(nvl(talhao.talhaovinhaca, 'N'), 'S', 'Sim', 'Não')  vinhacaa
                 , decode(nvl(talhao.areapec, 'N'), 'S', 'Sim', 'Não')        areapec
                 , decode(nvl(talhao.maturador, 'N'), 'S', 'Sim', 'Não')      maturadorr
                 , decode(nvl(talhao.areacritica, 'N'), 'S', 'Sim', 'Não')    areacritica
                 , topografia_talhao.descricao                                topografia

                 , TALHAO.BLOCO                                                                      BLOCO
                 , 'Bloco: '||nvl(TALHAO.BLOCO,999999)                                               DESC_BLOCO
                 , TALHAO.DESTINO_TALHAO                                                             COD_DESTINO
                 , 'Destino: '|| TALHAO.DESTINO_TALHAO ||' - '||DESTINOTALHAO.DESCRICAO              DESC_DESTINO
                 , TALHAO.COD_AMBIENTEPROD                                                           COD_AMBIENTEPROD
                 , 'Ambiente Prod.: '|| TALHAO.COD_AMBIENTEPROD ||' - '||AMBIENTE_PRODUCAO.DESCRICAO DESC_AMBIENTEPROD

                 , talhao.cod_fazenda cod_fazenda
                 , talhao.zona        zona
                 , talhao.cod_talhao  cod_talhao
                 , talhao.dataplantio

                 , safra.descricao            Desc_Safra
                 , talhao.Cod_Safra
                 , 'Safra: '||Talhao.Cod_SafraDetalhe||' - '||safra_Detalhe.Descricao Desc_SafraDetalhe
                 , talhao.Cod_SafraDetalhe

                 , talhao.cod_situacao
                 , situacao_talhao.desc_situacao SITUACAO_TALHAO

                 , 'Zona :'   || talhao.zona       desc_zona
                 , 'Talhão : '|| talhao.cod_talhao desc_talhao
                 , idade_cana.descricao            idade_cana
                 , 'Idade Cana: '|| idade_cana.descricao idade_cana1
                 , talhao.areaproducao
                 , talhao.areaplantada
                 , talhao.areamuda
                 , talhao.areadiversa
                 , talhao.areaarrendada
                 , talhao.rendimentoagricola                      rendimento_estimado
                 , talhao.metroslineares
                 , talhao.espacamento                             espacamentoo
                 , talhao.cod_frente || ' - ' || frente.descricao frente_estimada
                 , talhao.cod_frente
                 , 'Frente: '||talhao.cod_Frente||' - '||frente.descricao desc_frente
                 , regiao_agricola.descricao desc_regiaoagricola
                 , talhao.area_carreador
                 , talhao.area_reforma

                 , nvl(AGRICOLA.FN_IDADEVEGETATIVA(talhao.cod_safra
                                                  , talhao.cod_fazenda
                                                  , talhao.zona
                                                  , talhao.cod_talhao
                                                  , talhao.cod_safradetalhe
                                                  , talhao.data_corte_safra_anterior
                                                  , talhao.dataplantio
                                                  , talhao.DATAENCERRAMENTO
                                                  ),0) IdadeVegetativa
                 , talhao.area_cortada
                 , decode(tipo_corte.tipo,1,'Man.', decode(tipo_corte.tipo,2,'Mec.')) TIPO_CORTE
                 , setor_agricola.id_setoragricola
                 , regiao_agricola.cod_regiaoagricola
                 , regiao_agricola.descricao desc_regiao
            from  agricola.regiao_agricola
                 , agricola.setor_agricola setor_agricola
                 , agricola.vw_destinotalhao DestinoTalhao
                 , agricola.ambiente_producao
                 , rh.pessoa
                 , material.fornecedor
                 , agricola.tipo_solo
                 , agricola.irrigacaofonte
                 , agricola.irrigacaotipo
                 , agricola.tipofazenda
                 , agricola.historico_fazenda
                 , agricola.fazenda
                 , agricola.frente
                 , agricola.idade_cana
                 , agricola.topografia_talhao
                 , agricola.cultura cultura
                 , agricola.cultura culturatalhao
                 , agricola.variedade
                 , agricola.tipovariedade
                 , agricola.tipo_corte
                 , agricola.safra_Detalhe
                 , agricola.safra
                 , agricola.situacao_talhao
                 , agricola.talhao

            where  regiao_agricola.cod_regiaoagricola    = historico_fazenda.cod_regiaoagricola
            and    historico_fazenda.id_setoragricola    = setor_agricola.id_setoragricola

            and    pessoa.cod_pessoa                     = fornecedor.cod_pessoa
            and    fornecedor.cod_fornecedor             = historico_fazenda.cod_fornecedor

            and    tipo_solo.cod_tiposolo            (+) = talhao.cod_tiposolo

            AND    irrigacaofonte.id_irrigfonte      (+) = irrigacaotipo.id_irrigfonte

            and    irrigacaotipo.cod_tipoirrigacao   (+) = talhao.cod_tipoirrigacao

            and    tipofazenda.cod_tipofazenda           = historico_fazenda.cod_tipofazenda

            -- Só fazenda própria (cod_tipofazenda = 1) — talhão é sempre
            -- consultado pelo Dr. Alfredo restrito à fazenda própria.
            and    tipofazenda.cod_tipofazenda           = 1

            and    rh.intersecao(safra.data_inicio,safra.data_fim, historico_fazenda.data_inicio, historico_fazenda.data_fim) = 'TRUE'

            and    historico_fazenda.cod_fazenda      = fazenda.cod_fazenda
            and historico_fazenda.data_inicio         = agricola.fn_historicofazenda(talhao.cod_fazenda,'DATA_INICIO',safra.data_inicio,safra.data_fim)

            and    fazenda.cod_fazenda            = talhao.cod_fazenda
            and    frente.cod_frente              = talhao.cod_frente

            and    idade_cana.cod_idade_cana      = talhao.numerocorte

            and    topografia_talhao.cod_topo     (+) = talhao.cod_topo

            and    cultura.cod_cultura              (+)= variedade.cod_cultura
            and    culturatalhao.cod_cultura        (+)= talhao.cod_cultura

            and    variedade.cod_variedade             = talhao.cod_variedade
            and    tipovariedade.cod_tipovariedade (+) = variedade.cod_tipovariedade

            and    tipo_corte.cod_tipocorte   (+) = talhao.cod_tipocorte

            AND    SAFRA_DETALHE.COD_SAFRA               = TALHAO.COD_SAFRA
            AND    SAFRA_DETALHE.COD_SAFRADETALHE        = TALHAO.COD_SAFRADETALHE

            and    talhao.cod_safra = ?

            AND    SAFRA.COD_GRUPOEMPRESA                = 1
            AND    SAFRA.COD_EMPRESA                     = 1
            AND    SAFRA.COD_FILIAL                      = 1
            AND    SAFRA.COD_SAFRA                       = TALHAO.COD_SAFRA

            and    talhao.cod_fazenda      = fazenda.cod_fazenda

            and    situacao_talhao.cod_situacao         = talhao.cod_situacao

            and    talhao.cod_variedade    = variedade.cod_variedade

            and    DestinoTalhao.destino          (+)= talhao.Destino_talhao

            and    ambiente_producao.id_ambproduc (+)= talhao.cod_AmbienteProd
        )
        """;

    public List<Map<String, Object>> buscar(String codSafra) {
        try (Connection conn = AgroOracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            ps.setString(1, codSafra);

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar talhões: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de talhões", e);
        }
    }
}
