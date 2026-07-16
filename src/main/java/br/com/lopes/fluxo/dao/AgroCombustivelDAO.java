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
 *
 * Com agrupar=combustivel ou agrupar=equipamento, os litros são somados
 * direto no banco (ordenados do maior para o menor consumo) — necessário
 * para perguntas de "total por X" e "top N por consumo", já que o agente só
 * recebe uma amostra limitada de linhas do modo detalhado.
 */
public class AgroCombustivelDAO {

    private static final Logger LOG = Logger.getLogger(AgroCombustivelDAO.class.getName());

    /** Teto absoluto de linhas devolvidas (protege memória do Tomcat/export). */
    private static final int MAX_TOTAL = 20000;

    // abastecimento.data é armazenada como texto no formato DDMMYYYY (sem
    // separadores) — não é coluna DATE. Comparada direto como string, sem
    // to_date(), igual à consulta original validada no Oracle.
    private static final String FROM_WHERE_BASE = """
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
        and abastecimento.data                              >= ?
        and abastecimento.data                              <= ?
        and abastecimento.cod_grupoempresa                  = 1
        and abastecimento.cod_empresa                       = 1
        and abastecimento.cod_filial                        = 1
        and abastecimento.cod_sistema                       is null
        /*FILTROS*/
        """;

    private static final String SQL_DETALHADO = """
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
             , abastecimento.kmhs_rodados         kmhs_rodados
             , itensrequisicaomaterial.nrrequisicao nr_requisicao
             , itensrequisicaomaterial.item        item_requisicao
             , decode(itensrequisicaomaterial.situacao, 'P', 'Pendente', 'A', 'Retirada', 'C', 'Cancelada') situacao_requisicao
        %s
        order by abastecimento.data, abastecimento.cod_equipamento, abastecimento.cod_abast
        ) where rownum <= %d
        """.formatted(FROM_WHERE_BASE, MAX_TOTAL);

    private static final String SQL_POR_COMBUSTIVEL = """
        select * from (
        select
               itensrequisicaomaterial.cod_material cod_combustivel
             , substr(material.descricao,0,50)     desc_combustivel
             , round(sum(abastecimento.qtde_litros), 2) total_litros
             , count(*) qtde_abastecimentos
        %s
        group by itensrequisicaomaterial.cod_material, substr(material.descricao,0,50)
        order by total_litros desc
        ) where rownum <= %d
        """.formatted(FROM_WHERE_BASE, MAX_TOTAL);

    private static final String SQL_POR_EQUIPAMENTO = """
        select * from (
        select
               abastecimento.cod_equipamento      cod_equipamento
             , substr(equipamento.descricao,0,50) desc_equipamento
             , round(sum(abastecimento.qtde_litros), 2) total_litros
             , round(sum(abastecimento.kmhs_rodados), 1) total_kmhs_rodados
             , count(*) qtde_abastecimentos
        %s
        group by abastecimento.cod_equipamento, substr(equipamento.descricao,0,50)
        order by total_litros desc
        ) where rownum <= %d
        """.formatted(FROM_WHERE_BASE, MAX_TOTAL);

    /**
     * @param dataIni        obrigatório, yyyy-MM-dd (data do abastecimento >=)
     * @param dataFim        obrigatório, yyyy-MM-dd (data do abastecimento <=)
     * @param codEquipamento opcional
     * @param codTipoCliente opcional
     * @param combustivel    opcional, trecho da descrição do combustível
     * @param agrupar        opcional: null/"detalhado" (linha a linha),
     *                       "combustivel" (soma por combustível) ou
     *                       "equipamento" (soma por equipamento, maior
     *                       consumo primeiro — usado para "top N")
     */
    public List<Map<String, Object>> buscar(String dataIni, String dataFim, Integer codEquipamento,
                                            Integer codTipoCliente, String combustivel, String agrupar) {

        StringBuilder filtros = new StringBuilder();
        List<Object> params = new ArrayList<>();
        params.add(paraDDMMYYYY(dataIni));
        params.add(paraDDMMYYYY(dataFim));

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

        String template = switch (agrupar == null ? "" : agrupar.trim().toLowerCase()) {
            case "combustivel" -> SQL_POR_COMBUSTIVEL;
            case "equipamento" -> SQL_POR_EQUIPAMENTO;
            default -> SQL_DETALHADO;
        };
        String sql = template.replace("/*FILTROS*/", filtros.toString());

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

    /** Converte yyyy-MM-dd para DDMMYYYY (formato usado em abastecimento.data). */
    private static String paraDDMMYYYY(String isoDate) {
        String[] partes = isoDate.split("-");
        return partes[2] + partes[1] + partes[0];
    }
}
