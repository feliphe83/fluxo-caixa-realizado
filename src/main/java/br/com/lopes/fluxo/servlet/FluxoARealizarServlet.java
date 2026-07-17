package br.com.lopes.fluxo.servlet;

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

@WebServlet("/api/fluxo-arealizar")
public class FluxoARealizarServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(FluxoARealizarServlet.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class,
                    (JsonSerializer<LocalDate>) (src, t, ctx) ->
                            new JsonPrimitive(src.format(FMT)))
            .serializeNulls()
            .create();

    private static String buildSQL(String ini, String fim) {
        String di = "TO_DATE('" + ini + "','YYYY-MM-DD')";
        String df = "TO_DATE('" + fim + "','YYYY-MM-DD')";
        return
        "SELECT " +
        "    q.conta_fluxo, q.desc_fluxo, q.cod_grupoempenho, q.desc_grupoempenho, " +
        "    q.cod_empenho, q.descricao_empenho, q.cod_fornecedor, q.nome, q.documento, " +
        "    q.cod_tipocontaspagar, q.desc_contas_pagar, q.dataentrada, q.datavcto, " +
        "    q.valor, q.provisao, q.usuario, q.data_criacao, q.datavcto_orig, q.parcela, " +
        "    q.cod_indicefinanceiro " +
        "FROM ( " +
        "    SELECT conta_fluxo, desc_fluxo, cod_grupoempenho, desc_grupoempenho, cod_empenho, " +
        "           Descricao_empenho, cod_fornecedor, nome, documento, cod_tipocontaspagar, " +
        "           desc_contas_pagar, dataentrada, datavcto, " +
        "           (vlrparcela - vlr_baixa) valor, " +
        "           provisao, usuario, data_criacao, datavcto_orig, parcela, cod_indicefinanceiro " +
        "    FROM ( " +
        "        SELECT " +
        "            (SELECT t.descricao FROM financeiro.tipocontaspagar t WHERE t.cod_tipocontaspagar = tabela.cod_tipocontaspagar AND t.datafim IS NULL AND t.cod_empresa = 1 AND t.cod_filial = 1) desc_contas_pagar, " +
        "            (SELECT e.cod_grupoempenho FROM custo.empenho e WHERE e.cod_empenho = tabela.cod_empenho) cod_grupoempenho, " +
        "            (SELECT gg.descricao FROM custo.empenho e, custo.grupoempenho gg WHERE e.cod_empenho = tabela.cod_empenho AND e.cod_grupoempenho = gg.cod_grupoempenho) desc_grupoempenho, " +
        "            (SELECT flc.cod_contafluxo FROM financeiro.fluxoempenho fl, financeiro.fluxoconta flc WHERE fl.cod_planofluxo = 16 AND fl.cod_empenho = tabela.cod_empenho AND fl.cod_planofluxo = flc.cod_planofluxo AND fl.cod_contafluxo = flc.cod_contafluxo) conta_fluxo, " +
        "            (SELECT flc.descricaoconta FROM financeiro.fluxoempenho fl, financeiro.fluxoconta flc WHERE fl.cod_planofluxo = 16 AND fl.cod_empenho = tabela.cod_empenho AND fl.cod_planofluxo = flc.cod_planofluxo AND fl.cod_contafluxo = flc.cod_contafluxo) desc_fluxo, " +
        "            tabela.numero_integracao, tabela.cod_tipocontaspagar, tabela.usuario, " +
        "            tabela.data_criacao, tabela.datavcto_orig, tabela.cod_grupoempresa, " +
        "            tabela.cod_empresa, tabela.cod_filial, tabela.documento, tabela.parcela, " +
        "            tabela.usuariopgto, tabela.cod_situacao, tabela.num_lancamentobancario, " +
        "            DECODE(tabela.datapgto, NULL, " +
        "                ((financeiro.busca_valoratualparcela(tabela.cod_grupoempresa, tabela.cod_tipocontaspagar, tabela.documento, tabela.parcela, tabela.cod_indicefinanceiro, DECODE(tabela.data_indicefinanceiro,null,nvl(NULL,tabela.datavcto),tabela.data_indicefinanceiro), tabela.datapgto, tabela.valorparcela, tabela.valorindexado, 'T') + " +
        "                  financeiro.consulta_jurosdescontoparcela(tabela.cod_grupoempresa, tabela.cod_tipocontaspagar, tabela.documento, tabela.parcela, DECODE(tabela.data_indicefinanceiro,null,nvl(NULL,tabela.datavcto),tabela.data_indicefinanceiro), 'A'))), " +
        "                financeiro.Busca_BaixaParcelaVr_Liquido(tabela.COD_GRUPOEMPRESA, tabela.COD_TIPOCONTASPAGAR, tabela.DOCUMENTO, tabela.PARCELA)) vlrparcela, " +
        "            financeiro.busca_baixaparcelavr_liquido(tabela.cod_grupoempresa, tabela.cod_tipocontaspagar, tabela.documento, tabela.parcela) vlr_baixa, " +
        "            tabela.datavcto, tabela.datapgto, tabela.cod_indicefinanceiro, tabela.provisao, " +
        "            geral.fn_obtem_nome(tabela.cod_fornecedor, tabela.cpf) nome, " +
        "            tabela.cod_fornecedor, tabela.cod_empenho, " +
        "            (SELECT empenho.descricao FROM custo.empenho WHERE empenho.cod_empenho = tabela.cod_empenho) Descricao_empenho, " +
        "            tabela.dataentrada " +
        "        FROM ( " +
        "            SELECT parcelascontaspagar.* FROM financeiro.parcelascontaspagar parcelascontaspagar " +
        "            WHERE parcelascontaspagar.pagarreceber = 'P' " +
        "              AND parcelascontaspagar.datavcto BETWEEN " + di + " AND " + df + " " +
        "              AND NVL(parcelascontaspagar.dataentradanf, parcelascontaspagar.dataentrada) BETWEEN TO_DATE('01011500', 'DDMMYYYY') AND TO_DATE('31122058', 'DDMMYYYY') " +
        "              AND parcelascontaspagar.parcelabaixada = 'S' " +
        "              AND parcelascontaspagar.cod_filial = 1 AND parcelascontaspagar.cod_empresa = 1 AND parcelascontaspagar.cod_grupoempresa = 1 " +
        "            UNION ALL " +
        "            SELECT pcp.* FROM ( " +
        "                SELECT parcelascontaspagar.cod_grupoempresa, parcelascontaspagar.cod_tipocontaspagar, parcelascontaspagar.documento, parcelascontaspagar.parcela " +
        "                FROM financeiro.situacaoparcelascontaspagar, financeiro.parcelascontaspagar " +
        "                WHERE parcelascontaspagar.pagarreceber = 'P' " +
        "                  AND parcelascontaspagar.cod_situacao <> 13 " +
        "                  AND parcelascontaspagar.datavcto BETWEEN " + di + " AND " + df + " " +
        "                  AND NVL(parcelascontaspagar.dataentradanf, parcelascontaspagar.dataentrada) BETWEEN TO_DATE('01011500', 'DDMMYYYY') AND TO_DATE('31122058', 'DDMMYYYY') " +
        "                  AND parcelascontaspagar.cod_situacao = situacaoparcelascontaspagar.cod_situacao " +
        "                  AND situacaoparcelascontaspagar.liberapagamento = 'N' " +
        "                  AND NVL(situacaoparcelascontaspagar.visualizar_parcela,'S') = 'S' " +
        "                  AND parcelascontaspagar.cod_filial = 1 AND parcelascontaspagar.cod_empresa = 1 AND parcelascontaspagar.cod_grupoempresa = 1 " +
        "                UNION " +
        "                SELECT parcelascontaspagar.cod_grupoempresa, parcelascontaspagar.cod_tipocontaspagar, parcelascontaspagar.documento, parcelascontaspagar.parcela " +
        "                FROM financeiro.situacaoparcelascontaspagar, financeiro.parcelascontaspagar " +
        "                WHERE parcelascontaspagar.pagarreceber = 'P' " +
        "                  AND parcelascontaspagar.cod_situacao = 13 " +
        "                  AND parcelascontaspagar.datavcto BETWEEN " + di + " AND " + df + " " +
        "                  AND NVL(parcelascontaspagar.dataentradanf, parcelascontaspagar.dataentrada) BETWEEN TO_DATE('01011500', 'DDMMYYYY') AND TO_DATE('31122058', 'DDMMYYYY') " +
        "                  AND parcelascontaspagar.cod_situacao = situacaoparcelascontaspagar.cod_situacao " +
        "                  AND situacaoparcelascontaspagar.liberapagamento = 'N' " +
        "                  AND NVL(situacaoparcelascontaspagar.visualizar_parcela,'S') = 'S' " +
        "                  AND parcelascontaspagar.cod_filial = 1 AND parcelascontaspagar.cod_empresa = 1 AND parcelascontaspagar.cod_grupoempresa = 1 " +
        "                UNION " +
        "                SELECT parcelascontaspagar.cod_grupoempresa, parcelascontaspagar.cod_tipocontaspagar, parcelascontaspagar.documento, parcelascontaspagar.parcela " +
        "                FROM financeiro.situacaoparcelascontaspagar, financeiro.parcelascontaspagar " +
        "                WHERE parcelascontaspagar.pagarreceber = 'P' " +
        "                  AND parcelascontaspagar.datavcto BETWEEN " + di + " AND " + df + " " +
        "                  AND NVL(parcelascontaspagar.dataentradanf, parcelascontaspagar.dataentrada) BETWEEN TO_DATE('01011500', 'DDMMYYYY') AND TO_DATE('31122058', 'DDMMYYYY') " +
        "                  AND parcelascontaspagar.cod_situacao = situacaoparcelascontaspagar.cod_situacao " +
        "                  AND situacaoparcelascontaspagar.liberapagamento = 'S' " +
        "                  AND NVL(situacaoparcelascontaspagar.visualizar_parcela,'S') = 'S' " +
        "                  AND parcelascontaspagar.datapgto IS NULL " +
        "                  AND parcelascontaspagar.cod_filial = 1 AND parcelascontaspagar.cod_empresa = 1 AND parcelascontaspagar.cod_grupoempresa = 1 " +
        "            ) aux " +
        "            INNER JOIN financeiro.parcelascontaspagar pcp " +
        "               ON pcp.parcela = aux.parcela " +
        "              AND pcp.documento = aux.documento " +
        "              AND pcp.cod_tipocontaspagar = aux.cod_tipocontaspagar " +
        "              AND pcp.cod_grupoempresa = aux.cod_grupoempresa " +
        "        ) tabela " +
        "        WHERE tabela.cod_tipocontaspagar NOT IN (817,789) " +
        "    ) inner_q " +
        ") q " +
        "ORDER BY q.datavcto, q.documento, q.parcela";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        LocalDate dataIni = parseDate(req.getParameter("dataIni"));
        LocalDate dataFim = parseDate(req.getParameter("dataFim"));

        if (dataIni == null || dataFim == null) {
            resp.setStatus(400);
            out.print("{\"ok\":false,\"erro\":\"Parâmetros dataIni e dataFim são obrigatórios\"}");
            out.flush(); return;
        }

        String sql = buildSQL(dataIni.toString(), dataFim.toString());

        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            JsonArray arr = new JsonArray();
            while (rs.next()) {
                JsonObject o = new JsonObject();
                o.addProperty("codContaFluxo",      rs.getString("conta_fluxo"));
                o.addProperty("descricaoConta",     rs.getString("desc_fluxo"));
                o.addProperty("codEmpenho",         rs.getString("cod_empenho"));
                o.addProperty("descEmpenho",        rs.getString("descricao_empenho"));
                o.addProperty("codFornecedor",      rs.getString("cod_fornecedor"));
                o.addProperty("nome",               rs.getString("nome"));
                o.addProperty("descricaoTipoConta", rs.getString("desc_contas_pagar"));
                o.addProperty("documento",          rs.getString("documento"));
                o.addProperty("parcela",            rs.getString("parcela"));
                o.addProperty("codTipoContasPagar", rs.getString("cod_tipocontaspagar"));
                o.addProperty("provisao",           rs.getString("provisao"));
                o.addProperty("usuario",            rs.getString("usuario"));
                o.addProperty("valor",              rs.getBigDecimal("valor"));
                o.addProperty("codIndiceFinanceiro",rs.getString("cod_indicefinanceiro"));

                String dv = rs.getString("datavcto");
                o.addProperty("dataVcto", dv != null && dv.length() >= 10 ? dv.substring(0,10) : dv);

                String dvo = rs.getString("datavcto_orig");
                o.addProperty("dataVctoOrig", dvo != null && dvo.length() >= 10 ? dvo.substring(0,10) : dvo);

                String de = rs.getString("dataentrada");
                o.addProperty("dataEntrada", de != null && de.length() >= 10 ? de.substring(0,10) : de);

                arr.add(o);
            }

            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.add("data", arr);
            out.print(gson.toJson(result));

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro fluxo-arealizar", e);
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
