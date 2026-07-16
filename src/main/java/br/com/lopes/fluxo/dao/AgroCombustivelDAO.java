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
 * DAO da consulta de abastecimento de combustível (posto.abastecimento e
 * requisição de material vinculada), usada como fonte de dados do Dr.
 * Alfredo (chat) para perguntas sobre combustível.
 *
 * Período de abastecimento é obrigatório. Equipamento, tipo de cliente e
 * descrição do combustível são filtros opcionais.
 */
public class AgroCombustivelDAO {

    private static final Logger LOG = Logger.getLogger(AgroCombustivelDAO.class.getName());

    /** Teto absoluto de linhas devolvidas (protege memória do Tomcat/export). */
    private static final int MAX_TOTAL = 20000;

    private static final String SQL = """
        select * from (
        select
               abastecimento.cod_equipamento      cod_equipamento
             , substr(equipamento.descricao,0,50) desc_equipamento
             , abastecimento.cod_abast            cod_abastecimento
             , abastecimento.cod_tipocliente      cod_tipocliente
             , substr(tipocliente.descricao,0,30) desc_tipocliente
             , abastecimento.data                 data_abastecimento
             , abastecimento.nr_nf                nr_nf
             , abastecimento.serie_nf             serie_nf
             , itensrequisicaomaterial.cod_material cod_combustivel
             , substr(material.descricao,0,50)     desc_combustivel
             , abastecimento.qtde_litros          qtde_litros
             , itensrequisicaomaterial.nrrequisicao nr_requisicao
             , itensrequisicaomaterial.item        item_requisicao
             , decode(itensrequisicaomaterial.situacao, 'P', 'Pendente', 'A', 'Retirada', 'C', 'Cancelada') situacao_requisicao
        from material.material
           , posto.tipocliente
           , automotivo.equipamento
           , material.itensrequisicaomaterial  itensrequisicaomaterial
           , posto.abastecimento_itensrequisicao
           , posto.abastecimento abastecimento
        where material.cod_material                         = itensrequisicaomaterial.cod_material
        and abastecimento.cod_material                      = itensrequisicaomaterial.cod_material
        and abastecimento_itensrequisicao.item              = itensrequisicaomaterial.item
        and abastecimento_itensrequisicao.nrrequisicao      = itensrequisicaomaterial.nrrequisicao
        and abastecimento_itensrequisicao.cod_grupoempresa  = itensrequisicaomaterial.cod_grupoempresa
        and abastecimento_itensrequisicao.cod_empresa       = itensrequisicaomaterial.cod_empresa
        and abastecimento_itensrequisicao.cod_filial        = itensrequisicaomaterial.cod_filial
        and abastecimento.cod_abast                         = abastecimento_itensrequisicao.cod_abast
        and tipocliente.cod_tipocliente                     = abastecimento.cod_tipocliente
        and equipamento.cod_equipamento                     = abastecimento.cod_equipamento
        and equipamento.cod_grupoempresa                    = abastecimento.cod_grupoempresa
        and abastecimento.data                              >= to_date(?, 'YYYY-MM-DD')
        and abastecimento.data                              <= to_date(?, 'YYYY-MM-DD')
        and abastecimento.cod_grupoempresa                  = 1
        and abastecimento.cod_empresa                       = 1
        and abastecimento.cod_filial                        = 1
        and abastecimento.cod_sistema                       is null
        /*FILTROS*/
        order by abastecimento.data, abastecimento.cod_equipamento, abastecimento.cod_abast
        ) where rownum <= %d
        """.formatted(MAX_TOTAL);

    /**
     * @param dataIni        obrigatório, yyyy-MM-dd (data do abastecimento >=)
     * @param dataFim        obrigatório, yyyy-MM-dd (data do abastecimento <=)
     * @param codEquipamento opcional
     * @param codTipoCliente opcional
     * @param combustivel    opcional, trecho da descrição do combustível
     */
    public List<Map<String, Object>> buscar(String dataIni, String dataFim, Integer codEquipamento,
                                            Integer codTipoCliente, String combustivel) {

        StringBuilder filtros = new StringBuilder();
        List<Object> params = new ArrayList<>();
        params.add(dataIni);
        params.add(dataFim);

        if (codEquipamento != null) {
            filtros.append(" and abastecimento.cod_equipamento = ?\n");
            params.add(codEquipamento);
        }
        if (codTipoCliente != null) {
            filtros.append(" and abastecimento.cod_tipocliente = ?\n");
            params.add(codTipoCliente);
        }
        if (combustivel != null && !combustivel.isBlank()) {
            filtros.append(" and upper(material.descricao) like '%'||upper(?)||'%'\n");
            params.add(combustivel.trim());
        }

        String sql = SQL.replace("/*FILTROS*/", filtros.toString());

        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar abastecimentos: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de combustível: " + e.getMessage(), e);
        }
    }
}
