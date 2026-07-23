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
 * DAO da consulta de Ordem de Compra (material.ordemcompra e tabelas
 * relacionadas: cotação, resultado da cotação, itens da ordem, fornecedor,
 * aprovação e parcelas a pagar vinculadas), usada como fonte de dados do
 * Dr. Alfredo (chat) para perguntas sobre ordens de compra.
 *
 * Adaptada de um relatório Jasper (parâmetros $P{DATAINICIO}/$P{DATAFIM}
 * originais, aqui como bind ? em to_date(?, 'YYYY-MM-DD')) — o filtro de
 * período é sobre a data de VENCIMENTO da parcela vinculada à ordem
 * (parcelascontaspagar.datavcto), não a data da própria ordem de compra.
 *
 * A query original tinha um "modo agrupado" alternativo (branch
 * nvl('N','N') = 'S', sempre falsa — resquício de um parâmetro de relatório
 * desativado) que nunca retorna linha; omitido aqui por não alterar o
 * resultado. Mesma coisa para os filtros nvl('N','N')='N',
 * (cod_funcionario=0 or nvl(0,0)=0) e (cod_fornecedor=0 or nvl(0,0)=0): são
 * sempre verdadeiros (no-op), removidos sem mudar o resultado.
 *
 * Uma ordem de compra pode ter a parcela vinculada de duas formas — por
 * nota fiscal de entrada (origem=10) ou diretamente pelo número da ordem
 * como documento, com cod_tipocontaspagar=262 (ex.: adiantamento) — por
 * isso a consulta faz UNION ALL dos dois casos.
 */
public class OrdemCompraDAO {

    private static final Logger LOG = Logger.getLogger(OrdemCompraDAO.class.getName());

    /** Teto absoluto de linhas devolvidas (protege memória do Tomcat/export). */
    private static final int MAX_TOTAL = 20000;

    // Colunas finais (iguais nos dois blocos do UNION ALL).
    private static final String COLUNAS_FINAIS = """
        nroc, dataoc, cod_funcionario, cod_fornecedor, nome nome_fornecedor, observacao,
        cod_material, desc_material, qtde_aprovada qtde, preco,
        qtde_aprovada*preco-desconto1 total, qtde_aprovada*preco total2,
        null cod_objetocusto, endereco, bairro, cep, null fone, desc_cidade,
        dataaprovacao, nomeaprovador, cgc_cpf cgc, descontototal, desconto1,
        desconto1*-1 desconto2, cod_objeto, cod_unidade,
        custo.fn_busca_descricao_oc(cod_objeto) desc_objeto, documento,
        descricaoplano, datavcto, valorparcela
        """;

    private static final String GROUP_BY_FINAL = """
        group by nroc, dataoc, cod_funcionario, cod_fornecedor, nome, observacao,
                 cod_material, desc_material, qtde_aprovada, preco, endereco, bairro, cep,
                 desc_cidade, dataaprovacao, nomeaprovador, cgc_cpf, descontototal, desconto1,
                 cod_objeto, cod_unidade, descricaoplano, documento, datavcto, valorparcela
        """;

    // Corpo comum aos dois blocos (só muda o CTE vw_parcelas antes dele) —
    // liga ordem de compra, cotação/resultado da cotação, itens, fornecedor,
    // pessoa/endereço, plano de pagamento, frete, comprador e aprovação.
    private static final String TMP1_CORPO = """
        select tmp1.nroc, tmp1.nr_cotacao, tmp1.cod_plano, tmp1.dataoc, tmp1.cod_funcionario
             , tmp1.cod_grupoempresa_dest cod_grupoempresa, tmp1.cod_grupoempresa_dest, tmp1.cod_empresa, tmp1.cod_filial
             , tmp1.cod_fornecedor, tmp1.observacao, tmp1.provisaopelaordemouentrega, tmp1.cod_indicefinanceiro
             , tmp1.indicefinanceiro, tmp1.cod_material, tmp1.cod_material_relatorio
             , material.fn_buscadescmaterial(tmp1.cod_material) desc_material
             , material.fn_obterobjcustosolicitacao(tmp1.nr_cotacao, tmp1.cod_material) cod_objeto
             , tmp1.preco, tmp1.sequencia, tmp1.aliquota_icms, tmp1.observacao obs_rci, tmp1.nr_solicitacao, tmp1.perc_ipi
             , tmp1.Base_Icms_St, tmp1.Aliquota_Icms_St, tmp1.descontoporunidade, tmp1.descontoporcentagem
             , tmp1.vrdesc_rateio_ind, tmp1.descontototal, tmp1.prazoentrega, tmp1.dataentrega, tmp1.entrega
             , tmp1.precoparaxqtde, tmp1.preco_indice, tmp1.valor_indice_acordado, tmp1.data_indicefinanceiro
             , tmp1.quantidade_solic, tmp1.quantidade, tmp1.qtde_aprovada, tmp1.nome, tmp1.endereco
             , tmp1.complemento_endereco, tmp1.bairro, tmp1.cep, tmp1.cod_cidade, tmp1.estado, tmp1.nome_cidade desc_cidade
             , tmp1.cod_pessoa, tmp1.nomevendedor, tmp1.cod_tipocobranca, tmp1.cgc_cpf, tmp1.descricaoplano
             , tmp1.informarmanualmente, tmp1.descricaotipofrete, tmp1.cod_tipofrete, tmp1.nome_comprador
             , tmp1.email_comprador, tmp1.cod_fornecedor_frete, tmp1.telefone, tmp1.ramal, tmp1.database
             , tmp1.dataaprovacao, tmp1.nomeaprovador, tmp1.valor_icms_st
             , geral.fn_inf_colaborador(pn_cod_colaborador => tmp1.cod_fornecedor, pv_palavra_chave => 'RUC') RUC
             , (select estado.estado from material.estado estado where estado.sigla = tmp1.estado) departamentoPY
             , count(tmp1.nroc) over () qtdregistros, tmp1.documento, tmp1.datavcto, tmp1.valorparcela
             , material.fn_busca_descontos_oc(tmp1.nr_cotacao, tmp1.cod_plano, tmp1.cod_material, tmp1.nr_solicitacao) desconto1
             , (select cod_unidade from material.material where material.cod_material = tmp1.cod_material) cod_unidade
        from (
            select ordemcompra.nroc, ordemcompra.nr_cotacao, ordemcompra.cod_plano, ordemcompra.dataoc, ordemcompra.cod_funcionario
                 , ordemcompra.cod_grupoempresa_dest cod_grupoempresa, ordemcompra.cod_grupoempresa_dest
                 , ordemcompra.cod_empresa, ordemcompra.cod_filial, ordemcompra.cod_fornecedor, ordemcompra.observacao
                 , ordemcompra.provisaopelaordemouentrega, ordemcompra.cod_indicefinanceiro
                 , case when ordemcompra.cod_indicefinanceiro is not null
                        then (select ordemcompra.cod_indicefinanceiro || '-' || indicefinanceiro.descricao
                              from financeiro.indicefinanceiro
                              where indicefinanceiro.cod_indicefinanceiro = ordemcompra.cod_indicefinanceiro)
                        else (select parametro_financeiro.cod_indicefinanceiropadrao || '-' || indicefinanceiro.descricao
                              from financeiro.parametro_financeiro, financeiro.indicefinanceiro
                              where indicefinanceiro.cod_indicefinanceiro = parametro_financeiro.cod_indicefinanceiropadrao
                              and parametro_financeiro.cod_filial = ordemcompra.cod_filial
                              and parametro_financeiro.cod_empresa = ordemcompra.cod_empresa
                              and parametro_financeiro.cod_grupoempresa = ordemcompra.cod_grupoempresa_dest)
                   end indicefinanceiro
                 , resultadocotacaoitem.cod_material
                 , case when (select parametrosmaterial.visualiza_codigopadrao
                              from material.parametrosmaterial parametrosmaterial
                              where parametrosmaterial.cod_filial = ordemcompra.cod_filial
                              and parametrosmaterial.cod_empresa = ordemcompra.cod_empresa
                              and parametrosmaterial.cod_grupoempresa = ordemcompra.cod_grupoempresa_dest) = 'N'
                        then to_char(resultadocotacaoitem.cod_material)
                        else to_char(nvl(parametro_item.cod_padrao_item, resultadocotacaoitem.cod_material))
                   end cod_material_relatorio
                 , resultadocotacaoitem.preco, resultadocotacaoitem.sequencia, resultadocotacaoitem.aliquota_icms
                 , resultadocotacaoitem.observacao obs_rci
                 , resultadocotacaoitem.nr_solicitacao
                 , resultadocotacaoitem.perc_ipi, resultadocotacaoitem.Base_Icms_St, resultadocotacaoitem.Aliquota_Icms_St
                 , resultadocotacaoitem.descontoporunidade, resultadocotacaoitem.descontoporcentagem
                 , ((resultadocotacaoitem.vrdesc_rateio_ind / cotacaoitem.qtde_aprovada) * itensordemcompra.quantidade_solic) vrdesc_rateio_ind
                 , ((resultadocotacaoitem.descontototal / cotacaoitem.qtde_aprovada) * itensordemcompra.quantidade_solic) descontototal
                 , resultadocotacaoitem.prazoentrega, resultadocotacaoitem.dataentrega
                 , case when (select count(*) from material.itensordemcompraentrega
                              where itensordemcompraentrega.cod_material = itensordemcompra.cod_material
                              and itensordemcompraentrega.nr_solicitacao = itensordemcompra.nr_solicitacao
                              and itensordemcompraentrega.nroc = itensordemcompra.nroc) > 1 then ''
                        else decode(nvl(resultadocotacaoitem.prazoentrega, 0), 0,
                                    to_char(resultadocotacaoitem.dataentrega, 'DD/MM/YYYY'),
                                    resultadocotacaoitem.prazoentrega||' '||resultadocotacaoitem.tipoprazoentrega)
                   end entrega
                 , resultadocotacaoitem.precoparaxqtde, resultadocotacaoitem.preco_indice
                 , resultadocotacao.valor_indice_acordado, resultadocotacao.data_indicefinanceiro
                 , itensordemcompra.quantidade_solic, cotacaoitem.quantidade, cotacaoitem.qtde_aprovada
                 , pessoa.nome
                 , pessoa.endereco||decode(pessoa.numero_endereco,null,null,' - '||pessoa.numero_endereco) endereco
                 , pessoa.complemento_endereco, pessoa.bairro, pessoa.cep, cidade.cod_cidade, cidade.estado
                 , ltrim(cidade.descricao) || '/' || cidade.estado nome_cidade
                 , fornecedor.cod_pessoa, fornecedor.nomevendedor
                 , nvl(resultadocotacao.cod_tipocobranca,fornecedor.cod_tipocobranca) cod_tipocobranca
                 , decode(fornecedor.cgc, '', fornecedor.cpf, fornecedor.cgc) cgc_cpf
                 , planopagamento.descricaoplano, planopagamento.informarmanualmente
                 , tipofrete.descricaotipofrete, tipofrete.cod_tipofrete
                 , pessoa_fun.nome nome_comprador, nvl(pessoa_fun.email,pessoa_fun.email_alternativo) email_comprador
                 , resultadocotacaoitem_finaliz.cod_fornecedor_frete
                 , pessoatelefone_empresa.telefone, pessoatelefone_empresa.ramal
                 , ordemcompra.dataoc database, aprovacaoparacompra.dataaprovacao
                 , rh.fn_nomefuncionario(aprovacaoparacompra.cod_grupoempresa, aprovacaoparacompra.cod_funcionario) nomeaprovador
                 , resultadocotacaoitem.valor_icms_st
                 , vw_parcelas.documento, vw_parcelas.datavcto, vw_parcelas.valorparcela
            from faturamento.parametro_item parametro_item
               , geral.pessoatelefone_empresa
               , rh.pessoa pessoa_fun
               , rh.funcionario
               , rh.fisica
               , rh.cidade
               , rh.pessoa
               , material.fornecedor
               , material.planopagamento
               , material.tipofrete
               , material.cotacao
               , material.cotacaoitem
               , material.aprovacaoparacompra
               , material.resultadocotacao
               , material.resultadocotacaoitem_finaliz
               , material.resultadocotacaoitem resultadocotacaoitem
               , material.itensordemcompra itensordemcompra
               , material.ordemcompra
               , vw_parcelas vw_parcelas
            where ordemcompra.dataoc between '01011900' and '01012050'
            and   ordemcompra.nroc = vw_parcelas.nroc
            and   parametro_item.cod_material (+) = resultadocotacaoitem.cod_material
            and   pessoatelefone_empresa.cod_pessoa (+) = fisica.cod_pessoa
            and   pessoa_fun.cod_pessoa = fisica.cod_pessoa
            and   fisica.cpf = funcionario.cpf
            and   funcionario.cod_grupoempresa = cotacao.cod_grupoempresa
            and   funcionario.cod_funcionario = cotacao.cod_funcionario
            and   cidade.cod_cidade = pessoa.cod_cidade
            and   pessoa.cod_pessoa = fornecedor.cod_pessoa
            and   fornecedor.cod_fornecedor = ordemcompra.cod_fornecedor
            and   planopagamento.cod_plano = ordemcompra.cod_plano
            and   tipofrete.cod_tipofrete (+) = resultadocotacao.cod_tipofrete
            and   cotacaoitem.nr_cotacao = ordemcompra.nr_cotacao
            and   cotacaoitem.nr_solicitacao = itensordemcompra.nr_solicitacao
            and   cotacaoitem.cod_material = itensordemcompra.cod_material
            and   cotacao.nr_cotacao = ordemcompra.nr_cotacao
            and   aprovacaoparacompra.nr_cotacao = resultadocotacaoitem.nr_cotacao
            and   aprovacaoparacompra.cod_plano = resultadocotacaoitem.cod_plano
            and   aprovacaoparacompra.cod_material = resultadocotacaoitem.cod_material
            and   aprovacaoparacompra.nr_solicitacao = resultadocotacaoitem.nr_solicitacao
            and   resultadocotacao.nr_cotacao = ordemcompra.nr_cotacao
            and   resultadocotacao.cod_plano = ordemcompra.cod_plano
            and   resultadocotacaoitem_finaliz.nr_cotacao (+) = resultadocotacaoitem.nr_cotacao
            and   resultadocotacaoitem_finaliz.cod_plano (+) = resultadocotacaoitem.cod_plano
            and   resultadocotacaoitem_finaliz.cod_material (+) = resultadocotacaoitem.cod_material
            and   resultadocotacaoitem_finaliz.nr_solicitacao (+) = resultadocotacaoitem.nr_solicitacao
            and   resultadocotacaoitem.nr_solicitacao = itensordemcompra.nr_solicitacao
            and   resultadocotacaoitem.cod_material = itensordemcompra.cod_material
            and   resultadocotacaoitem.cod_plano = ordemcompra.cod_plano
            and   resultadocotacaoitem.nr_cotacao = ordemcompra.nr_cotacao
            and   itensordemcompra.nroc = ordemcompra.nroc
            and   ORDEMCOMPRA.Data_Envio is not null
        ) tmp1
        """;

    private static final String VW_PARCELAS_NOTAFISCAL = """
        with vw_parcelas as (
            select parcelascontaspagar.cod_grupoempresa, parcelascontaspagar.cod_tipocontaspagar
                 , parcelascontaspagar.documento, parcelascontaspagar.parcela
                 , notafiscal.sequencia_nf, notafiscal.nrnf, notafiscal.serie, itensentrada.dataentrada_seq
                 , ordemcompra.nroc, parcelascontaspagar.datavcto, parcelascontaspagar.valorparcela
            from material.ordemcompra ordemcompra
               , material.itensentrada itensentrada
               , material.notafiscal notafiscal
               , financeiro.parcelascontaspagar parcelascontaspagar
            where ordemcompra.dataoc between '01011900' and '01012050'
            and   ordemcompra.nroc = itensentrada.nroc
            and   itensentrada.dataentrada_seq between '01011900' and '01012050'
            and   itensentrada.serie = notafiscal.serie
            and   itensentrada.nrnf = notafiscal.nrnf
            and   itensentrada.sequencia_nf = notafiscal.sequencia_nf
            and   notafiscal.cod_documento = parcelascontaspagar.documento
            and   parcelascontaspagar.datapgto is null
            and   parcelascontaspagar.origem = 10
            and   parcelascontaspagar.datavcto between to_date(?, 'YYYY-MM-DD') and to_date(?, 'YYYY-MM-DD')
            and   parcelascontaspagar.cod_filial = 1
            and   parcelascontaspagar.cod_empresa = 1
            and   parcelascontaspagar.cod_grupoempresa = 1
            group by parcelascontaspagar.cod_grupoempresa, parcelascontaspagar.cod_tipocontaspagar
                 , parcelascontaspagar.documento, parcelascontaspagar.parcela
                 , notafiscal.sequencia_nf, notafiscal.nrnf, notafiscal.serie, itensentrada.dataentrada_seq
                 , ordemcompra.nroc, parcelascontaspagar.datavcto, parcelascontaspagar.valorparcela
        )
        """;

    private static final String VW_PARCELAS_DIRETO = """
        with vw_parcelas as (
            select parcelascontaspagar.cod_grupoempresa, parcelascontaspagar.cod_tipocontaspagar
                 , parcelascontaspagar.documento, parcelascontaspagar.parcela
                 , ordemcompra.nroc, parcelascontaspagar.datavcto, parcelascontaspagar.valorparcela
            from material.ordemcompra ordemcompra
               , financeiro.parcelascontaspagar parcelascontaspagar
            where ordemcompra.dataoc between '01011900' and '01012050'
            and   parcelascontaspagar.datapgto is null
            and   ordemcompra.nroc = parcelascontaspagar.documento
            and   parcelascontaspagar.cod_tipocontaspagar = 262
            and   parcelascontaspagar.datavcto between to_date(?, 'YYYY-MM-DD') and to_date(?, 'YYYY-MM-DD')
            and   parcelascontaspagar.cod_filial = 1
            and   parcelascontaspagar.cod_empresa = 1
            and   parcelascontaspagar.cod_grupoempresa = 1
            group by parcelascontaspagar.cod_grupoempresa, parcelascontaspagar.cod_tipocontaspagar
                 , parcelascontaspagar.documento, parcelascontaspagar.parcela
                 , ordemcompra.nroc, parcelascontaspagar.datavcto, parcelascontaspagar.valorparcela
        )
        """;

    private static final String SQL = """
        select * from (
        select %s
        from ( %s %s ) %s
        union all
        select %s
        from ( %s %s ) %s
        ) where rownum <= %d
        """.formatted(
                COLUNAS_FINAIS, VW_PARCELAS_NOTAFISCAL, TMP1_CORPO, GROUP_BY_FINAL,
                COLUNAS_FINAIS, VW_PARCELAS_DIRETO, TMP1_CORPO, GROUP_BY_FINAL,
                MAX_TOTAL);

    /**
     * @param dataIniVcto obrigatório, yyyy-MM-dd — início do período de
     *                    vencimento da parcela vinculada à ordem de compra
     * @param dataFimVcto obrigatório, yyyy-MM-dd — fim do período
     */
    public List<Map<String, Object>> buscar(String dataIniVcto, String dataFimVcto) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            ps.setString(1, dataIniVcto);
            ps.setString(2, dataFimVcto);
            ps.setString(3, dataIniVcto);
            ps.setString(4, dataFimVcto);

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar ordens de compra: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de ordem de compra: " + e.getMessage(), e);
        }
    }
}
