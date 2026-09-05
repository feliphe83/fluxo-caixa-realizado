package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entrada de Materiais no período — a mesma consulta que o formulário do ERP
 * "CS_SelecionaGEF" (nº 7823) roda quando alguém clica em gerar o relatório.
 *
 * COMO FUNCIONA. Duas etapas na MESMA conexão/sessão Oracle, obrigatoriamente:
 *  1. material.pr_rel_entradasperiodo povoa material.tmp_rel_entradasperiodo,
 *     que é uma tabela temporária DE SESSÃO — cada conexão só enxerga as
 *     linhas que ELA MESMA gravou, então rodar a etapa 2 numa conexão
 *     diferente veria a tabela vazia.
 *  2. Depois, um select comum lê essa tabela, decorada com a árvore de
 *     objeto de custo (negócio/processo/subprocesso) via
 *     posto.fn_busca_arvore_objetocusto — a mesma função usada no dashboard
 *     de orçamento de compras.
 *
 * Como cada chamada abre uma conexão nova (sem pool, ver OracleConnectionUtil)
 * e a fecha ao final do método, não há necessidade de limpar a tabela
 * temporária antes: a próxima chamada começa com uma sessão nova, e portanto
 * uma tmp_rel_entradasperiodo vazia.
 *
 * ID_LOGON, NOME_COMPONENTE e COD_FORMULARIO são fixos — identificam esta
 * tela de relatório DENTRO DO ERP (o "formulário" e o "componente" que o
 * procedimento espera), não o usuário logado na intranet. É a mesma
 * combinação que o formulário original do ERP já usava; só o período
 * (pd_data_ini_entr / pd_data_fim_entr) varia por chamada.
 */
public class EntradaMateriaisDAO {

    private static final Logger LOG = Logger.getLogger(EntradaMateriaisDAO.class.getName());

    private static final int ID_LOGON = 324;
    private static final String NOME_COMPONENTE = "CS_SelecionaGEF";
    private static final int COD_FORMULARIO = 7823;

    private static final String BLOCO_PROCEDIMENTO =
        "declare\n" +
        "   vn_existe number := 0;\n" +
        "begin\n" +
        "   select count(*)\n" +
        "   into   vn_existe\n" +
        "   from   geral.tmp_selecionagef\n" +
        "   where  id_logon        = " + ID_LOGON + "\n" +
        "   and    nome_componente = '" + NOME_COMPONENTE + "'\n" +
        "   and    cod_formulario  = " + COD_FORMULARIO + ";\n" +
        "\n" +
        "   if vn_existe = 0 then\n" +
        "      insert into geral.tmp_selecionagef ( id_logon\n" +
        "                                          , nome_componente\n" +
        "                                          , cod_formulario\n" +
        "                                          , cod_grupoempresa\n" +
        "                                          , cod_empresa\n" +
        "                                          , cod_filial )\n" +
        "                                   values ( " + ID_LOGON + "\n" +
        "                                          , '" + NOME_COMPONENTE + "'\n" +
        "                                          , " + COD_FORMULARIO + "\n" +
        "                                          , 1\n" +
        "                                          , 1\n" +
        "                                          , 1 );\n" +
        "      commit;\n" +
        "   end if;\n" +
        "\n" +
        "   material.pr_rel_entradasperiodo\n" +
        "   ( pn_codformulario              => " + COD_FORMULARIO + "\n" +
        "   , pn_idlogon                    => " + ID_LOGON + "\n" +
        "   , pv_nomecomponente             => '" + NOME_COMPONENTE + "'\n" +
        "   , pd_data_ini_entr              => ?\n" +
        "   , pd_data_fim_entr              => ?\n" +
        "   , pn_sequencia_nf               => 0\n" +
        "   , pn_nrnf                       => 0\n" +
        "   , pv_cod_material               => '0'\n" +
        "   , pn_cod_almoxarifado           => 0\n" +
        "   , pn_cod_aplicacao              => null\n" +
        "   , pn_cod_grupomaterial          => 99999\n" +
        "   , pn_cod_familia                => 99999\n" +
        "   , pn_cod_fornecedor             => 0\n" +
        "   , pv_cod_identificacao          => '0'\n" +
        "   , pv_cod_modelodocumento        => '0'\n" +
        "   , pv_cod_plano                  => '99999'\n" +
        "   , pv_cod_tipomaterial           => '0'\n" +
        "   , pv_cfop                       => '0'\n" +
        "   , pv_conhecimento               => 'N'\n" +
        "   , pv_entrada_confirmada         => 'T'\n" +
        "   , pv_sem_identificacao          => 'S'\n" +
        "   , pv_notafiscal_complementar    => 'N'\n" +
        "   , pv_somente_oc                 => 'N'\n" +
        "   , pn_nroc                       => 0\n" +
        "   , pd_data_ini_cont              => null\n" +
        "   , pd_data_fim_cont              => null\n" +
        "   , pv_usuariologon               => null\n" +
        "   , pn_cod_cidade                 => 0\n" +
        "   , pv_cod_aplicacao              => '0'\n" +
        "   , pv_somenteNFsemassociacao     => 'N'\n" +
        "   , pv_cod_tipocobranca           => '0'\n" +
        "   , pn_cod_subgrupo               => 99999\n" +
        "   , pv_cod_tipocfop               => '0'\n" +
        "   , pv_tp_parceiro                => 'T'\n" +
        "   , pd_data_ini_emissaonf         => null\n" +
        "   , pd_data_fim_emissaonf         => null\n" +
        "   , pn_cod_func_contagem          => 0\n" +
        "   , pn_cod_grupoempresa_func_cont => null\n" +
        "   , pn_usuario_digitacao          => 0\n" +
        "   , pv_apurar_frete_piscofins     => 'N'\n" +
        "   , pd_data_ini_criacaonf         => null\n" +
        "   , pd_data_fim_criacaonf         => null\n" +
        "   , pv_apenas_lote                => null\n" +
        "   , pv_cod_lote                   => '99999'\n" +
        "   );\n" +
        "end;";

    private static final String SQL_RESULTADO =
        "select t.*\n" +
        "     , posto.fn_busca_arvore_objetocusto(t.cod_objetocusto,'C','NG')                        cod_negocio\n" +
        "     , nvl(posto.fn_busca_arvore_objetocusto(t.cod_objetocusto,'D','NG'),'Sem negocio')      negocio\n" +
        "     , posto.fn_busca_arvore_objetocusto(t.cod_objetocusto,'C','PR')                         cod_processo\n" +
        "     , nvl(posto.fn_busca_arvore_objetocusto(t.cod_objetocusto,'D','PR'),'Sem processo')     processo\n" +
        "     , posto.fn_busca_arvore_objetocusto(t.cod_objetocusto,'C','SP')                         cod_subprocesso\n" +
        "     , nvl(posto.fn_busca_arvore_objetocusto(t.cod_objetocusto,'D','SP'),'Sem subprocesso')  subprocesso\n" +
        "from   material.tmp_rel_entradasperiodo t\n" +
        "order  by t.dataentrada\n" +
        "        , t.sequencia_nf\n" +
        "        , t.nrnf\n" +
        "        , t.serie\n" +
        "        , t.item";

    /** @return uma linha por item de nota fiscal de entrada, na ordem do relatório original. */
    public List<Map<String, Object>> buscar(LocalDate inicio, LocalDate fim) {
        try (Connection conn = OracleConnectionUtil.getConnection()) {
            executarProcedimento(conn, inicio, fim);
            return consultarResultado(conn);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao consultar entrada de materiais", e);
            throw new RuntimeException(mensagem(e), e);
        }
    }

    private void executarProcedimento(Connection conn, LocalDate inicio, LocalDate fim) throws SQLException {
        try (CallableStatement cs = conn.prepareCall(BLOCO_PROCEDIMENTO)) {
            cs.setDate(1, java.sql.Date.valueOf(inicio));
            cs.setDate(2, java.sql.Date.valueOf(fim));
            cs.execute();
        }
    }

    private List<Map<String, Object>> consultarResultado(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_RESULTADO);
             ResultSet rs = ps.executeQuery()) {
            return RowMapperUtil.toList(rs);
        }
    }

    private static String mensagem(SQLException e) {
        String m = e.getMessage() == null ? e.getClass().getName() : e.getMessage().trim();
        int q = m.indexOf('\n');
        return q > 0 ? m.substring(0, q) : m;
    }
}
