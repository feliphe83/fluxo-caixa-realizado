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
 * Dados de cada talhão para o mapa interativo (tela mapa-talhoes.html).
 *
 * A geometria não vem daqui — o ERP não guarda coordenada nenhuma. Os
 * polígonos estão num GeoJSON estático (webapp/mapas/talhoes.geojson),
 * convertido do shapefile da topografia. O que casa os dois é a chave
 * "fazenda-zona-talhao", presente nos dois lados.
 *
 * Consulta enxuta de propósito: só o que o mapa pinta e o que o popup
 * mostra. O cadastro completo do talhão continua em {@link AgricolaTalhaoDAO},
 * que o chatbot usa.
 *
 * Traz também produção estimada e realizada do talhão, para o mapa comparar
 * as duas. São a mesma cana contada em dois momentos — antes e depois do
 * corte — e por isso nunca devem ser somadas uma à outra.
 */
public class MapaTalhaoDAO {

    private static final Logger LOG = Logger.getLogger(MapaTalhaoDAO.class.getName());

    /**
     * Um registro por talhão da safra. Os outer joins nas tabelas de apoio
     * (situação, variedade, idade) são de propósito: um talhão sem variedade
     * cadastrada ainda precisa aparecer no mapa, senão some sem explicação.
     */
    private static final String SQL = """
        select talhao.cod_fazenda || '-' || talhao.zona || '-' || talhao.cod_talhao  chave
             , talhao.cod_fazenda
             , talhao.zona
             , talhao.cod_talhao
             , fazenda.descricao                                                      desc_fazenda
             , talhao.cod_situacao
             , situacao_talhao.desc_situacao
             , variedade.descricao                                                    desc_variedade
             , idade_cana.descricao                                                   desc_idade
             , talhao.numerocorte
             , nvl(talhao.areaproducao, 0)                                            areaproducao
             , nvl(talhao.areaplantada, 0)                                            areaplantada
             , talhao.data_corte_safra_anterior                                       data_ultimo_corte
             , round(nvl(agricola.fn_estimativatalhao( 1, 1, 1
                                                     , talhao.cod_safra
                                                     , talhao.cod_fazenda
                                                     , talhao.zona
                                                     , talhao.cod_talhao
                                                     , 0
                                                     , 'TCH'), 0), 2)                 tch_estimado
             -- Produção estimada, na conta que a área agrícola usa: área de
             -- produção × rendimento agrícola do talhão, convertido para
             -- tonelada. Na consulta de origem havia um
             -- "+ decode('N','N',0,areamuda)", que é zero sempre — o 'N' diz
             -- para não somar a área de muda. Ficou de fora por ser o que é.
             , round( nvl(talhao.areaproducao, 0)
                    * agricola.fn_conversao_unidade( rh.c('UNIDADE_TONELADA')
                                                   , 'T'
                                                   , nvl(talhao.rendimentoagricola, 0))
                    , 2)                                                              producao_estimada
             -- Produção realizada: a cana que já saiu deste talhão na safra,
             -- somando todas as ordens de corte dele (um talhão costuma ter
             -- mais de uma). pesomuda entra junto porque é cana pesada do
             -- mesmo corte, como no relatório de produtividade.
             --
             -- Diferente daquele relatório, aqui NÃO se exige a ordem de
             -- colheita encerrada: no meio da safra quase toda ordem está
             -- aberta, e zerar essas faria o mapa mostrar colheita nenhuma
             -- justamente quando ele é mais útil. O número é, então, o que já
             -- foi pesado até agora.
             , round( nvl( (select sum(nvl(oc.producao_realizada, 0) + nvl(oc.pesomuda, 0))
                            from   agricola.ordem_corte_unica oc
                            where  oc.cod_grupoempresa = 1
                            and    oc.cod_empresa      = 1
                            and    oc.cod_filial       = 1
                            and    oc.cod_safra        = talhao.cod_safra
                            and    oc.cod_fazenda      = talhao.cod_fazenda
                            and    oc.zona             = talhao.zona
                            and    oc.cod_talhao       = talhao.cod_talhao), 0)
                    , 2)                                                              producao_realizada
        from   agricola.talhao
             , agricola.fazenda
             , agricola.situacao_talhao
             , agricola.variedade
             , agricola.idade_cana
        where  fazenda.cod_fazenda              = talhao.cod_fazenda
        and    situacao_talhao.cod_situacao (+) = talhao.cod_situacao
        and    variedade.cod_variedade      (+) = talhao.cod_variedade
        and    idade_cana.cod_idade_cana    (+) = talhao.numerocorte
        and    talhao.cod_safra                 = ?
        """;

    /** Safras cadastradas, da mais recente para a mais antiga. */
    private static final String SQL_SAFRAS = """
        select cod_safra, data_inicio, data_fim
             , case when trunc(sysdate) between trunc(data_inicio) and trunc(data_fim)
                    then 'S' else 'N' end atual
        from   agricola.safra
        where  cod_grupoempresa = 1
        and    cod_empresa = 1
        and    cod_filial = 1
        order  by data_inicio desc
        """;

    public List<Map<String, Object>> buscar(String codSafra) {
        try (Connection conn = AgroOracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {
            ps.setString(1, codSafra);
            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar talhões do mapa (safra=" + codSafra + "): " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de talhões: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> safras() {
        try (Connection conn = AgroOracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SAFRAS);
             ResultSet rs = ps.executeQuery()) {
            return RowMapperUtil.toList(rs);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao listar safras: " + e.getMessage(), e);
            throw new RuntimeException("Falha ao listar safras: " + e.getMessage(), e);
        }
    }
}
