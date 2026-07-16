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
 * tabelas relacionadas: preço calculado via posto.f_preco_combustivel,
 * classificação por objeto de custo, fazenda, proprietário do equipamento),
 * usada como fonte de dados do Dr. Alfredo (chat) para perguntas sobre
 * combustível.
 *
 * Período de abastecimento é obrigatório. Equipamento, tipo de cliente e
 * descrição do combustível são filtros opcionais. Os parâmetros de relatório
 * do formulário original (frentista, bomba, almoxarifado, tipo de pagamento,
 * operação agrícola, pessoa — todos via geral.csmultifiltro/cod_formulario
 * 8109) não são expostos aqui: ficam sempre resolvidos como "sem filtro",
 * igual ao valor capturado na consulta original de referência.
 *
 * Por padrão (agrupar nulo/vazio ou "combustivel"), os litros e o valor
 * (R$) são somados direto no banco por combustível (ordenado do maior para
 * o menor consumo) — é o formato certo para "quanto de combustível foi
 * abastecido". Com agrupar=equipamento, a soma é por equipamento (usado
 * para "top N por consumo"). O detalhamento linha a linha só entra com
 * agrupar=detalhado, pedido explicitamente.
 */
public class AgroCombustivelDAO {

    private static final Logger LOG = Logger.getLogger(AgroCombustivelDAO.class.getName());

    /** Teto absoluto de linhas devolvidas (protege memória do Tomcat/export). */
    private static final int MAX_TOTAL = 20000;

    // Nível intermediário: uma linha por abastecimento, com preço calculado
    // (posto.f_preco_combustivel) e classificação por objeto de custo —
    // reaproveitado tanto no modo detalhado quanto nos modos agregados
    // (agrupar soma sobre este resultado).
    //
    // abastecimento.data é comparada como string DDMMYYYY (sem separadores,
    // sem to_date()) — forma já validada em produção para esta coluna.
    private static final String SUBQUERY_DETALHE = """
        select tmp.* , tmp.nome_fornecedor nome_proprietario
             , posto.fn_busca_arvore_objetocusto(tmp.cod_objetocusto_detalhe,'C','NG')                   cod_negocio
             , nvl(posto.fn_busca_arvore_objetocusto(tmp.cod_objetocusto_detalhe,'D','NG'),'Não possui') desc_negocio
             , posto.fn_busca_arvore_objetocusto(tmp.cod_objetocusto_detalhe,'C','PR')                   cod_processo
             , nvl(posto.fn_busca_arvore_objetocusto(tmp.cod_objetocusto_detalhe,'D','PR'),'Não possui') desc_processo
             , posto.fn_busca_arvore_objetocusto(tmp.cod_objetocusto_detalhe,'C','SP')                   cod_subprocesso
             , nvl(posto.fn_busca_arvore_objetocusto(tmp.cod_objetocusto_detalhe,'D','SP'),'Não possui') desc_subprocesso
             , posto.fn_busca_arvore_objetocusto(tmp.cod_objetocusto_detalhe,'C','AT')                   cod_atividade
             , nvl(posto.fn_busca_arvore_objetocusto(tmp.cod_objetocusto_detalhe,'D','AT'),'Não possui') desc_atividade
             , rh.fn_nomefuncionario(tmp.cod_grupoempresa, tmp.frentista) des_frentista
             , rh.fn_nomefuncionario(tmp.cod_grupoempresa,tmp.cod_funcionario) des_funcionario
             , (select pessoa.nome
                   from rh.pessoa
                 where pessoa.cod_pessoa = tmp.cod_pessoa) des_pessoa
             , (select pessoa.nome
                   from rh.pessoa
                 where pessoa.cod_pessoa = tmp.cod_terceiro) des_terceiro
             , tmp.valor_unit valor_unitario
             , tmp.valor_tot valor_total
          from (
                select equipamento.cod_equipamento
                     , equipamento.descricao des_equipamento
                     , to_date(to_char(abastecimento.data,'dd/mm/rrrr')||' '||abastecimento.hora_ini,'dd/mm/rrrr hh24:mi:ss') datahora
                     , material.fn_buscanomefornec(proprietario.cod_fornecedor,sysdate) nome_fornecedor
                     , abastecimento.numero_ficha
                     , abastecimento.placa
                     , abastecimento.cod_almoxarifado_destino
                     , abastecimento.cod_funcionario
                     , abastecimento.cod_grupoempresa
                     , abastecimento.cod_grupoempresa||'.'||abastecimento.cod_empresa||'.'||abastecimento.cod_filial codigo_GEF
                     , geral.fn_busca_info_gef(abastecimento.cod_grupoempresa,abastecimento.cod_empresa,abastecimento.cod_filial,'FN') nome_GEF
                     , (to_char(abastecimento.data,'YYYY/MM')) anomes
                     , abastecimento.cod_fornecedor
                     , geral.fn_inf_colaborador(abastecimento.cod_fornecedor,'RAZAO_SOCIAL') des_fornecedor
                     , posto.procura_codigo_pessoa(abastecimento.cod_funcionario, abastecimento.cod_fornecedor, abastecimento.cod_grupoempresa, abastecimento.cod_pessoa) cod_pessoa
                     , abastecimento.cod_terceiro
                     , nvl(automotivo.fn_busca_prefixo_equipamento (abastecimento.cod_grupoempresaequipamento
                                                                  , abastecimento.cod_equipamento
                                                                  , abastecimento.data)
                                                                  , equipamento.cod_equipamento) prefixo
                     , abastecimento.data
                     , abastecimento.hora_ini
                     , abastecimento.qtde_litros
                     , abastecimento.km_ho
                     , nvl(posto.f_preco_combustivel(abastecimento.cod_material, abastecimento.cod_tipocliente,sysdate, abastecimento.cod_grupoempresa, abastecimento.cod_empresa, abastecimento.cod_filial),0) valor_unit
                     , nvl(posto.f_preco_combustivel(abastecimento.cod_material, abastecimento.cod_tipocliente,sysdate, abastecimento.cod_grupoempresa, abastecimento.cod_empresa, abastecimento.cod_filial),0)*abastecimento.qtde_litros valor_tot
                     , fazenda.cod_fazenda
                     , fazenda.descricao des_fazenda
                     , abastecimento.frentista
                     , operacaoagricola.cod_operacaoagricola
                     , operacaoagricola.descricao des_operacaoagricola
                     , abastecimento.cod_grupoempresaalmoxarifado
                     , abastecimento.cod_empresaalmoxarifado
                     , abastecimento.cod_filialalmoxarifado
                     , almoxarifado.cod_almoxarifado
                     , almoxarifado.descricaoalmoxarifado des_almoxarifado
                     , bomba.cod_bomba
                     , 'BOMBA ' || bomba.cod_bomba des_bomba
                     , material.cod_material cod_combustivel
                     , material.descricao des_combustivel
                     , tipocliente.cod_tipocliente
                     , tipocliente.descricao tipocliente
                     , modeloequipamento.cod_modelo
                     , modeloequipamento.descricaomodelo
                     , tipopagamento.cod_tipopagamento
                     , tipopagamento.descricao des_tipopagamento
                     , estado.estado
                     , cidade.estado sigla
                     , cidade.cod_cidade
                     , cidade.descricao descricao_cidade
                     , ' '  Cod_tipoequipamento
                     , ' ' Des_tipoequipamento
                     , abastecimento.observacao
                     , decode(abastecimento.cod_objetocusto
                             ,null
                             ,to_number(automotivo.fn_busca_inf_equipamento(abastecimento.cod_grupoempresa
                                                                           ,abastecimento.cod_empresa
                                                                           ,abastecimento.cod_filial
                                                                           ,abastecimento.cod_equipamento
                                                                           ,abastecimento.data
                                                                           ,'COD_OBJETOCUSTO'))
                             ,abastecimento.cod_objetocusto) cod_objetocusto_detalhe
                     , palm.descricao desc_palm
                     , nvl(abastecimento.idpalm,0) cod_palm
                     , nvl(automotivo.fn_busca_inf_equipamento(abastecimento.cod_grupoempresa, abastecimento.cod_empresa, abastecimento.cod_filial,abastecimento.cod_equipamento, abastecimento.data,'GRUPOITEMCUSTO'),0) cod_grupo_item_custo
                     , nvl(automotivo.fn_busca_inf_equipamento(abastecimento.cod_grupoempresa, abastecimento.cod_empresa, abastecimento.cod_filial,abastecimento.cod_equipamento, abastecimento.data,'DESC_GRUPOITEMCUSTO'),'NÃO INFORMADO')  descricao_grupo_item_custo
                     , abastecimento.kmhs_rodados
                     , proprietario.cod_fornecedor cod_proprietario
                     , nvl((select componentes.cod_sistema||' - '||sistemas.descricao
                                from  automotivo.sistemas sistemas
                                     ,automotivo.componentes componentes
                                where sistemas.cod_sistema     = componentes.cod_sistema
                                and componentes.cod_sistema    = abastecimento.cod_sistema
                                and componentes.cod_componente = abastecimento.cod_componente),'Não Possui') sistema
                     , nvl((select componentes.cod_componente||' - '||componentes.descricao
                                from  automotivo.sistemas sistemas
                                     ,automotivo.componentes componentes
                                where sistemas.cod_sistema     = componentes.cod_sistema
                                and componentes.cod_sistema    = abastecimento.cod_sistema
                                and componentes.cod_componente = abastecimento.cod_componente),'Não Possui') componente
                     , (select operacaoobjetocusto.cod_objetocusto
                        from   rh.operacaoobjetocusto operacaoobjetocusto
                        where (operacaoobjetocusto.data_inicio         <= abastecimento.data
                        and    operacaoobjetocusto.data_termino         is null
                        or     operacaoobjetocusto.data_inicio         <= abastecimento.data
                        and    operacaoobjetocusto.data_termino        >= abastecimento.data )
                        and   (operacaoobjetocusto.cod_grupoempresa     = abastecimento.cod_grupoempresa or operacaoobjetocusto.cod_grupoempresa is null)
                        and   (operacaoobjetocusto.cod_empresa          = abastecimento.cod_empresa      or operacaoobjetocusto.cod_empresa is null)
                        and   (operacaoobjetocusto.cod_filial           = abastecimento.cod_filial       or operacaoobjetocusto.cod_filial is null)
                        and    operacaoobjetocusto.cod_tipofazenda      = (select agricola.historico_fazenda.cod_tipofazenda
                                                                           from   agricola.historico_fazenda
                                                                           where (historico_fazenda.data_inicio     <= abastecimento.data
                                                                           and    historico_fazenda.data_fim         is null
                                                                           or     historico_fazenda.data_inicio     <= abastecimento.data
                                                                           and    historico_fazenda.data_fim        >= abastecimento.data )
                                                                           and    historico_fazenda.cod_fazenda      = fazenda.cod_fazenda
                                                                           and    rownum <= 1 )
                        and    operacaoobjetocusto.cod_operacaoagricola =  operacaoagricola.cod_operacaoagricola
                        and    rownum <= 1
                        ) cod_objeto_tarefa
                  from
                       financeiro.tipopagamento
                     , posto.bomba
                     , material.almoxarifado
                     , rh.operacaoagricola
                     , material.estado
                     , rh.cidade
                     , agricola.fazenda
                     , automotivo.modeloequipamento
                     , automotivo.equipamento
                     , material.material
                     , posto.tipocliente
                     , cspalm.palm
                     , posto.abastecimento abastecimento
                     , automotivo.histproprietarioequip proprietario
                where 1=1

                  and (proprietario.data_inicial        <= to_date(to_char(abastecimento.data,'dd/mm/rrrr')||' '||abastecimento.hora_ini,'dd/mm/rrrr hh24:mi:ss')
                    or abastecimento.cod_equipamento      is null)
                  and (proprietario.data_final         >= to_date(to_char(abastecimento.data,'dd/mm/rrrr')||' '||abastecimento.hora_ini,'dd/mm/rrrr hh24:mi:ss')
                    or proprietario.data_final            is null
                    or abastecimento.cod_equipamento      is null)
                  and proprietario.cod_equipamento  (+)= equipamento.cod_equipamento
                  and proprietario.cod_grupoempresa (+)= equipamento.cod_grupoempresa

                  and nvl(abastecimento.reservatorio,'N')      = 'N'

                  and tipopagamento.cod_tipopagamento       (+)= abastecimento.cod_tipopagamento
                  and bomba.cod_bomba                          = abastecimento.cod_bomba
                  and almoxarifado.cod_almoxarifado            = abastecimento.cod_almoxarifado
                  and almoxarifado.cod_filial                  = abastecimento.cod_filialalmoxarifado
                  and almoxarifado.cod_empresa                 = abastecimento.cod_empresaalmoxarifado
                  and almoxarifado.cod_grupoempresa            = abastecimento.cod_grupoempresaalmoxarifado
                  and operacaoagricola.cod_operacaoagricola (+)= abastecimento.cod_operacaoagricola

                  and estado.sigla                          (+)= cidade.estado
                  and cidade.cod_cidade                     (+)= fazenda.cod_cidade
                  and fazenda.cod_fazenda                   (+)= abastecimento.cod_fazenda

                  and modeloequipamento.cod_modelo          (+)= equipamento.cod_modelo

                  and equipamento.cod_equipamento           (+)= abastecimento.cod_equipamento
                  and equipamento.cod_grupoempresa          (+)= abastecimento.cod_grupoempresaequipamento

                  and material.cod_material                    = abastecimento.cod_material
                  and tipocliente.cod_tipocliente              = abastecimento.cod_tipocliente
                  and palm.codigo                           (+)= abastecimento.idpalm

                  and abastecimento.cod_componente is null

                  and abastecimento.data                       >= ?
                  and abastecimento.data                       <= ?

                  and abastecimento.cod_filial = 1
                  and abastecimento.cod_empresa = 1
                  and abastecimento.cod_grupoempresa = 1
                  /*FILTROS*/
               ) tmp
        """;

    private static final String SQL_DETALHADO = """
        select * from (
        %s
        order by cod_combustivel, datahora
        ) where rownum <= %d
        """.formatted(SUBQUERY_DETALHE, MAX_TOTAL);

    private static final String SQL_POR_COMBUSTIVEL = """
        select * from (
        select
               cod_combustivel, des_combustivel
             , round(sum(qtde_litros), 2) total_litros
             , round(sum(valor_total), 2) valor_total
             , round(decode(sum(qtde_litros),0,0,sum(valor_total)/sum(qtde_litros)),2) valor_unitario_medio
             , count(*) qtde_abastecimentos
        from ( %s )
        group by cod_combustivel, des_combustivel
        order by total_litros desc
        ) where rownum <= %d
        """.formatted(SUBQUERY_DETALHE, MAX_TOTAL);

    private static final String SQL_POR_EQUIPAMENTO = """
        select * from (
        select
               cod_equipamento, des_equipamento
             , round(sum(qtde_litros), 2) total_litros
             , round(sum(valor_total), 2) valor_total
             , round(sum(kmhs_rodados), 1) total_kmhs_rodados
             , count(*) qtde_abastecimentos
        from ( %s )
        group by cod_equipamento, des_equipamento
        order by total_litros desc
        ) where rownum <= %d
        """.formatted(SUBQUERY_DETALHE, MAX_TOTAL);

    /**
     * @param dataIni        obrigatório, yyyy-MM-dd (data do abastecimento >=)
     * @param dataFim        obrigatório, yyyy-MM-dd (data do abastecimento <=)
     * @param codEquipamento opcional
     * @param codTipoCliente opcional
     * @param combustivel    opcional, trecho da descrição do combustível
     * @param agrupar        opcional: null/"combustivel" (padrão — soma por
     *                       combustível), "equipamento" (soma por
     *                       equipamento, maior consumo primeiro — usado
     *                       para "top N") ou "detalhado" (linha a linha, só
     *                       quando pedido explicitamente)
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
            case "detalhado" -> SQL_DETALHADO;
            case "equipamento" -> SQL_POR_EQUIPAMENTO;
            default -> SQL_POR_COMBUSTIVEL;
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
