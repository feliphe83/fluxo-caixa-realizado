package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.FaturamentoVendasDAO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET /api/faturamento-vendas?dataIni=yyyy-MM-dd&dataFim=yyyy-MM-dd
 *
 * O DAO devolve uma linha por ITEM de nota. Aqui essas linhas viram o que o
 * painel mostra: total faturado, quebra por produto (o corte principal), mês
 * a mês por produto, e os cortes por cliente, estado e destino.
 *
 * O cuidado que decide se os números prestam: as colunas de NOTA
 * (valor_total_nota, icms, ipi, desconto, outras despesas, icms_st) vêm
 * repetidas em cada item da mesma nota. Somá-las linha a linha multiplicaria
 * o imposto pelo número de itens — uma nota com seis produtos apareceria com
 * seis vezes o ICMS que tem. Por isso elas são somadas UMA VEZ por nota,
 * enquanto quantidade e valor do item somam normalmente.
 */
@WebServlet("/api/faturamento-vendas")
public class FaturamentoVendasServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(FaturamentoVendasServlet.class.getName());
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Gson GSON = new Gson();

    /** Quantos produtos viram série própria no gráfico; o resto vira "Outros". */
    private static final int MAX_SERIES_PRODUTO = 6;
    /** Teto das listas de cliente/estado/destino. */
    private static final int MAX_LISTA = 12;

    private final FaturamentoVendasDAO dao = new FaturamentoVendasDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        LocalDate ini = parseData(req.getParameter("dataIni"));
        LocalDate fim = parseData(req.getParameter("dataFim"));
        if (ini == null || fim == null) {
            resp.setStatus(400);
            escrever(resp, "{\"ok\":false,\"erro\":\"Parâmetros dataIni e dataFim são obrigatórios (yyyy-MM-dd)\"}");
            return;
        }
        if (fim.isBefore(ini)) {
            resp.setStatus(400);
            escrever(resp, "{\"ok\":false,\"erro\":\"Data fim anterior à data início\"}");
            return;
        }

        try {
            escrever(resp, GSON.toJson(montar(dao.buscar(ini.format(ISO), fim.format(ISO)), ini, fim)));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro no painel de faturamento", e);
            resp.setStatus(500);
            String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            escrever(resp, "{\"ok\":false,\"erro\":\"" + msg.replace("\"", "'").replace("\n", " ") + "\"}");
        }
    }

    private void escrever(HttpServletResponse resp, String corpo) throws IOException {
        resp.getWriter().print(corpo);
        resp.getWriter().flush();
    }

    // ── Montagem ──────────────────────────────────────────────────────────

    static JsonObject montar(List<Map<String, Object>> linhas, LocalDate ini, LocalDate fim) {
        Map<String, Acum> porProduto  = new HashMap<>();
        Map<String, Acum> porCliente  = new HashMap<>();
        Map<String, Acum> porEstado   = new HashMap<>();
        Map<String, Acum> porDestino  = new HashMap<>();
        Map<String, Acum> porRotina   = new HashMap<>();
        // TreeMap: a chave é AAAA-MM, então os meses já saem em ordem.
        Map<String, Map<String, BigDecimal>> meses = new TreeMap<>();

        // Uma entrada por nota — é aqui que os valores de nota são contados
        // uma vez só, em vez de uma vez por item.
        Map<String, Nota> notas = new LinkedHashMap<>();

        BigDecimal valorItens = BigDecimal.ZERO;
        BigDecimal quantidade = BigDecimal.ZERO;

        for (Map<String, Object> l : linhas) {
            BigDecimal vItem = dec(l.get("valor_total_item"));
            BigDecimal qtd   = dec(l.get("quantidade"));
            String produto   = txt(l.get("produto"), "Sem produto");
            String mes       = mesDe(l.get("dataemissao"));

            valorItens = valorItens.add(vItem);
            quantidade = quantidade.add(qtd);

            porProduto.computeIfAbsent(produto, k -> new Acum()).somar(qtd, vItem, txt(l.get("descricaounidade"), ""));
            porCliente.computeIfAbsent(txt(l.get("nome"), "Sem cliente"), k -> new Acum()).somar(qtd, vItem, "");
            porEstado .computeIfAbsent(txt(l.get("estado"), "—"),         k -> new Acum()).somar(qtd, vItem, "");
            porDestino.computeIfAbsent(txt(l.get("destino"), "—"),        k -> new Acum()).somar(qtd, vItem, "");
            porRotina .computeIfAbsent(txt(l.get("rotina"), "—"),         k -> new Acum()).somar(qtd, vItem, "");

            if (mes != null) {
                meses.computeIfAbsent(mes, k -> new HashMap<>())
                     .merge(produto, vItem, BigDecimal::add);
            }

            // Chave da nota: número + emissão. Só o número se repete entre
            // séries e anos; a data separa sem precisar do id interno, que a
            // consulta não devolve.
            String chaveNota = txt(l.get("nr_nf"), "?") + "|" + txt(l.get("dataemissao"), "?");
            notas.computeIfAbsent(chaveNota, k -> new Nota(l));
        }

        BigDecimal valorNotas = BigDecimal.ZERO, icms = BigDecimal.ZERO, ipi = BigDecimal.ZERO,
                   desconto = BigDecimal.ZERO, outras = BigDecimal.ZERO, icmsSt = BigDecimal.ZERO;
        for (Nota n : notas.values()) {
            valorNotas = valorNotas.add(n.total);
            icms       = icms.add(n.icms);
            ipi        = ipi.add(n.ipi);
            desconto   = desconto.add(n.desconto);
            outras     = outras.add(n.outras);
            icmsSt     = icmsSt.add(n.icmsSt);
        }

        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("dataIni", ini.format(ISO));
        r.addProperty("dataFim", fim.format(ISO));
        r.addProperty("atualizadoEm", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        r.addProperty("valorItens", valorItens);
        r.addProperty("quantidade", quantidade);
        r.addProperty("itens", linhas.size());
        r.addProperty("notas", notas.size());
        r.addProperty("valorNotas", valorNotas);
        r.addProperty("icms", icms);
        r.addProperty("ipi", ipi);
        r.addProperty("desconto", desconto);
        r.addProperty("outrasDesp", outras);
        r.addProperty("icmsSt", icmsSt);

        List<String> ordemProdutos = ordenados(porProduto);
        r.add("porProduto", lista(porProduto, ordemProdutos, valorItens, Integer.MAX_VALUE, true));
        r.add("porCliente", lista(porCliente, ordenados(porCliente), valorItens, MAX_LISTA, false));
        r.add("porEstado",  lista(porEstado,  ordenados(porEstado),  valorItens, MAX_LISTA, false));
        r.add("porDestino", lista(porDestino, ordenados(porDestino), valorItens, MAX_LISTA, false));
        r.add("porRotina",  lista(porRotina,  ordenados(porRotina),  valorItens, MAX_LISTA, false));

        // Séries do gráfico mensal: os maiores produtos, e o resto somado em
        // "Outros" — oito séries empilhadas não se distinguem no olho.
        List<String> series = new ArrayList<>(ordemProdutos.subList(0, Math.min(MAX_SERIES_PRODUTO, ordemProdutos.size())));
        boolean temOutros = ordemProdutos.size() > series.size();

        JsonArray serieMeses = new JsonArray();
        for (Map.Entry<String, Map<String, BigDecimal>> e : meses.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("mes", e.getKey());
            o.addProperty("label", rotuloMes(e.getKey()));

            BigDecimal totalMes = BigDecimal.ZERO, outrosMes = BigDecimal.ZERO;
            JsonObject valores = new JsonObject();
            for (Map.Entry<String, BigDecimal> pv : e.getValue().entrySet()) {
                totalMes = totalMes.add(pv.getValue());
                if (series.contains(pv.getKey())) valores.addProperty(pv.getKey(), pv.getValue());
                else outrosMes = outrosMes.add(pv.getValue());
            }
            if (temOutros) valores.addProperty("Outros", outrosMes);
            o.addProperty("total", totalMes);
            o.add("produtos", valores);
            serieMeses.add(o);
        }
        r.add("meses", serieMeses);

        JsonArray nomesSerie = new JsonArray();
        series.forEach(nomesSerie::add);
        if (temOutros) nomesSerie.add("Outros");
        r.add("seriesProduto", nomesSerie);

        return r;
    }

    /** Acumulador de quantidade e valor por dimensão. */
    private static final class Acum {
        BigDecimal qtd = BigDecimal.ZERO;
        BigDecimal valor = BigDecimal.ZERO;
        String unidade = "";
        void somar(BigDecimal q, BigDecimal v, String un) {
            qtd = qtd.add(q);
            valor = valor.add(v);
            // Guarda a primeira unidade vista. Produto medido em duas
            // unidades diferentes é caso para olhar, não para inventar
            // conversão — por isso não tento somar unidades distintas.
            if (unidade.isEmpty() && un != null && !un.isBlank()) unidade = un.trim();
        }
    }

    /** Valores que pertencem à NOTA, não ao item. */
    private static final class Nota {
        final BigDecimal total, icms, ipi, desconto, outras, icmsSt;
        Nota(Map<String, Object> l) {
            total    = dec(l.get("valor_total_nota"));
            icms     = dec(l.get("valor_icms"));
            ipi      = dec(l.get("valor_ipi"));
            desconto = dec(l.get("valordesconto"));
            outras   = dec(l.get("valoroutrasdesp"));
            icmsSt   = dec(l.get("valor_icms_st"));
        }
    }

    private static List<String> ordenados(Map<String, Acum> mapa) {
        List<String> chaves = new ArrayList<>(mapa.keySet());
        chaves.sort(Comparator.comparing((String k) -> mapa.get(k).valor).reversed());
        return chaves;
    }

    private static JsonArray lista(Map<String, Acum> mapa, List<String> ordem,
                                   BigDecimal total, int limite, boolean comUnidade) {
        JsonArray arr = new JsonArray();
        int n = 0;
        for (String chave : ordem) {
            if (n++ >= limite) break;
            Acum a = mapa.get(chave);
            JsonObject o = new JsonObject();
            o.addProperty("nome", chave);
            o.addProperty("quantidade", a.qtd);
            o.addProperty("valor", a.valor);
            o.addProperty("precoMedio", a.qtd.signum() == 0 ? BigDecimal.ZERO
                    : a.valor.divide(a.qtd, 4, RoundingMode.HALF_UP));
            o.addProperty("pct", total.signum() == 0 ? BigDecimal.ZERO
                    : a.valor.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP));
            if (comUnidade) o.addProperty("unidade", a.unidade);
            arr.add(o);
        }
        return arr;
    }

    // ── Apoio ─────────────────────────────────────────────────────────────

    /** "2026-03-14..." -> "2026-03". Aceita a data já convertida em texto ISO. */
    static String mesDe(Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        return s.length() >= 7 && s.charAt(4) == '-' ? s.substring(0, 7) : null;
    }

    static String rotuloMes(String aaaaMm) {
        String[] nomes = { "jan","fev","mar","abr","mai","jun","jul","ago","set","out","nov","dez" };
        if (aaaaMm == null || !aaaaMm.matches("\\d{4}-\\d{2}")) return aaaaMm == null ? "" : aaaaMm;
        int m = Integer.parseInt(aaaaMm.substring(5, 7));
        if (m < 1 || m > 12) return aaaaMm;
        return nomes[m - 1] + "/" + aaaaMm.substring(2, 4);
    }

    private static String txt(Object v, String padrao) {
        if (v == null) return padrao;
        String s = String.valueOf(v).trim();
        // A perna da cana preenche os campos de cliente com o texto 'null'.
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return padrao;
        return s;
    }

    static BigDecimal dec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private LocalDate parseData(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim(), ISO); }
        catch (DateTimeParseException e) { return null; }
    }
}
