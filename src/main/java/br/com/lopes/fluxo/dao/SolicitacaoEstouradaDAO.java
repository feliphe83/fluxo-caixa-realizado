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
 * Solicitações de compra em estouro de orçamento, aguardando aprovação.
 *
 * É o estágio da SOLICITAÇÃO (material.solicitacaocompra com
 * orcamento_estourado = 'S' e solicitacaoaprovada = 'F'), anterior ao estágio da
 * cotação tratado por {@link OrcamentoEstouradoDAO}. Alimenta o alerta de
 * WhatsApp AlertaSolicitacaoEstouradaHandler.
 *
 * Consulta base: "5874 - Aprovação de Orçamento Acima do Planejado" do ERP.
 * Empresa fixada em 1/1/1, como nas demais consultas da intranet.
 */
public class SolicitacaoEstouradaDAO {

    private static final Logger LOG = Logger.getLogger(SolicitacaoEstouradaDAO.class.getName());

    private static final String SQL = """
        select solicitacaocompra.cod_objetocusto
             , objetocusto.descricao as descobjeto
             , solicitacaocompra.nr_solicitacao
             , solicitacaocompra.data
             , solicitacaocompra.qtdesolicitada
             , solicitacaocompra.cod_unidade
             , solicitacaocompra.cod_material
             , material.descricao as descmaterial
             , solicitacaocompra.cod_funcionario
             , rh.fn_nomefuncionario(solicitacaocompra.cod_grupoempresa,
                                     solicitacaocompra.cod_funcionario) nome
             , objetocusto.negocio ||' - '||
               objetocusto.processo ||' - '||
               objetocusto.subprocesso ||' - '||
               objetocusto.atividade as classificacao
             , solicitacaocompra.cod_equipamento
             , solicitacaocompra.datautilizacaoprevista
             , decode(materialporfilial.curva_xyz, 'X', 'ALTA', 'Y', 'MEDIA', 'BAIXA') as prioridade
             , solicitacaocompra.cod_almoxarifado
             , almoxarifado.descricaoalmoxarifado
             , solicitacaocompra.observacao
             , solicitacaocompra.data_estouro data_estouro
             , (select usuario.logon from segurancanovo.usuario
                 where usuario.id_logon = solicitacaocompra.id_logon_estouro) usuario
        from   material.almoxarifado
             , material.materialporfilial
             , material.material
             , rh.objetocusto
             , material.solicitacaocompra
        where  almoxarifado.cod_almoxarifado       = solicitacaocompra.cod_almoxarifado
        and    almoxarifado.cod_filial             = materialporfilial.cod_filial
        and    almoxarifado.cod_empresa            = materialporfilial.cod_empresa
        and    almoxarifado.cod_grupoempresa       = materialporfilial.cod_grupoempresa
        and    material.cod_material               = materialporfilial.cod_material
        and    materialporfilial.cod_filial        = solicitacaocompra.cod_filial_destino
        and    materialporfilial.cod_empresa       = solicitacaocompra.cod_empresa_destino
        and    materialporfilial.cod_grupoempresa  = solicitacaocompra.cod_grupoempresa_destino
        and    materialporfilial.cod_material      = solicitacaocompra.cod_material
        and    materialporfilial.situacao          = 'A'
        and    rh.intersecao(solicitacaocompra.data, solicitacaocompra.data,
                             materialporfilial.datainicio, materialporfilial.datatermino) = 'TRUE'
        and    objetocusto.cod_objetocusto     (+)= solicitacaocompra.cod_objetocusto
        and    solicitacaocompra.orcamento_estourado = 'S'
        and    solicitacaocompra.solicitacaoaprovada = 'F'
        and    solicitacaocompra.situacao           <> 'C'
        and    solicitacaocompra.cod_grupoempresa    = 1
        and    solicitacaocompra.cod_empresa_destino = 1
        and    solicitacaocompra.cod_filial_destino  = 1
        order  by solicitacaocompra.nr_solicitacao
        """;

    /** As solicitações de compra em estouro de orçamento pendentes de aprovação. */
    public List<Map<String, Object>> buscar() {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL);
             ResultSet rs = ps.executeQuery()) {
            return RowMapperUtil.toList(rs);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar solicitações em estouro: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de solicitações em estouro: " + e.getMessage(), e);
        }
    }
}
