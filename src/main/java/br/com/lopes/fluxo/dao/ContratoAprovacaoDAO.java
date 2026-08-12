package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contratos que ainda não passaram por nenhuma aprovação.
 *
 * A consulta é a que a área financeira usa, com quatro mudanças, todas em
 * trechos que estavam presos a um dia e a uma pessoa:
 *
 * 1. As duas datas fixas viraram TRUNC(SYSDATE) — a original tinha o dia em
 *    que foi escrita, e no dia seguinte já traria a vigência errada.
 * 2. A data de criação virou uma data de corte configurável (>=), para o
 *    alerta não varrer o contrato de dois anos atrás na primeira execução.
 *    Fica no agendamento em vez de fixa na consulta: mudá-la não pode exigir
 *    deploy.
 * 3. O 324 do fn_verificasupervisorde e do fn_funcionarioaprovaetapa virou
 *    bind: é o id_logon do ERP de quem vai receber, do mesmo jeito que o
 *    alerta de variação de preço já faz. Assim cada destinatário recebe o que
 *    ELE aprova, e não a lista de outra pessoa.
 * O filtro do autorizante ficou fixo em 19424, como na consulta original — o
 * mesmo código usado no alerta de parcelas.
 *
 * O 8258 continua fixo: é o código da rotina de aprovação de contrato, como o
 * 7871 é o da cotação em {@link VariacaoPrecoDAO}.
 */
public class ContratoAprovacaoDAO {

    private static final Logger LOG = Logger.getLogger(ContratoAprovacaoDAO.class.getName());

    /** Binds, nesta ordem: dataCriacao, idLogon, idLogon. */
    private static final String SQL = """
        select tmp.numerocontrato
             , tmp.datainicio
             , tmp.datatermino
             , tmp.desc_tipocontrato
             , tmp.cod_fornecedor
             , tmp.nome_fornecedor
             , tmp.descricaoresumida
             , tmp.valortotal
             , tmp.qtdeparcelas
             , tmp.fixovariavel
             , tmp.desc_empenho
             , tmp.desc_objetocusto
             , tmp.nome_autorizante
             , nvl(tmp.valortotal,0) + tmp.valor_total_ctr valor_total_ctr
             , (select t.descricao
                from   financeiro.tipooperacaocontrato t
                     , financeiro.contratovigencia v
                where  t.idtipooperacao     = v.idtipooperacao
                and    v.idcontratovigencia = (select max(v.idcontratovigencia)
                                               from   financeiro.contratovigencia v
                                               where  v.cod_grupoempresa = tmp.cod_grupoempresa
                                               and    v.numerocontrato   = tmp.numerocontrato
                                               and    rh.intersecao(tmp.datainicio, tmp.datatermino, v.datainicio, v.datatermino) = 'TRUE') ) vigencia
        from   (
            select historicocontrato.numerocontrato
                 , historicocontrato.cod_grupoempresa
                 , historicocontrato.datainicio
                 , historicocontrato.datatermino
                 , contrato.cod_empresa
                 , contrato.cod_filial
                 , contrato.cod_tipocontrato
                 , tipocontrato.descricao desc_tipocontrato
                 , contrato.cod_fornecedor
                 , (select pessoa.nome from rh.pessoa where pessoa.cod_pessoa = fornecedor.cod_pessoa) nome_fornecedor
                 , contrato.descricaoresumida
                 , contrato.cod_agente
                 , (select pessoa.nome from rh.pessoa where pessoa.cod_pessoa = agente.cod_pessoa) nome_agente
                 , contrato.exigenotafiscal
                 , contrato.cod_fazenda
                 , contrato.zona
                 , contrato.cod_talhao
                 , fazenda.descricao desc_fazenda
                 , contrato.pagarreceber
                 , contrato.finalizado
                 , contrato.gera_inss
                 , contrato.gera_iss
                 , contrato.gera_ir
                 , contrato.calculafolha
                 , contrato.acumulabase_irpj
                 , contrato.cod_equipamento
                 , contrato.credito_piscofins
                 , contrato.origem
                 , origem.descricao desc_origem
                 , contrato.data_criacao
                 , historicocontrato.cod_indicefinanceiro
                 , historicocontrato.diasavisovcto
                 , historicocontrato.valortotal
                 , historicocontrato.qtdeparcelas
                 , historicocontrato.vr_minimo_parcelas
                 , decode(historicocontrato.tipo_valor_min,'R','Reais','Índice') tipo_valor_min
                 , decode(historicocontrato.fixovariavel,'V','Variável','Fixo') fixovariavel
                 , historicocontrato.tipooperacao
                 , historicocontrato.funcaprovacao cod_autorizante
                 , rh.fn_nomefuncionario(historicocontrato.cod_grupoempresa, historicocontrato.funcaprovacao) nome_autorizante
                 , nvl(historicocontrato.cod_empenho,0) cod_empenho
                 , empenho.descricao desc_empenho
                 , nvl(historicocontrato.cod_objetocusto,0) cod_objetocusto
                 , objetocusto.descricao desc_objetocusto
                 , historicocontrato.contacontabil
                 , historicocontrato.cod_aplicacao
                 , aplicacao_contrato.descricao desc_aplicacao
                 , historicocontrato.cod_projeto
                 , projeto.descricao_projeto
                 , nvl((select sum(nvl(VALORMATERIAIS,0) + nvl(VALORMATERIASTERCEIRO,0))
                        from   financeiro.CONTRATOVIGENCIA
                        where  rh.intersecao(historicocontrato.datainicio, historicocontrato.datatermino, CONTRATOVIGENCIA.datainicio, CONTRATOVIGENCIA.datatermino) = 'TRUE'
                        and    CONTRATOVIGENCIA.cod_grupoempresa = historicocontrato.cod_grupoempresa
                        and    CONTRATOVIGENCIA.numerocontrato   = historicocontrato.numerocontrato
                       ),0) valor_total_ctr
            from  financeiro.origem
                , financeiro.tipocontrato
                , material.fornecedor agente
                , agricola.fazenda
                , material.fornecedor
                , custo.empenho
                , rh.objetocusto
                , custo.projeto
                , ( select aplicacao.cod_aplicacao
                         , aplicacao.descricao
                    from   material.aplicacao
                    where (aplicacao.cod_aplicacao in (select rh.valor_item(a.valor, rownum)
                                                       from   rh.constante a
                                                            , rh.constante b
                                                       where  a.nome = 'COD_APLICACAO_COMUNS'
                                                       and    rownum <= to_char(rh.total_itens(rh.c('COD_APLICACAO_COMUNS'))))
                    or     cod_aplicacao in (select rh.valor_item(a.valor, rownum)
                                             from   rh.constante a
                                                  , rh.constante b
                                             where  a.nome = 'COD_APLICACAO_ACUCAR'
                                             and    rownum <= to_char(rh.total_itens(rh.c('COD_APLICACAO_ACUCAR')))))
                  ) aplicacao_contrato
                , financeiro.parametroscontrato
                , financeiro.historicocontrato
                , financeiro.contrato
            -- Se o flag "Utilização Aprovação" da tela Parâmetros Gerais estiver
            -- marcado, busca pela data do histórico do parâmetro.
            where  RH.INTERSECAO(trunc(sysdate), trunc(sysdate), PARAMETROSCONTRATO.DATAINICIO, PARAMETROSCONTRATO.DATAFIM) = 'TRUE'
            AND    PARAMETROSCONTRATO.UTILIZA_APROVACAO      = 'S'
            AND    PARAMETROSCONTRATO.COD_FILIAL             = contrato.cod_filial
            AND    PARAMETROSCONTRATO.COD_EMPRESA            = contrato.cod_empresa
            AND    PARAMETROSCONTRATO.COD_GRUPOEMPRESA       = contrato.cod_grupoempresa
            and    origem.origem                         (+)= contrato.origem
            and    rh.intersecao(TRUNC(SYSDATE), TRUNC(SYSDATE), tipocontrato.datainicio(+), tipocontrato.datafim(+)) = 'TRUE'
            and    tipocontrato.cod_grupoempresa         (+)= contrato.cod_grupoempresa
            and    tipocontrato.cod_empresa              (+)= contrato.cod_empresa
            and    tipocontrato.cod_filial               (+)= contrato.cod_filial
            and    tipocontrato.cod_tipocontrato         (+)= contrato.cod_tipocontrato
            and    agente.cod_fornecedor                 (+)= contrato.cod_agente
            and    fazenda.cod_fazenda                   (+)= contrato.cod_fazenda
            and    fornecedor.cod_fornecedor             (+)= contrato.cod_fornecedor
            and    empenho.cod_empenho                   (+)= historicocontrato.cod_empenho
            and    objetocusto.cod_objetocusto           (+)= historicocontrato.cod_objetocusto
            and    projeto.cod_projeto                   (+)= historicocontrato.cod_projeto
            and    aplicacao_contrato.cod_aplicacao      (+)= historicocontrato.cod_aplicacao
            -- É isto que define "sem aprovação nenhuma".
            and    not exists (select 1
                               from   financeiro.contratoaprovacao
                               where  contratoaprovacao.cod_grupoempresa = historicocontrato.cod_grupoempresa
                               and    contratoaprovacao.numerocontrato   = historicocontrato.numerocontrato
                               and    contratoaprovacao.data_inicio      = historicocontrato.datainicio)
            and   (historicocontrato.datatermino is null or historicocontrato.datatermino >= TRUNC(SYSDATE))
            and    historicocontrato.cod_grupoempresa    = contrato.cod_grupoempresa
            and    historicocontrato.numerocontrato      = contrato.numerocontrato
            and    contrato.finalizado                   = 'F'   -- ainda em uso pela empresa
            and    contrato.cod_filial                   = 1
            and    contrato.cod_empresa                  = 1
            and    contrato.cod_grupoempresa             = 1
            and    contrato.data_criacao                >= ?
        ) tmp
        where  segurancanovo.fn_verificasupervisorde(tmp.cod_grupoempresa,
                                                     tmp.cod_empresa,
                                                     tmp.cod_filial,
                                                     ?,
                                                     tmp.cod_objetocusto,
                                                     tmp.cod_empenho,
                                                     0,
                                                     0,
                                                     8258) = 'T'
        and    tmp.cod_autorizante = 19424
        and    FINANCEIRO.fn_funcionarioaprovaetapa(tmp.cod_grupoempresa
                                                  , tmp.cod_empresa
                                                  , tmp.cod_filial
                                                  , tmp.numerocontrato
                                                  , ?
                                                  , 8258
                                                  , 'A'
                                                  , tmp.datainicio) = 'T'
        order by tmp.datainicio, tmp.numerocontrato
        """;

    /**
     * Contratos sem nenhuma aprovação que este usuário pode aprovar.
     *
     * @param idLogon       id_logon do ERP, de fc_usuario.id_logon_erp
     * @param dataCriacao   só considera contratos criados a partir desta data
     */
    public List<Map<String, Object>> buscarSemAprovacao(int idLogon, LocalDate dataCriacao) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            ps.setDate(1, Date.valueOf(dataCriacao));
            ps.setInt(2, idLogon);
            ps.setInt(3, idLogon);

            try (ResultSet rs = ps.executeQuery()) {
                return RowMapperUtil.toList(rs);
            }

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar contratos sem aprovação (idLogon=" + idLogon
                    + ", dataCriacao=" + dataCriacao + "): " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de contratos para aprovação: " + e.getMessage(), e);
        }
    }
}
