package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.DeParaTipoServicoCache;
import br.com.lopes.fluxo.util.OracleConnectionUtil;
import com.google.gson.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.logging.*;

/**
 * GET /api/controle-servicos?fazendas=N,N,N&dataIni=YYYY-MM-DD&dataFim=YYYY-MM-DD
 *
 * Parâmetro "fazendas" é OPCIONAL — se vazio/ausente, retorna todas as
 * fazendas com lançamento no período.
 *
 * Versão atualizada: bloco "TRANSPORTE DE PESSOAL" usa
 * it.cod_fazenda_destino como cod_fazenda_origem (alias), e a condição
 * "o.geracusto = 'S'" foi removida (comentada) — conforme query fornecida.
 */
@WebServlet("/api/controle-servicos")
public class ServicosFornecedorServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ServicosFornecedorServlet.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private final Gson gson = new Gson();

    private static String buildSQL(String ini, String fim, String filtroFazendasIn) {
        String dIni = "'" + converterParaDDMMYYYY(ini) + "'";
        String dFim = "'" + converterParaDDMMYYYY(fim) + "'";

        return
        "SELECT '1' EMPRESA, tipo, origem, cod_equipamento cod_funcionario, " +
        "rh.fn_nomefuncionario(1,cod_equipamento) nome, cod_fazenda_origem, cod_codservico, desc_servico, " +
        "(select f.descricao from agricola.fazenda f where f.cod_fazenda = cod_fazenda_origem) descrica, " +
        "desc_fornecedor, " +
        "(select max(f.cod_fornecedor) from material.fornecedor f, rh.pessoa p " +
        "where p.cod_pessoa = f.cod_pessoa and upper(trim(p.nome)) = upper(trim(desc_fornecedor))) cod_fornecedor, " +
        "sum(qtd_apontada), data_movimento, sum(valor_total), MAX(unidade) unidade, MAX(descobjeto) descobjeto " +
        "FROM ( " +
        "select 'MÃO DE OBRA' tipo, 'TRANSPORTE DE PESSOAL' origem, a.dt_apontamento data_movimento, " +
        "te.cod_tipoequipamento, te.descricaotipoequipamento, it.cod_equipamento, e.descricao desc_equipamento, " +
        "it.cod_fazenda_destino cod_fazenda_origem, agricola.fn_busca_nomeproprietario(it.cod_fazenda_destino) desc_fornecedor, " +
        "it.cod_operacaoagricola cod_codservico, o.descricao desc_servico, it.cod_unidade unidade, " +
        "a.numerocontrato, a.parcela, sum(it.quantidade) qtd_apontada, sum(it.valor_total) valor_total, " +
        "null descobjeto " +
        "from automotivo.apontamentoterceiro a, automotivo.itens_apontamentoterceiro it, " +
        "automotivo.historico_tipoequipamento he, automotivo.tipoequipamento te, automotivo.equipamento e, " +
        "rh.operacaoagricola o, rh.pessoa p, material.fornecedor fo " +
        "where a.cod_grupoempresa = 1 and a.cod_empresa = 1 and a.cod_filial = 1 " +
        "and a.ano_apontamento = it.ano_apontamento and a.numero_apontamento = it.numero_apontamento " +
        "and fo.cod_fornecedor = a.cod_fornecedor and p.cod_pessoa = fo.cod_pessoa " +
        "and e.cod_grupoempresa = a.cod_grupoempresa and e.cod_equipamento = it.cod_equipamento " +
        "and te.cod_tipoequipamento = he.cod_tipoequipamento " +
        "and rh.intersecao(a.dt_apontamento, a.dt_apontamento, he.data_inicio, he.data_fim) = 'TRUE' " +
        "and he.cod_grupoempresa = a.cod_grupoempresa and he.cod_equipamento = it.cod_equipamento " +
        "and o.cod_operacaoagricola = it.cod_operacaoagricola and o.ativo = 'S' " +
        "group by a.dt_apontamento, te.cod_tipoequipamento, te.descricaotipoequipamento, it.cod_equipamento, " +
        "e.descricao, it.cod_fazenda_destino, p.nome, it.cod_operacaoagricola, o.descricao, it.cod_unidade, " +
        "a.numerocontrato, a.parcela " +

        "UNION " +
        "select 'FATURAMENTO' tipo, 'CORTE DE CANA ' origem, a.data_apontamento data_movimento, " +
        "null cod_tipoequipamento, null descricaotipoequipamento, a.cod_funcionario cod_equipamento, null desc_equipamento, " +
        "a.cod_fazenda, agricola.fn_busca_nomeproprietario(a.cod_fazenda), RR.COD_TIPOSERVICO cod_codservico, " +
        "rr.descricao desc_servico, null unidade, null numerocontrato, null parcela, " +
        "sum(a.qtde_apontada)/1000 qtd_apontada, sum(a.valortotal) valor_total, null descobjeto " +
        "from rh.apontamento a, rh.tiposervico rr where a.cod_itemtabelapreco = rr.cod_tiposervico " +
        "AND RR.COD_TIPOSERVICO IN (5558,5554,5532,5526,5555,5531,5553) " +
        "group by a.data_apontamento, a.cod_fazenda, a.cod_funcionario, RR.COD_TIPOSERVICO, rr.descricao " +

        "UNION " +
        "select 'MÃO DE OBRA' tipo, 'LIDERAÇÃO RURAL ' origem, a.data_apontamento data_movimento, " +
        "null cod_tipoequipamento, null descricaotipoequipamento, a.cod_funcionario cod_equipamento, null desc_equipamento, " +
        "a.cod_fazenda, agricola.fn_busca_nomeproprietario(a.cod_fazenda), RR.COD_TIPOSERVICO cod_codservico, " +
        "rr.descricao desc_servico, null unidade, null numerocontrato, null parcela, " +
        "sum(a.qtde_apontada) qtd_apontada, sum(a.valortotal) valor_total, 'Administração/Controle Agrícola' descobjeto " +
        "from rh.apontamento a, rh.tiposervico rr where a.cod_itemtabelapreco = rr.cod_tiposervico " +
        "AND RR.COD_TIPOSERVICO NOT IN (5558,5554,5532,5526,5555,5531,5553) " +
        "group by a.data_apontamento, a.cod_fazenda, a.cod_funcionario, RR.COD_TIPOSERVICO, rr.descricao " +

        "UNION " +
        "select 'MÃO DE OBRA' tipo, 'ENCARGOS SOCIAIS ' origem, a.data_apontamento data_movimento, " +
        "null cod_tipoequipamento, null descricaotipoequipamento, null cod_equipamento, null desc_equipamento, " +
        "a.cod_fazenda, agricola.fn_busca_nomeproprietario(a.cod_fazenda), RR.COD_TIPOSERVICO cod_codservico, " +
        "rr.descricao desc_servico, null unidade, null numerocontrato, null parcela, " +
        "0 qtd_apontada, sum(a.valortotal)*0.70 valor_total, null descobjeto " +
        "from rh.apontamento a, rh.tiposervico rr where a.cod_itemtabelapreco = rr.cod_tiposervico " +
        "group by a.data_apontamento, a.cod_fazenda, RR.COD_TIPOSERVICO, rr.descricao " +

        "UNION " +
        "select 'MÃO DE OBRA' tipo, 'ADMINISTRAÇÃO ' origem, a.data_apontamento data_movimento, " +
        "null cod_tipoequipamento, null descricaotipoequipamento, a.cod_funcionario cod_equipamento, null desc_equipamento, " +
        "a.cod_fazenda, agricola.fn_busca_nomeproprietario(a.cod_fazenda), RR.COD_TIPOSERVICO cod_codservico, " +
        "rr.descricao desc_servico, null unidade, null numerocontrato, null parcela, " +
        "0 qtd_apontada, sum(a.valortotal)*0.065 valor_total, null descobjeto " +
        "from rh.apontamento a, rh.tiposervico rr where a.cod_fazenda = 26632 " +
        "and a.cod_itemtabelapreco = rr.cod_tiposervico " +
        "group by a.data_apontamento, a.cod_fazenda, RR.COD_TIPOSERVICO, a.cod_funcionario, rr.descricao " +

        "union " +
        "select 'AUTOMOTIVO' tipo, tarefa origem, dt_apontamento, null cod_tipoequipamento, " +
        "null descricaoequipamento, cod_equipamento, null desc_equipamento, Cod_Fazenda, " +
        "agricola.fn_busca_nomeproprietario(cod_fazenda), null cod_servico, tarefa, null unidade, " +
        "null numerocontrato, null parcela, sum(tot_hs) total_horas, (sum(tot_km) * max(valor)) valor1, " +
        "max(DescObjetoCusto) descobjeto " +
        "from ( " +
        "Select Apontamento.Ano_Apontamento, Apontamento.Numero_Apontamento, Apontamento.Dt_Apontamento, " +
        "to_char(to_date(apontamento.dt_apontamento,'dd/mm/yyyy'),'mm/yyyy') mes_ano, " +
        "to_number(to_char(to_date(apontamento.dt_apontamento,'dd/mm/rrrr'),'rrrrmm')) mes_ano_numero, " +
        "Apontamento.Cod_Funcionario, Itens_Apontamento.Cod_Fazenda, Itens_Apontamento.Cod_Cidade, " +
        "Itens_Apontamento.Cod_Talhao, Itens_Apontamento.cod_implemento, itens_apontamento.cod_equipamento, " +
        "Itens_Apontamento.Cod_OperacaoAgricola, VW_PESSOA.Nome, Fazenda.Descricao Fazenda, " +
        "Cidade.Descricao Fazenda_original, " +
        "objetocusto.negocio || '.' || objetocusto.processo || '.' || objetocusto.subprocesso || objetocusto.descricao DescObjetoCusto, " +
        "OperacaoAgricola.Descricao Tarefa, Equipamento.Descricao, Itens_Apontamento.KM_INICIAL, Itens_Apontamento.KM_FINAL, " +
        "itens_apontamento.km_final - itens_apontamento.km_inicial tot_km, Itens_Apontamento.HORAINICIAL, Itens_Apontamento.HORAFINAL, " +
        "case when geral.hora_centesimal(itens_apontamento.horafinal) >= geral.hora_centesimal(itens_apontamento.horainicial) " +
        "then geral.hora_centesimal(itens_apontamento.horafinal) - geral.hora_centesimal(itens_apontamento.horainicial) " +
        "when geral.hora_centesimal(itens_apontamento.horafinal) < geral.hora_centesimal(itens_apontamento.horainicial) " +
        "then (geral.hora_centesimal(itens_apontamento.horafinal) + 24) - geral.hora_centesimal(itens_apontamento.horainicial) " +
        "end tot_hs, itens_apontamento.cod_fazenda_origem, fazenda_origem.descricao desc_fazenda_origem, at.valor valor " +
        "From agricola.fazenda fazenda_origem, Automotivo.Apontamento Apontamento, Automotivo.Itens_Apontamento Itens_Apontamento, " +
        "Automotivo.equipamento, Rh.VW_PESSOA, rh.objetocusto, Rh.OperacaoAgricola, RH.VW_PESSOA_FUNCIONARIO, RH.CIDADE, " +
        "Agricola.fazenda, cpd.atividade_equipamento at " +
        "Where fazenda_origem.cod_fazenda (+)= itens_apontamento.cod_fazenda_origem " +
        "and VW_PESSOA.Cod_Pessoa = VW_PESSOA_FUNCIONARIO.Cod_Pessoa " +
        "and Itens_Apontamento.Cod_Operacaoagricola = at.atividade and Itens_Apontamento.Cod_Equipamento = at.equipamento " +
        "and VW_PESSOA_FUNCIONARIO.Cod_Funcionario = Apontamento.Cod_Funcionario " +
        "and VW_PESSOA_FUNCIONARIO.Cod_GrupoEmpresa = Apontamento.Cod_GrupoEmpresa " +
        "and fazenda.Cod_Fazenda (+)= Itens_Apontamento.Cod_fazenda and cidade.Cod_cidade (+)= Itens_Apontamento.Cod_cidade " +
        "and objetocusto.cod_objetocusto (+)= itens_apontamento.cod_objetocusto " +
        "and OperacaoAgricola.Cod_OperacaoAgricola (+)= Itens_Apontamento.Cod_Operacaoagricola " +
        "and Equipamento.Cod_Equipamento = itens_apontamento.cod_equipamento " +
        "and Equipamento.Cod_GrupoEmpresa = Apontamento.Cod_Grupoempresa " +
        "and Itens_Apontamento.Ano_Apontamento (+)= Apontamento.Ano_Apontamento " +
        "and Itens_Apontamento.Numero_Apontamento (+)= Apontamento.Numero_Apontamento " +
        "and Itens_Apontamento.dt_apontamento (+)= Apontamento.dt_apontamento " +
        "and Apontamento.Cod_GrupoEmpresa = 1 and Apontamento.Cod_Empresa = 1 and Apontamento.Cod_Filial = 1 " +
        "and Apontamento.Dt_Apontamento Between " + dIni + " and " + dFim + " " +
        "order by cod_operacaoagricola, cod_equipamento " +
        ") group by Cod_OperacaoAgricola, tarefa, Cod_Fazenda, dt_apontamento, cod_equipamento, tarefa " +

        "UNION " +
        "select 'FATURAMENTO' tipo, 'EPI, FERRAMENTAS E GELO ' origem, a.data_apontamento data_movimento, " +
        "null cod_tipoequipamento, null descricaotipoequipamento, null cod_equipamento, null desc_equipamento, " +
        "a.cod_fazenda, agricola.fn_busca_nomeproprietario(a.cod_fazenda), RR.COD_TIPOSERVICO cod_codservico, " +
        "rr.descricao desc_servico, null unidade, null numerocontrato, null parcela, " +
        "0 qtd_apontada, sum(a.qtde_apontada)*2.40/1000 valor_total, null descobjeto " +
        "from rh.apontamento a, rh.tiposervico rr where a.cod_itemtabelapreco = rr.cod_tiposervico " +
        "AND RR.COD_TIPOSERVICO IN (5558,5554,5532,5526,5555,5531,5553) " +
        "group by a.data_apontamento, a.cod_fazenda, RR.COD_TIPOSERVICO, rr.descricao " +

        ") t " +
        "WHERE " + filtroFazendasIn +
        "t.data_movimento BETWEEN " + dIni + " AND " + dFim + " " +
        "AND t.desc_fornecedor <> 'USINA SANTA CLOTILDE S/A' " +
        "GROUP BY tipo, origem, cod_fazenda_origem, desc_fornecedor, cod_codservico, cod_equipamento, desc_servico, data_movimento " +
        "ORDER BY 6, 2 DESC, 3, 4";
    }

    /** Converte YYYY-MM-DD para DD/MM/YYYY (formato usado nas comparações Oracle desta query) */
    private static String converterParaDDMMYYYY(String isoDate) {
        String[] partes = isoDate.split("-");
        return partes[2] + "/" + partes[1] + "/" + partes[0];
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        LocalDate dataIni = parseDate(req.getParameter("dataIni"));
        LocalDate dataFim = parseDate(req.getParameter("dataFim"));
        String fazendasParam = req.getParameter("fazendas");

        if (dataIni == null || dataFim == null) {
            resp.setStatus(400);
            out.print("{\"ok\":false,\"erro\":\"Parâmetros dataIni e dataFim são obrigatórios\"}");
            out.flush(); return;
        }

        // Filtro de fazenda(s) é OPCIONAL — se vazio, retorna todas
        String filtroFazendasIn = "";
        if (fazendasParam != null && !fazendasParam.isBlank()) {
            String[] partes = fazendasParam.split(",");
            StringBuilder sb = new StringBuilder();
            for (String p : partes) {
                String limpo = p.trim();
                if (limpo.isEmpty()) continue;
                if (!limpo.matches("\\d+")) {
                    resp.setStatus(400);
                    out.print("{\"ok\":false,\"erro\":\"Código de fazenda inválido: " + limpo.replace("\"","'") + "\"}");
                    out.flush(); return;
                }
                if (sb.length() > 0) sb.append(",");
                sb.append(limpo);
            }
            if (sb.length() > 0) {
                filtroFazendasIn = "t.cod_fazenda_origem IN (" + sb + ") AND ";
            }
        }

        String sql = buildSQL(dataIni.toString(), dataFim.toString(), filtroFazendasIn);

        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            JsonArray arr = new JsonArray();
            while (rs.next()) {
                String tipo = rs.getString(2);
                String origem = rs.getString(3);
                String codServico = rs.getString(7);
                String descObjeto = rs.getString(16);

                // De-para Tipo de Serviço -> Objeto de Custo (MySQL, mantido
                // pelo admin): enriquece descObjeto quando o Oracle não traz
                // essa classificação, usando cod_tiposervico. Só se aplica a
                // blocos de MÃO DE OBRA cujo codServico É de fato
                // cod_tiposervico — não ao bloco TRANSPORTE DE PESSOAL, que
                // usa cod_operacaoagricola (domínio de código diferente), nem
                // a ENCARGOS SOCIAIS, que não entra na quebra por Objeto.
                if ("MÃO DE OBRA".equals(tipo) && origem != null
                        && !origem.trim().equals("TRANSPORTE DE PESSOAL")
                        && !origem.trim().equals("ENCARGOS SOCIAIS")) {
                    DeParaTipoServicoCache.Registro reg = DeParaTipoServicoCache.buscar(codServico);
                    if (reg != null && reg.objetoCusto != null && !reg.objetoCusto.isBlank()) {
                        descObjeto = reg.objetoCusto;
                    }
                }

                JsonObject o = new JsonObject();
                o.addProperty("empresa",          rs.getString(1));
                o.addProperty("tipo",             tipo);
                o.addProperty("origem",           origem);
                o.addProperty("codFuncionario",   rs.getString(4));
                o.addProperty("nomeFuncionario",  rs.getString(5));
                o.addProperty("codFazendaOrigem", rs.getString(6));
                o.addProperty("codServico",       codServico);
                o.addProperty("descServico",      rs.getString(8));
                o.addProperty("descFazenda",      rs.getString(9));
                o.addProperty("descFornecedor",   rs.getString(10));
                o.addProperty("codFornecedor",    rs.getString(11));
                o.addProperty("qtdApontada",      rs.getBigDecimal(12));

                java.sql.Date dataMov = rs.getDate(13);
                o.addProperty("dataMovimento", dataMov != null ? dataMov.toString() : null);

                o.addProperty("valorTotal",       rs.getBigDecimal(14));
                o.addProperty("unidade",          rs.getString(15));
                o.addProperty("descObjeto",       descObjeto);
                arr.add(o);
            }

            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.add("data", arr);
            out.print(gson.toJson(result));

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro controle-servicos", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"","'").replace("\n"," ")
                    : e.getClass().getName();
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim(), FMT); }
        catch (DateTimeParseException e) { return null; }
    }
}
