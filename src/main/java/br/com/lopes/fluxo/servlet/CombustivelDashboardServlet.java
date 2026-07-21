package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AgroCombustivelDAO;
import br.com.lopes.fluxo.util.DataParamUtil;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API do dashboard de consumo de combustível (combustivel-dashboard.html) —
 * front-end web autenticado por sessão (AuthFilter), diferente da rota
 * /api/agricola/combustivel, que é a ferramenta do Dr. Alfredo via chave de
 * API. Ambas usam a mesma consulta base (AgroCombustivelDAO).
 *
 * GET /api/combustivel-dashboard?dataIni=yyyy-MM-dd&dataFim=yyyy-MM-dd
 *        [&codEquipamento=N] [&codTipoCliente=N] [&combustivel=trecho]
 *        [&codFazenda=N]
 *
 * A agregação é feita aqui (e não no Oracle) porque o dashboard precisa de
 * vários cortes do mesmo resultado (KPIs, série diária, por combustível,
 * por equipamento, por fazenda, opções de filtro) — uma única execução da
 * consulta pesada alimenta todos.
 */
@WebServlet("/api/combustivel-dashboard")
public class CombustivelDashboardServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(CombustivelDashboardServlet.class.getName());

    /** Máximo de linhas de detalhe devolvidas para a tabela/export da tela. */
    private static final int MAX_DETALHE = 2000;
    private static final int MAX_RANKING = 15;

    private final Gson gson = new Gson();
    private final AgroCombustivelDAO dao = new AgroCombustivelDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            String dataIni = DataParamUtil.normalizar(req.getParameter("dataIni"));
            String dataFim = DataParamUtil.normalizar(req.getParameter("dataFim"));
            if (dataIni == null || dataFim == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Informe o período (dataIni e dataFim)\"}");
                return;
            }

            Integer codEquipamento = lerInteiro(req.getParameter("codEquipamento"));
            Integer codTipoCliente = lerInteiro(req.getParameter("codTipoCliente"));
            Integer codFazenda = lerInteiro(req.getParameter("codFazenda"));
            String combustivel = req.getParameter("combustivel");

            List<Map<String, Object>> linhas = dao.buscarDetalhadoDashboard(
                    dataIni, dataFim, codEquipamento, codTipoCliente, combustivel, codFazenda);

            Map<String, Object> resultado = new LinkedHashMap<>();
            resultado.put("ok", true);
            resultado.put("totalLinhas", linhas.size());
            resultado.put("kpis", montarKpis(linhas));
            resultado.put("porDia", agregar(linhas, l -> dia(l), true));
            resultado.put("porCombustivel", agregar(linhas, l -> rotulo(l, "cod_combustivel", "des_combustivel"), false));
            resultado.put("porEquipamento", limitar(agregar(linhas, l -> rotulo(l, "cod_equipamento", "des_equipamento"), false)));
            resultado.put("porFazenda", limitar(agregar(linhas, l -> rotulo(l, "cod_fazenda", "des_fazenda"), false)));
            resultado.put("opcoes", montarOpcoes(linhas));
            resultado.put("truncadoDetalhe", linhas.size() > MAX_DETALHE);
            resultado.put("detalhe", montarDetalhe(linhas));

            out.print(gson.toJson(resultado));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no dashboard de combustível", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    // ── KPIs ────────────────────────────────────────────────────────────

    private Map<String, Object> montarKpis(List<Map<String, Object>> linhas) {
        double litros = 0, valor = 0;
        java.util.Set<Object> equipamentos = new java.util.HashSet<>();
        for (Map<String, Object> l : linhas) {
            litros += num(l.get("qtde_litros"));
            valor += num(l.get("valor_total"));
            Object eq = l.get("cod_equipamento");
            if (eq != null) equipamentos.add(eq);
        }
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalLitros", arred(litros));
        kpis.put("valorTotal", arred(valor));
        kpis.put("qtdeAbastecimentos", linhas.size());
        kpis.put("precoMedio", litros == 0 ? 0 : arred(valor / litros));
        kpis.put("qtdeEquipamentos", equipamentos.size());
        return kpis;
    }

    // ── Agregações genéricas (litros, valor, qtde por chave) ────────────

    private interface Chave { String de(Map<String, Object> linha); }

    private List<Map<String, Object>> agregar(List<Map<String, Object>> linhas, Chave chave, boolean ordenarPorChave) {
        Map<String, double[]> mapa = ordenarPorChave ? new TreeMap<>() : new LinkedHashMap<>();
        for (Map<String, Object> l : linhas) {
            String k = chave.de(l);
            if (k == null) k = "Não informado";
            double[] acc = mapa.computeIfAbsent(k, x -> new double[3]);
            acc[0] += num(l.get("qtde_litros"));
            acc[1] += num(l.get("valor_total"));
            acc[2] += 1;
        }
        List<Map<String, Object>> lista = new ArrayList<>();
        mapa.forEach((k, acc) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chave", k);
            item.put("litros", arred(acc[0]));
            item.put("valor", arred(acc[1]));
            item.put("qtde", (int) acc[2]);
            lista.add(item);
        });
        if (!ordenarPorChave) {
            lista.sort(Comparator.comparingDouble((Map<String, Object> m) -> (Double) m.get("litros")).reversed());
        }
        return lista;
    }

    private List<Map<String, Object>> limitar(List<Map<String, Object>> lista) {
        return lista.size() > MAX_RANKING ? new ArrayList<>(lista.subList(0, MAX_RANKING)) : lista;
    }

    /** Data do abastecimento como yyyy-MM-dd (a coluna vem como LocalDateTime.toString()). */
    private static String dia(Map<String, Object> l) {
        Object d = l.get("data");
        if (d == null) return null;
        String s = String.valueOf(d);
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    private static String rotulo(Map<String, Object> l, String colCod, String colDesc) {
        Object desc = l.get(colDesc);
        if (desc != null && !String.valueOf(desc).isBlank()) return String.valueOf(desc);
        Object cod = l.get(colCod);
        return cod == null ? null : String.valueOf(cod);
    }

    // ── Opções de filtro (derivadas do resultado do período) ────────────

    private Map<String, Object> montarOpcoes(List<Map<String, Object>> linhas) {
        Map<String, Object> opcoes = new LinkedHashMap<>();
        opcoes.put("combustiveis", distintos(linhas, "cod_combustivel", "des_combustivel"));
        opcoes.put("fazendas", distintos(linhas, "cod_fazenda", "des_fazenda"));
        opcoes.put("tiposCliente", distintos(linhas, "cod_tipocliente", "tipocliente"));
        opcoes.put("equipamentos", distintos(linhas, "cod_equipamento", "des_equipamento"));
        return opcoes;
    }

    private List<Map<String, Object>> distintos(List<Map<String, Object>> linhas, String colCod, String colDesc) {
        Map<String, Map<String, Object>> mapa = new TreeMap<>();
        for (Map<String, Object> l : linhas) {
            Object cod = l.get(colCod);
            Object desc = l.get(colDesc);
            if (cod == null) continue;
            String descricao = desc == null || String.valueOf(desc).isBlank()
                    ? String.valueOf(cod) : String.valueOf(desc);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cod", cod);
            item.put("descricao", descricao);
            mapa.put(descricao + "|" + cod, item);
        }
        return new ArrayList<>(mapa.values());
    }

    // ── Detalhe para tabela/export ──────────────────────────────────────

    private static final String[] COLUNAS_DETALHE = {
            "data", "hora_ini", "prefixo", "des_equipamento", "des_combustivel",
            "qtde_litros", "valor_unitario", "valor_total", "des_fazenda",
            "tipocliente", "des_frentista", "des_funcionario", "desc_negocio",
            "des_almoxarifado", "km_ho", "kmhs_rodados"
    };

    private List<Map<String, Object>> montarDetalhe(List<Map<String, Object>> linhas) {
        // Mais recentes primeiro (a consulta vem ordenada por combustível/data).
        List<Map<String, Object>> ordenadas = new ArrayList<>(linhas);
        ordenadas.sort(Comparator.comparing(
                (Map<String, Object> l) -> String.valueOf(l.get("datahora"))).reversed());

        int limite = Math.min(ordenadas.size(), MAX_DETALHE);
        List<Map<String, Object>> detalhe = new ArrayList<>(limite);
        for (int i = 0; i < limite; i++) {
            Map<String, Object> l = ordenadas.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            for (String col : COLUNAS_DETALHE) {
                item.put(col, l.get(col));
            }
            detalhe.add(item);
        }
        return detalhe;
    }

    // ── Utilitários ─────────────────────────────────────────────────────

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static double arred(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static Integer lerInteiro(String v) {
        if (v == null || v.isBlank() || !v.trim().matches("\\d+")) return null;
        return Integer.valueOf(v.trim());
    }
}
