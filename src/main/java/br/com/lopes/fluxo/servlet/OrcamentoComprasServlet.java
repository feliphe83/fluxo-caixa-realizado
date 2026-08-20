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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET /api/orcamento-compras?ini=202509&fim=202608[&negocio=...]
 * GET /api/orcamento-compras/sql          -> o SQL literal já com o período
 * GET /api/orcamento-compras/diagnostico  -> as colunas das tabelas envolvidas
 *
 * O período é sempre de SAFRA: setembro a agosto, o mesmo recorte do
 * faturamento. O exemplo que veio junto com a consulta (202509 a 202608) é
 * exatamente uma safra, e ter dois recortes diferentes de "ano" no mesmo
 * sistema é como duas telas passam a discordar sem ninguém achar o motivo.
 *
 * O FILTRO DE NEGÓCIO É APLICADO AQUI, e não no banco. A consulta vem
 * sempre inteira, por dois motivos: a lista de negócios do seletor sai dos
 * próprios dados do período (filtrando no banco, a lista encolheria a cada
 * escolha e não daria para voltar), e trocar de negócio deixa de custar uma
 * ida ao Oracle. São dezenas de empenhos por safra — não é volume que peça
 * filtro no banco.
 */
@WebServlet({"/api/orcamento-compras", "/api/orcamento-compras/*"})
public class OrcamentoComprasServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(OrcamentoComprasServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final OrcamentoComprasDAO dao = new OrcamentoComprasDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try {
            int[] periodo = periodo(req);
            String rota = req.getPathInfo() == null ? "" : req.getPathInfo();

            if ("/sql".equals(rota)) {
                JsonObject o = new JsonObject();
                o.addProperty("ok", true);
                o.addProperty("sql", dao.sql(periodo[0], periodo[1]));
                escrever(resp, GSON.toJson(o));
                return;
            }
            if ("/diagnostico".equals(rota)) {
                JsonObject o = new JsonObject();
                o.addProperty("ok", true);
                o.addProperty("colunaDeNegocio", dao.colunaDeNegocio());
                o.add("colunas", GSON.toJsonTree(dao.diagnostico()));
                escrever(resp, GSON.toJson(o));
                return;
            }

            escrever(resp, GSON.toJson(montar(periodo[0], periodo[1],
                    req.getParameter("negocio"), dao.buscar(periodo[0], periodo[1], null),
                    dao.colunaDeNegocio())));

        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro no orçamento de compras", e);
            resp.setStatus(500);
            String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            escrever(resp, "{\"ok\":false,\"erro\":\""
                    + msg.replace("\\", " ").replace("\"", "'").replace("\n", " ") + "\"}");
        }
    }

    /** ini/fim em AAAAMM; sem eles, a safra corrente (set a ago). */
    static int[] periodo(HttpServletRequest req) {
        Integer ini = anomes(req.getParameter("ini"));
        Integer fim = anomes(req.getParameter("fim"));
        if (ini == null || fim == null || ini > fim) {
            LocalDate h = LocalDate.now();
            int ano = h.getMonthValue() >= 9 ? h.getYear() : h.getYear() - 1;
            return new int[]{ ano * 100 + 9, (ano + 1) * 100 + 8 };
        }
        return new int[]{ ini, fim };
    }

    private static Integer anomes(String v) {
        if (v == null || !v.trim().matches("\\d{6}")) return null;
        int n = Integer.parseInt(v.trim()), mes = n % 100;
        return (mes >= 1 && mes <= 12) ? n : null;
    }

    private void escrever(HttpServletResponse resp, String corpo) throws IOException {
        resp.getWriter().print(corpo);
        resp.getWriter().flush();
    }

    // ── Montagem ──────────────────────────────────────────────────────────

    static JsonObject montar(int ini, int fim, String negocio,
                             List<Map<String, Object>> linhas, String colunaNegocio) {
        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("ini", ini);
        r.addProperty("fim", fim);
        r.addProperty("periodo", rotuloMes(ini) + " a " + rotuloMes(fim));
        r.addProperty("atualizadoEm",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        // Sem coluna de objeto de custo não há como saber o negócio; a tela
        // esconde o seletor em vez de mostrar um filtro que não filtra.
        r.addProperty("temNegocio", colunaNegocio != null && !colunaNegocio.isEmpty());

        // A lista do seletor sai de TUDO que veio, antes de filtrar.
        TreeSet<String> negocios = new TreeSet<>();
        for (Map<String, Object> l : linhas) {
            String n = texto(l.get("negocio"));
            if (!n.isEmpty()) negocios.add(n);
        }
        r.add("negocios", GSON.toJsonTree(negocios));

        boolean filtrar = negocio != null && !negocio.isBlank();
        Map<String, Grupo> grupos = new LinkedHashMap<>();
        BigDecimal totalO = BigDecimal.ZERO, totalR = BigDecimal.ZERO;

        for (Map<String, Object> l : linhas) {
            if (filtrar && !negocio.trim().equals(texto(l.get("negocio")))) continue;

            BigDecimal o = decimal(l.get("orcado"));
            BigDecimal rr = decimal(l.get("realizado"));
            // Empenho sem orçamento e sem realizado é ruído do plano de contas.
            if (o.signum() == 0 && rr.signum() == 0) continue;

            String codGrupo = texto(l.get("cod_grupoempenho"));
            String nomeGrupo = vazioVira(texto(l.get("grupo")), "Sem grupo");
            Grupo g = grupos.computeIfAbsent(codGrupo + "|" + nomeGrupo,
                    k -> new Grupo(codGrupo, nomeGrupo));
            g.orcado = g.orcado.add(o);
            g.realizado = g.realizado.add(rr);

            JsonObject e = new JsonObject();
            e.addProperty("cod", texto(l.get("cod_empenho")));
            e.addProperty("nome", vazioVira(texto(l.get("empenho")), "Sem descrição"));
            e.addProperty("negocio", texto(l.get("negocio")));
            e.addProperty("orcado", o);
            e.addProperty("realizado", rr);
            e.addProperty("diferenca", o.subtract(rr));
            e.addProperty("pct", pct(rr, o));
            g.empenhos.add(new Object[]{ e, o.add(rr) });

            totalO = totalO.add(o);
            totalR = totalR.add(rr);
        }

        // Grupo maior primeiro: é por onde se começa a olhar um orçamento.
        List<Grupo> ordenados = new ArrayList<>(grupos.values());
        ordenados.sort(Comparator.comparing((Grupo g) -> g.orcado.max(g.realizado)).reversed());

        JsonArray arr = new JsonArray();
        for (Grupo g : ordenados) {
            g.empenhos.sort(Comparator.comparing(
                    (Object[] x) -> (BigDecimal) x[1]).reversed());
            JsonArray emps = new JsonArray();
            for (Object[] x : g.empenhos) emps.add((JsonObject) x[0]);

            JsonObject o = new JsonObject();
            o.addProperty("cod", g.cod);
            o.addProperty("nome", g.nome);
            o.addProperty("orcado", g.orcado);
            o.addProperty("realizado", g.realizado);
            o.addProperty("diferenca", g.orcado.subtract(g.realizado));
            o.addProperty("pct", pct(g.realizado, g.orcado));
            o.addProperty("empenhos", emps.size());
            o.add("detalhe", emps);
            arr.add(o);
        }
        r.add("grupos", arr);

        JsonObject capa = new JsonObject();
        capa.addProperty("orcado", totalO);
        capa.addProperty("realizado", totalR);
        capa.addProperty("diferenca", totalO.subtract(totalR));
        capa.addProperty("pct", pct(totalR, totalO));
        capa.addProperty("empenhos", linhas.size());
        r.add("capa", capa);
        return r;
    }

    private static final class Grupo {
        final String cod, nome;
        BigDecimal orcado = BigDecimal.ZERO, realizado = BigDecimal.ZERO;
        final List<Object[]> empenhos = new ArrayList<>();
        Grupo(String cod, String nome) { this.cod = cod; this.nome = nome; }
    }

    /**
     * Quanto do orçado já foi realizado.
     *
     * Sem orçamento não existe percentual de realização — devolve null, e a
     * tela escreve "sem orçamento". Um "100%" ali seria pior do que nada:
     * gasto sem verba prevista viraria execução perfeita.
     */
    static BigDecimal pct(BigDecimal realizado, BigDecimal orcado) {
        if (orcado == null || orcado.signum() == 0) return null;
        return realizado.multiply(BigDecimal.valueOf(100))
                        .divide(orcado, 1, RoundingMode.HALF_UP);
    }

    /** 202509 -> "set/25". */
    static String rotuloMes(int anomes) {
        String[] nomes = { "jan","fev","mar","abr","mai","jun","jul","ago","set","out","nov","dez" };
        int m = anomes % 100;
        if (m < 1 || m > 12) return String.valueOf(anomes);
        return nomes[m - 1] + "/" + String.format("%02d", (anomes / 100) % 100);
    }

    private static String vazioVira(String v, String padrao) {
        return v == null || v.isEmpty() || "null".equalsIgnoreCase(v) ? padrao : v;
    }

    private static String texto(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
