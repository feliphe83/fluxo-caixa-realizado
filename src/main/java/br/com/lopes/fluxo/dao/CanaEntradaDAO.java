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
 * Cana que entrou na usina na safra, para o painel de acompanhamento.
 *
 * A fonte é agricola.ordem_corte_unica — a ordem de corte é onde o ERP grava
 * a produção pesada de cada talhão colhido. É a mesma origem que o mapa de
 * talhões usa para "produção realizada" e que o relatório de produtividade
 * usa para produção/TCH, então os três contam a mesma cana.
 *
 * Uma linha por mês × tipo de fundo agrícola × tipo de corte. Quem soma e
 * classifica é o servlet: assim a consulta não precisa saber o nome que a
 * empresa dá a "própria", "fornecedor" ou "acionista", e um tipo novo
 * cadastrado no ERP aparece em vez de sumir dentro de um ELSE.
 *
 * Três cuidados que a consulta toma:
 *
 * 1. Não repete linha. Tudo pende de ordem_corte_unica por outer join, e o
 *    tipo do fundo agrícola vem de uma subconsulta que devolve um registro
 *    por fazenda (o histórico vigente) — o mesmo recorte com row_number que
 *    {@link ManobraDAO} já usa. Sem isso, uma fazenda com três históricos
 *    triplicaria a tonelagem dela no painel.
 *
 * 2. Não exige a ordem de colheita encerrada, como o mapa. No meio da safra
 *    quase toda ordem está aberta, e zerar essas mostraria uma usina parada.
 *
 * 3. pesomuda entra junto com producao_realizada — é cana pesada do mesmo
 *    corte, como no relatório de produtividade.
 */
public class CanaEntradaDAO {

    private static final Logger LOG = Logger.getLogger(CanaEntradaDAO.class.getName());

    /** Bind: cod_safra. */
    private static final String SQL = """
        select to_char(oc.data_ordem, 'YYYY-MM')                      mes
             , nvl(tipo_faz.descricao, 'Não informado')               tipo_fazenda
             , nvl(tc.descricao,       'Não informado')               tipo_corte
             , sum(nvl(oc.producao_realizada, 0) + nvl(oc.pesomuda, 0)) toneladas
             , count(*)                                               ordens
        from       agricola.ordem_corte_unica oc
        left join  agricola.tipo_corte tc
               on  tc.cod_tipocorte = oc.cod_tipocorte
        left join  ( select cod_fazenda, descricao
                     from ( select hf.cod_fazenda
                                 , tf.descricao
                                 , row_number() over (partition by hf.cod_fazenda
                                                      order by hf.data_inicio desc nulls last) rn
                            from      agricola.historico_fazenda hf
                            left join agricola.tipofazenda tf
                                   on tf.cod_tipofazenda = hf.cod_tipofazenda
                            where     hf.cod_grupoempresa = 1
                            and       hf.cod_empresa      = 1
                            and       hf.cod_filial       = 1 )
                     where rn = 1 ) tipo_faz
               on  tipo_faz.cod_fazenda = oc.cod_fazenda
        where  oc.cod_grupoempresa = 1
        and    oc.cod_empresa      = 1
        and    oc.cod_filial       = 1
        and    oc.cod_safra        = ?
        and    oc.data_ordem      is not null
        group by to_char(oc.data_ordem, 'YYYY-MM')
               , nvl(tipo_faz.descricao, 'Não informado')
               , nvl(tc.descricao,       'Não informado')
        order by 1, 2, 3
        """;

    /** Safras cadastradas, da mais recente para a mais antiga. */
    private static final String SQL_SAFRAS = """
        select cod_safra, data_inicio, data_fim
             , case when trunc(sysdate) between trunc(data_inicio) and trunc(data_fim)
                    then 'S' else 'N' end atual
        from   agricola.safra
        where  cod_grupoempresa = 1
        and    cod_empresa      = 1
        and    cod_filial       = 1
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
            LOG.log(Level.SEVERE, "Erro ao buscar entrada de cana (safra=" + codSafra + "): " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de entrada de cana: " + e.getMessage(), e);
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
