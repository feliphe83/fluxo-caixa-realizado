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
 * DAO da Análise de Folha de Pagamento Rural (Apontamento Geral) — mesma
 * consulta fornecida pelo Departamento Agrícola (rh.apontamento), usada como
 * fonte de dados para o relatório "Fazendas Próprias, Terceiros e Geral" por
 * Atividade Principal.
 *
 * A classificação por Atividade Principal (Preparo de Solo, Plantio de Cana
 * etc.) e a separação Própria/Terceiro é feita em Java, pelo servlet — este
 * DAO só devolve as linhas de apontamento no período, uma por lançamento.
 */
public class AnaliseFolhaRuralDAO {

    private static final Logger LOG = Logger.getLogger(AnaliseFolhaRuralDAO.class.getName());

    /** Teto absoluto de linhas devolvidas (protege memória do Tomcat). */
    private static final int MAX_TOTAL = 100000;

    private static final String SQL = """
        SELECT * FROM (
        SELECT
            o.cod_grupoempresa,
            o.cod_empresa AS empresa_apontamento,

            NVL(fa.cod_empresa, 0) AS cod_empresa_dona_fazenda,
            NVL(emp_faz.nome, 'Empresa Não Vinculada à Fazenda') AS nome_empresa_dona_fazenda,

            o.numero_apontamento,
            o.cod_safra,

            (SELECT hf.cod_tipofazenda
             FROM agricola.historico_fazenda hf
             WHERE hf.cod_fazenda = o.cod_fazenda
               AND TRUNC(o.data_apontamento) BETWEEN TRUNC(hf.data_inicio) AND NVL(TRUNC(hf.data_fim), TO_DATE('31/12/2099', 'dd/mm/yyyy'))
               AND ROWNUM = 1) AS tipo_fundo_agricola,

            (SELECT tf.cod_tipofazenda||' - '||tf.descricao
             FROM agricola.historico_fazenda hf
             INNER JOIN agricola.tipofazenda tf ON hf.cod_tipofazenda = tf.cod_tipofazenda
             WHERE hf.cod_fazenda = o.cod_fazenda
               AND TRUNC(o.data_apontamento) BETWEEN TRUNC(hf.data_inicio) AND NVL(TRUNC(hf.data_fim), TO_DATE('31/12/2099', 'dd/mm/yyyy'))
               AND ROWNUM = 1) AS desc_fundo_agricola,

            o.cod_fazenda,
            fa.descricao AS descricaofazenda,
            o.cod_talhao,
            o.data_apontamento,
            o.cod_tabelapreco,
            o.cod_itemtabelapreco,
            o.cod_funcionario,
            p.nome AS nomefuncionario,
            o.periodo,
            o.quantidade,
            o.valorunitario,
            o.qtde_apontada,
            o.valortotal,
            o.qtde_media,
            o.cod_fiscal,
            o.padrao_vlr_minino,
            o.complementa_diaria,
            o.cod_objetocusto,

            obj_atual.subprocesso AS sub_processo,
            obs.descricao AS descricaosubprocesso,

            t.cod_tiposervico,
            s.descricao AS descricaotiposervico,
            s.fator,
            s.formula,
            o.cod_agenciador,
            a.cod_funcionario AS cod_func_genciador,
            o.cod_grupoempresa_apontador,
            o.cod_funcionario_apontador,
            o.periodo_apontador,
            t.cod_unidade
        FROM rh.apontamento o
        INNER JOIN rh.itemtabelapreco t
            ON o.cod_grupoempresa = t.cod_grupoempresa
           AND o.cod_empresa = t.cod_empresa
           AND o.cod_tabelapreco = t.cod_tabelapreco
           AND o.cod_itemtabelapreco = t.cod_itemtabelapreco
        INNER JOIN rh.tiposervico s
            ON t.cod_tiposervico = s.cod_tiposervico
        INNER JOIN rh.funcionario fu
            ON o.cod_funcionario = fu.cod_funcionario
        INNER JOIN rh.fisica fi
            ON fu.cpf = fi.cpf
        INNER JOIN rh.pessoa p
            ON fi.cod_pessoa = p.cod_pessoa

        LEFT JOIN agricola.fazenda fa
            ON o.cod_fazenda = fa.cod_fazenda

        LEFT JOIN rh.empresa emp_faz
            ON fa.cod_grupoempresa = emp_faz.cod_grupoempresa
           AND fa.cod_empresa      = emp_faz.cod_empresa

        LEFT JOIN rh.agenciador a
            ON o.cod_agenciador = a.cod_agenciador

        LEFT JOIN rh.objetocusto obj_atual
            ON o.cod_objetocusto = obj_atual.cod_objetocusto
        LEFT JOIN rh.objetocusto obs
            ON obj_atual.negocio     = obs.negocio
           AND obj_atual.processo    = obs.processo
           AND obj_atual.subprocesso = obs.subprocesso
           AND obs.atividade         = 0
        WHERE o.data_apontamento BETWEEN TO_DATE(?, 'dd/mm/yyyy')
                                     AND TO_DATE(?, 'dd/mm/yyyy')
        ) WHERE ROWNUM <= %d
        """.formatted(MAX_TOTAL);

    /**
     * @param dataIni data inicial do apontamento (dd/mm/yyyy)
     * @param dataFim data final do apontamento (dd/mm/yyyy)
     */
    public List<Map<String, Object>> buscar(String dataIni, String dataFim) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            ps.setString(1, dataIni);
            ps.setString(2, dataFim);

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar análise de folha de pagamento rural: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de folha de pagamento rural: " + e.getMessage(), e);
        }
    }
}
