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
 * Divergências de nota fiscal ainda sem autorização (nr_autorizacao = '0').
 *
 * São duas origens unidas: a divergência que já aponta para uma ordem de
 * compra (divergencianf.nroc preenchido) e a que só tem a sequência da nota,
 * cuja ordem vem pelos itens de entrada (divergencianf.nroc nulo). Por isso a
 * mesma sequência pode voltar em várias linhas — uma por divergência, e às
 * vezes por ordem de compra. Quem consome agrupa por sequencia_nf.
 *
 * Ao contrário dos alertas de ordem de compra e de variação de preço, esta
 * consulta não filtra por aprovador: não há id_logon envolvido, então todo
 * destinatário do agendamento recebe a mesma lista.
 *
 * As duas ordens excluídas (29479 e 119428) vinham da consulta original e
 * foram mantidas.
 *
 * Colunas: sequencia_nf, nroc, entrada, compra, descricaodivergencia,
 * cod_fornecedor, nome_fornecedor, nome_comprador, cod_funcionario.
 */
public class DivergenciaNfDAO {

    private static final Logger LOG = Logger.getLogger(DivergenciaNfDAO.class.getName());

    private static final String SQL = """
        select divergencianf.sequencia_nf, divergencianf.nroc, divergencianf.entrada, divergencianf.compra,
               divergencia.descricao DescricaoDivergencia, oc.cod_fornecedor,
               material.fn_buscanomefornec(oc.cod_fornecedor,sysdate) as nome_fornecedor,
               rh.fn_nomefuncionario(1,oc.cod_funcionario) as nome_comprador,
               oc.cod_funcionario
        from material.material,
             material.divergencia,
             material.divergencianf divergencianf,
             material.ordemcompra oc
        where material.cod_material       = divergencianf.cod_material
          and divergencia.cod_divergencia = divergencianf.cod_divergencia
          and divergencianf.nr_autorizacao = '0'
          and divergencianf.nroc = oc.nroc
          and oc.nroc not in (29479,119428)

        union all

        select it.sequencia_nf, it.nroc, divergencianf.entrada, divergencianf.compra,
               divergencia.descricao DescricaoDivergencia, oc.cod_fornecedor,
               material.fn_buscanomefornec(oc.cod_fornecedor,sysdate) as nome_fornecedor,
               rh.fn_nomefuncionario(1,oc.cod_funcionario) as nome_comprador,
               oc.cod_funcionario
        from material.divergencia,
             material.divergencianf divergencianf,
             material.itensentrada it,
             material.ordemcompra oc
        where divergencia.cod_divergencia = divergencianf.cod_divergencia
          and divergencianf.nr_autorizacao = '0'
          and divergencianf.sequencia_nf = it.sequencia_nf
          and divergencianf.nroc is null
          and it.nroc = oc.nroc
          and it.nroc not in (29479,119428)
        group by it.sequencia_nf, it.nroc, divergencianf.entrada, divergencianf.compra,
                 divergencia.descricao, oc.cod_fornecedor,
                 rh.fn_nomefuncionario(1,oc.cod_funcionario), oc.cod_funcionario
        """;

    /** Uma linha por divergência; a mesma sequencia_nf pode aparecer várias vezes. */
    public List<Map<String, Object>> buscarPendentes() {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL);
             ResultSet rs = ps.executeQuery()) {

            return RowMapperUtil.toList(rs);

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar divergências de nota fiscal: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de divergências de nota fiscal: " + e.getMessage(), e);
        }
    }
}
