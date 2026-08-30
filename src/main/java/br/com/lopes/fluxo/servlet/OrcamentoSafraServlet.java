package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.OrcamentoComprasDAO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dados do painel "Acompanhamento do Orçamento de Compras — Safra" (a versão
 * com layout de dashboard, drill-down grupo/área/empenho e gráficos —
 * orcamento-safra.html). Reaproveita a mesma consulta de
 * {@link OrcamentoComprasDAO} (a de orcamento-compras.html), só que chamada
 * DUAS vezes — uma pela safra corrente (orçado) e outra pela safra anterior
 * (realizado, como referência, já que a safra corrente ainda não aconteceu
 * na maior parte dos meses) — e o resultado é combinado em memória num
 * formato compacto: uma linha por (grupo, empenho, negócio, mês), com o
 * orçado da safra corrente e o realizado da safra anterior lado a lado.
 *
 * GET /api/orcamento-safra[?anoInicio=2026]
 *   -> { ok, anoInicio, meses:[...], dados:[[grupo,empenho,negocio,mesIdx,orcado,realizadoAnterior,tipoOC], ...] }
 *
 * anoInicio ausente = calculado a partir de hoje: setembro a dezembro abre a
 * safra do próprio ano; janeiro/fevereiro ainda são o fim da safra do ano
 * anterior; março a agosto (entressafra) mostra a PRÓXIMA safra, que começa
 * em setembro deste ano.
 */
@WebServlet("/api/orcamento-safra")
public class OrcamentoSafraServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(OrcamentoSafraServlet.class.getName());
    private static final Gson GSON = new Gson();
    private final OrcamentoComprasDAO dao = new OrcamentoComprasDAO();

    private static final String[] NOMES_MES = { "Setembro", "Outubro", "Novembro", "Dezembro", "Janeiro", "Fevereiro" };
    private static final String[] ABREV_MES = { "Set", "Out", "Nov", "Dez", "Jan", "Fev" };

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try {
            int anoInicio = anoInicio(req);
            int atualIni = anoInicio * 100 + 9, atualFim = (anoInicio + 1) * 100 + 2;
            int anteriorIni = (anoInicio - 1) * 100 + 9, anteriorFim = anoInicio * 100 + 2;

            List<Map<String, Object>> atual = dao.buscar(atualIni, atualFim, null);
            List<Map<String, Object>> anterior = dao.buscar(anteriorIni, anteriorFim, null);

            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.addProperty("anoInicio", anoInicio);
            r.addProperty("temNegocio", !dao.colunaDeNegocio().isEmpty());

            JsonArray meses = new JsonArray();
            for (int i = 0; i < 6; i++) {
                JsonObject m = new JsonObject();
                m.addProperty("m", ABREV_MES[i]);
                m.addProperty("l", NOMES_MES[i]);
                m.addProperty("orc", rotuloMes(atualIni, i));
                m.addProperty("rea", rotuloMes(anteriorIni, i));
                meses.add(m);
            }
            r.add("meses", meses);
            r.add("dados", montarDados(atual, anterior, anoInicio));

            resp.getWriter().print(GSON.toJson(r));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no painel de orçamento por safra", e);
            resp.setStatus(500);
            String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            resp.getWriter().print("{\"ok\":false,\"erro\":" + GSON.toJson(msg) + "}");
        }
    }

    private static int anoInicio(HttpServletRequest req) {
        String p = req.getParameter("anoInicio");
        if (p != null && p.matches("\\d{4}")) return Integer.parseInt(p);
        LocalDate h = LocalDate.now();
        int ano = h.getYear(), mes = h.getMonthValue();
        if (mes >= 9) return ano;
        if (mes <= 2) return ano - 1;
        return ano;   // entressafra (mar-ago): mostra a próxima safra, que abre em setembro deste ano
    }

    /** 202609 -> "set/26". */
    private static String rotuloMes(int anomesIni, int idx) {
        int ano = somar(anomesIni, idx) / 100;
        return ABREV_MES[idx].toLowerCase() + "/" + String.format("%02d", ano % 100);
    }

    private static int somar(int anomes, int meses) {
        int ano = anomes / 100, mes = anomes % 100;
        mes += meses;
        while (mes > 12) { mes -= 12; ano++; }
        return ano * 100 + mes;
    }

    /** anomes -> índice 0(set)..5(fev) dentro da safra que começa em anoInicio; -1 se fora do esperado. */
    private static int indiceMes(int anomes, int anoInicio) {
        int ano = anomes / 100, mes = anomes % 100;
        if (ano == anoInicio && mes >= 9) return mes - 9;
        if (ano == anoInicio + 1 && mes <= 2) return mes + 3;
        return -1;
    }

    private JsonArray montarDados(List<Map<String, Object>> atual, List<Map<String, Object>> anterior, int anoInicio) {
        Map<String, double[]> oAtual = new LinkedHashMap<>();
        Map<String, double[]> rAnterior = new LinkedHashMap<>();
        Map<String, String> tipo = new LinkedHashMap<>();
        Map<String, String[]> partes = new LinkedHashMap<>();

        for (Map<String, Object> l : atual) {
            int idx = indiceMes(inteiro(l.get("anomes")), anoInicio);
            if (idx < 0) continue;
            String chave = chave(l);
            oAtual.computeIfAbsent(chave, k -> new double[6])[idx] += numero(l.get("orcado"));
            tipo.putIfAbsent(chave, texto(l.get("tipo_oc"), "OC"));
            partes.putIfAbsent(chave, new String[]{ texto(l.get("grupo"), ""), texto(l.get("empenho"), ""), texto(l.get("negocio"), "") });
        }
        for (Map<String, Object> l : anterior) {
            int idx = indiceMes(inteiro(l.get("anomes")), anoInicio - 1);
            if (idx < 0) continue;
            String chave = chave(l);
            rAnterior.computeIfAbsent(chave, k -> new double[6])[idx] += numero(l.get("realizado"));
            tipo.putIfAbsent(chave, texto(l.get("tipo_oc"), "OC"));
            partes.putIfAbsent(chave, new String[]{ texto(l.get("grupo"), ""), texto(l.get("empenho"), ""), texto(l.get("negocio"), "") });
        }

        List<String> chaves = new ArrayList<>(oAtual.keySet());
        for (String k : rAnterior.keySet()) if (!oAtual.containsKey(k)) chaves.add(k);

        JsonArray dados = new JsonArray();
        for (String chave : chaves) {
            String[] p = partes.get(chave);
            double[] o = oAtual.getOrDefault(chave, new double[6]);
            double[] r = rAnterior.getOrDefault(chave, new double[6]);
            String tp = tipo.getOrDefault(chave, "OC");
            for (int i = 0; i < 6; i++) {
                if (o[i] == 0 && r[i] == 0) continue;
                JsonArray linha = new JsonArray();
                linha.add(p[0]); linha.add(p[1]); linha.add(p[2]); linha.add(i);
                linha.add(arredondar(o[i])); linha.add(arredondar(r[i])); linha.add(tp);
                dados.add(linha);
            }
        }
        return dados;
    }

    private static String chave(Map<String, Object> l) {
        return texto(l.get("grupo"), "") + "" + texto(l.get("empenho"), "") + "" + texto(l.get("negocio"), "");
    }

    private static double arredondar(double v) { return Math.round(v * 100.0) / 100.0; }

    private static int inteiro(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return v == null ? 0 : Integer.parseInt(v.toString().trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static double numero(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return v == null ? 0 : Double.parseDouble(v.toString().trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static String texto(Object v, String padrao) {
        if (v == null) return padrao;
        String s = v.toString().trim();
        return s.isEmpty() ? padrao : s;
    }
}
