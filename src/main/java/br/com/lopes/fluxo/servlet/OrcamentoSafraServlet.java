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
 * {@link OrcamentoComprasDAO} (a de orcamento-compras.html), chamada duas
 * vezes — pelo PERÍODO escolhido (safra OU entressafra, sempre 6 meses) e
 * pelo MESMO período um ano antes (safra 2026 compara com safra 2025;
 * entressafra 2026 compara com entressafra 2025 — nunca safra com
 * entressafra). Das duas chamadas saem tanto orçado quanto realizado, porque
 * um período passado já tem os dois — só o período CORRENTE, no ano em
 * curso, costuma ter o realizado incompleto ou zerado.
 *
 * GET /api/orcamento-safra[?ini=202609&fim=202702]
 *   -> { ok, periodoAtual:{ini,fim,rotulo}, periodoAnterior:{ini,fim,rotulo},
 *        meses:[...], temNegocio,
 *        dados:[[grupo,empenho,negocio,mesIdx,orcadoAtual,realizadoAnterior,tipoOC,realizadoAtual], ...] }
 *
 * ini/fim ausentes = calculado a partir de hoje, mesma regra de
 * {@link OrcamentoComprasServlet#periodo}: março a agosto é a entressafra
 * corrente; setembro a fevereiro é a safra corrente.
 */
@WebServlet("/api/orcamento-safra")
public class OrcamentoSafraServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(OrcamentoSafraServlet.class.getName());
    private static final Gson GSON = new Gson();
    private final OrcamentoComprasDAO dao = new OrcamentoComprasDAO();

    private static final String[] NOMES_MES = {
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    };
    private static final String[] ABREV_MES = { "Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez" };

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try {
            int[] p = OrcamentoComprasServlet.periodo(req);
            int atualIni = p[0], atualFim = p[1];
            int anteriorIni = atualIni - 100, anteriorFim = atualFim - 100;

            List<Map<String, Object>> atual = dao.buscar(atualIni, atualFim, null);
            List<Map<String, Object>> anterior = dao.buscar(anteriorIni, anteriorFim, null);

            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.add("periodoAtual", periodoJson(atualIni, atualFim));
            r.add("periodoAnterior", periodoJson(anteriorIni, anteriorFim));
            r.addProperty("temNegocio", !dao.colunaDeNegocio().isEmpty());

            JsonArray meses = new JsonArray();
            for (int i = 0; i < 6; i++) {
                int anomes = somar(atualIni, i);
                JsonObject m = new JsonObject();
                m.addProperty("m", ABREV_MES[anomes % 100 - 1]);
                m.addProperty("l", NOMES_MES[anomes % 100 - 1]);
                m.addProperty("orc", rotuloMes(atualIni, i));
                m.addProperty("rea", rotuloMes(anteriorIni, i));
                meses.add(m);
            }
            r.add("meses", meses);
            r.add("dados", montarDados(atual, anterior, atualIni, anteriorIni));

            resp.getWriter().print(GSON.toJson(r));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no painel de orçamento por safra", e);
            resp.setStatus(500);
            String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            resp.getWriter().print("{\"ok\":false,\"erro\":" + GSON.toJson(msg) + "}");
        }
    }

    /** {ini, fim, rotulo} — "Safra 2026/27" (começa em setembro) ou "EntreSafra 2026" (começa em março). */
    private static JsonObject periodoJson(int ini, int fim) {
        int ano = ini / 100, mesIni = ini % 100;
        JsonObject o = new JsonObject();
        o.addProperty("ini", ini);
        o.addProperty("fim", fim);
        if (mesIni == 9) {
            o.addProperty("rotulo", "Safra " + ano + "/" + String.format("%02d", (ano + 1) % 100));
            o.addProperty("chave", "S" + ano);
        } else {
            o.addProperty("rotulo", "EntreSafra " + ano);
            o.addProperty("chave", "E" + ano);
        }
        return o;
    }

    /** 202609 -> "set/26". */
    private static String rotuloMes(int anomesIni, int idx) {
        int anomes = somar(anomesIni, idx);
        int mes = anomes % 100, ano = anomes / 100;
        return ABREV_MES[mes - 1].toLowerCase() + "/" + String.format("%02d", ano % 100);
    }

    private static int somar(int anomes, int meses) {
        int ano = anomes / 100, mes = anomes % 100;
        mes += meses;
        while (mes > 12) { mes -= 12; ano++; }
        return ano * 100 + mes;
    }

    /** Quantos meses após anomesBase o anomes está — 0..5 dentro do período de 6 meses, -1 se fora. */
    private static int indiceMes(int anomes, int anomesBase) {
        int anoA = anomes / 100, mesA = anomes % 100;
        int anoB = anomesBase / 100, mesB = anomesBase % 100;
        int dif = (anoA - anoB) * 12 + (mesA - mesB);
        return (dif >= 0 && dif < 6) ? dif : -1;
    }

    private JsonArray montarDados(List<Map<String, Object>> atual, List<Map<String, Object>> anterior,
                                   int atualIni, int anteriorIni) {
        Map<String, double[]> oAtual = new LinkedHashMap<>();
        Map<String, double[]> rAtual = new LinkedHashMap<>();
        Map<String, double[]> rAnterior = new LinkedHashMap<>();
        Map<String, String> tipo = new LinkedHashMap<>();
        Map<String, String[]> partes = new LinkedHashMap<>();

        for (Map<String, Object> l : atual) {
            int idx = indiceMes(inteiro(l.get("anomes")), atualIni);
            if (idx < 0) continue;
            String chave = chave(l);
            oAtual.computeIfAbsent(chave, k -> new double[6])[idx] += numero(l.get("orcado"));
            rAtual.computeIfAbsent(chave, k -> new double[6])[idx] += numero(l.get("realizado"));
            tipo.putIfAbsent(chave, texto(l.get("tipo_oc"), "OC"));
            partes.putIfAbsent(chave, new String[]{ texto(l.get("grupo"), ""), texto(l.get("empenho"), ""), texto(l.get("negocio"), "") });
        }
        for (Map<String, Object> l : anterior) {
            int idx = indiceMes(inteiro(l.get("anomes")), anteriorIni);
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
            double[] rAtu = rAtual.getOrDefault(chave, new double[6]);
            double[] rAnt = rAnterior.getOrDefault(chave, new double[6]);
            String tp = tipo.getOrDefault(chave, "OC");
            for (int i = 0; i < 6; i++) {
                if (o[i] == 0 && rAnt[i] == 0 && rAtu[i] == 0) continue;
                JsonArray linha = new JsonArray();
                linha.add(p[0]); linha.add(p[1]); linha.add(p[2]); linha.add(i);
                linha.add(arredondar(o[i])); linha.add(arredondar(rAnt[i])); linha.add(tp); linha.add(arredondar(rAtu[i]));
                dados.add(linha);
            }
        }
        return dados;
    }

    private static String chave(Map<String, Object> l) {
        return texto(l.get("grupo"), "") + "" + texto(l.get("empenho"), "") + "" + texto(l.get("negocio"), "");
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
