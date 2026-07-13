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
 * DAO da consulta de precipitação pluviométrica (chuvas), sobre
 * agricola.vw_lancamento_meteorolog_tipo (tipos 'P' e 'EP') — usada como
 * fonte de dados do chatbot agrícola (via n8n) para perguntas sobre chuva.
 *
 * Não usa a geral.fn_autorizacao_empresa da consulta original do ERP: aquela
 * função valida a permissão do usuário logado no ERP, e a intranet conecta
 * com usuário de serviço próprio (empresa fixada em 1/1/1, como no original).
 *
 * Todos os filtros são opcionais. Com agruparPorMes=true a precipitação é
 * somada por mês e ponto de coleta direto no banco — recomendado para o
 * agente, que só recebe uma amostra pequena de linhas.
 */
public class AgricolaChuvaDAO {

    private static final Logger LOG = Logger.getLogger(AgricolaChuvaDAO.class.getName());

    /** Teto absoluto de linhas devolvidas (protege memória do Tomcat/export). */
    private static final int MAX_TOTAL = 20000;

    private static final String SQL_DIA = """
        select * from (
        SELECT lancamento_meteorologico.data_coleta     data_coleta
             , lancamento_meteorologico.cod_pontocoleta cod_pontocoleta
             , lancamento_meteorologico.descricao       ponto_coleta
             , lancamento_meteorologico.precipitacao    precipitacao_mm
          FROM agricola.vw_lancamento_meteorolog_tipo lancamento_meteorologico
         WHERE lancamento_meteorologico.cod_grupoempresa = 1
           and lancamento_meteorologico.cod_empresa      = 1
           and lancamento_meteorologico.cod_filial       = 1
           AND lancamento_meteorologico.tipo IN ('P', 'EP')
           /*FILTROS*/
         ORDER BY lancamento_meteorologico.data_coleta
                , lancamento_meteorologico.cod_pontocoleta
        ) where rownum <= %d
        """.formatted(MAX_TOTAL);

    private static final String SQL_MES = """
        select * from (
        SELECT to_char(trunc(lancamento_meteorologico.data_coleta, 'MM'), 'MM/YYYY') mes_ano
             , lancamento_meteorologico.cod_pontocoleta                              cod_pontocoleta
             , lancamento_meteorologico.descricao                                    ponto_coleta
             , round(sum(lancamento_meteorologico.precipitacao), 1)                  precipitacao_total_mm
             , count(*)                                                              qtde_coletas
          FROM agricola.vw_lancamento_meteorolog_tipo lancamento_meteorologico
         WHERE lancamento_meteorologico.cod_grupoempresa = 1
           and lancamento_meteorologico.cod_empresa      = 1
           and lancamento_meteorologico.cod_filial       = 1
           AND lancamento_meteorologico.tipo IN ('P', 'EP')
           /*FILTROS*/
         GROUP BY trunc(lancamento_meteorologico.data_coleta, 'MM')
                , lancamento_meteorologico.cod_pontocoleta
                , lancamento_meteorologico.descricao
         ORDER BY trunc(lancamento_meteorologico.data_coleta, 'MM')
                , lancamento_meteorologico.cod_pontocoleta
        ) where rownum <= %d
        """.formatted(MAX_TOTAL);

    /**
     * @param dataIni       opcional, formato yyyy-MM-dd (data da coleta >=)
     * @param dataFim       opcional, formato yyyy-MM-dd (data da coleta <=)
     * @param ponto         opcional: código numérico do ponto de coleta ou
     *                      trecho da descrição (busca contém, sem
     *                      diferenciar maiúsculas)
     * @param agruparPorMes true = soma a precipitação por mês/ponto de coleta
     */
    public List<Map<String, Object>> buscar(String dataIni, String dataFim,
                                            String ponto, boolean agruparPorMes) {

        StringBuilder filtros = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (dataIni != null && !dataIni.isBlank()) {
            filtros.append(" and trunc(lancamento_meteorologico.data_coleta) >= to_date(?, 'YYYY-MM-DD')\n");
            params.add(dataIni.trim());
        }
        if (dataFim != null && !dataFim.isBlank()) {
            filtros.append(" and trunc(lancamento_meteorologico.data_coleta) <= to_date(?, 'YYYY-MM-DD')\n");
            params.add(dataFim.trim());
        }
        if (ponto != null && !ponto.isBlank()) {
            String p = ponto.trim();
            if (p.matches("\\d+")) {
                filtros.append(" and lancamento_meteorologico.cod_pontocoleta = ?\n");
                params.add(Integer.valueOf(p));
            } else {
                filtros.append(" and upper(lancamento_meteorologico.descricao) like '%'||upper(?)||'%'\n");
                params.add(p);
            }
        }

        String sql = (agruparPorMes ? SQL_MES : SQL_DIA).replace("/*FILTROS*/", filtros.toString());

        try (Connection conn = AgroOracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar chuvas: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de chuvas: " + e.getMessage(), e);
        }
    }
}
