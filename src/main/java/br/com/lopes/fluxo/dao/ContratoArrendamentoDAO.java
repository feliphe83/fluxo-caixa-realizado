package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
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
 * Contratos de arrendamento (tipo 12) que vencem nos próximos 90 dias.
 *
 * A janela é a da consulta original — hoje entre o término menos 90 dias e o
 * próprio término —, então um contrato entra na lista três meses antes de
 * vencer e permanece nela até a data final.
 *
 * Sem parâmetro nenhum: não há alçada nem recorte configurável, todo
 * destinatário do agendamento recebe a mesma lista.
 *
 * Atenção ao consumir: o outer join com historicocontrato pode devolver o
 * mesmo contrato em mais de uma linha (uma por histórico vigente). Nenhuma
 * coluna do histórico entra na mensagem, então quem consome consolida por
 * contrato antes de enviar.
 *
 * Colunas usadas na mensagem: numerocontrato, fornecedor, descricaoresumida,
 * datatermino, diasparavencer.
 */
public class ContratoArrendamentoDAO {

    private static final Logger LOG = Logger.getLogger(ContratoArrendamentoDAO.class.getName());

    private static final String SQL = """
        SELECT CONTRATO.COD_GRUPOEMPRESA
             , CONTRATO.COD_EMPRESA
             , CONTRATO.COD_FILIAL
             , CONTRATO.NUMEROCONTRATO
             , contratovigencia.DATAINICIO
             , contratovigencia.DATATERMINO
             , contratovigencia.CONTRATOJURIDICO
             , PESSOA.NOME FORNECEDOR
             , CONTRATO.COD_TIPOCONTRATO
             , TIPOCONTRATO.DESCRICAO DESCRICAOTIPOCONTRATO
             , CONTRATO.DESCRICAORESUMIDA
             , HISTORICOCONTRATO.VALORTOTAL
             , HISTORICOCONTRATO.QTDEPARCELAS
             , DECODE(HISTORICOCONTRATO.FIXOVARIAVEL,'F','FIXO','V','VARIÁVEL','???') FIXOVARIAVEL
             , TRUNC(contratovigencia.datatermino) - TRUNC(SYSDATE) diasParaVencer
        FROM   RH.PESSOA
             , MATERIAL.FORNECEDOR
             , FINANCEIRO.TIPOCONTRATO
             , FINANCEIRO.INDICEFINANCEIRO
             , FINANCEIRO.HISTORICOCONTRATO
             , FINANCEIRO.CONTRATOVIGENCIA
             , FINANCEIRO.CONTRATO
        WHERE  pessoa.cod_pessoa = fornecedor.cod_pessoa
        AND    fornecedor.cod_fornecedor = contrato.cod_fornecedor
        AND    rh.intersecao(contrato.data_criacao,contrato.data_criacao,tipocontrato.datainicio,tipocontrato.datafim) = 'TRUE'
        AND    tipocontrato.cod_grupoempresa = contrato.cod_grupoempresa
        AND    tipocontrato.cod_empresa = contrato.cod_empresa
        AND    tipocontrato.cod_filial = contrato.cod_filial
        AND    tipocontrato.cod_tipocontrato = contrato.cod_tipocontrato
        AND    indicefinanceiro.cod_indicefinanceiro (+)= historicocontrato.cod_indicefinanceiro
        AND   (rh.intersecao(contratovigencia.datainicio,contratovigencia.datainicio,historicocontrato.datainicio,historicocontrato.datatermino) = 'TRUE' OR historicocontrato.datainicio IS NULL)
        AND    historicocontrato.cod_grupoempresa (+)= contratovigencia.cod_grupoempresa
        AND    historicocontrato.numerocontrato (+)= contratovigencia.numerocontrato
        AND    TRUNC(SYSDATE) BETWEEN TRUNC(contratovigencia.DATATERMINO - 90) AND TRUNC(contratovigencia.DATATERMINO)
        AND    contratovigencia.cod_grupoempresa = contrato.cod_grupoempresa
        AND    contratovigencia.numerocontrato = contrato.numerocontrato
        AND    contratovigencia.DATATERMINO is NOT NULL
        AND    CONTRATO.COD_TIPOCONTRATO = 12
        AND    contrato.finalizado = 'F'
        AND    contrato.cod_empresa = 1
        AND    contrato.cod_filial = 1
        AND    contrato.cod_grupoempresa = 1
        ORDER BY CONTRATO.COD_GRUPOEMPRESA, contrato.numerocontrato
        """;

    public List<Map<String, Object>> buscarAVencer() {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL);
             ResultSet rs = ps.executeQuery()) {

            return RowMapperUtil.toList(rs);

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar contratos de arrendamento a vencer: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de contratos de arrendamento: " + e.getMessage(), e);
        }
    }
}
