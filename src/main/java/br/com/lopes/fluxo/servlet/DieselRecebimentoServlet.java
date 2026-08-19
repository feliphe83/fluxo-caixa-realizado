package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.DieselRecebimentoDAO;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET /api/diesel-recebimento             -> painel dos últimos 6 meses vs. ano anterior
 * GET /api/diesel-recebimento/sql         -> o SQL literal
 * GET /api/diesel-recebimento/diagnostico -> o que material.itensentrada tem de verdade
 *
 * A JANELA. Seis meses terminando HOJE, e o comparativo é o mesmo intervalo
 * de dias um ano atrás — não o mês fechado. O mês corrente está pela metade;
 * comparar 19 dias de agosto contra os 31 de agosto passado mostraria uma
 * queda que não existe. Cortando os dois lados no mesmo dia, a comparação é
 * de igual para igual.
 *
 * QUANTIDADE E VALOR. O DAO traz itensentrada.* inteiro porque o nome da
 * coluna de quantidade e o da coluna de valor não estão documentados neste
 * projeto e não há como executar nada no Oracle daqui para conferir. Quem
 * escolhe é {@link #primeiroNumero}, por uma lista de nomes candidatos, e o
 * que ele escolheu vai no JSON (bloco "diag") e aparece no rodapé da tela.
 * Se escolher errado, dá para ver — em vez de um número silenciosamente
 * errado, que é o pior desfecho possível num painel de parede.
 */
@WebServlet({"/api/diesel-recebimento", "/api/diesel-recebimento/*"})
public class DieselRecebimentoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(DieselRecebimentoServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final DieselRecebimentoDAO dao = new DieselRecebimentoDAO();

    /** Quantos meses a janela cobre, contando o corrente. */
    private static final int MESES = 6;

    /** Nomes possíveis da quantidade recebida, na ordem de preferência. */
    private static final String[] COLUNAS_QTDE = {
        "qtde_entrada", "qtdeentrada", "quantidade_entrada", "qtde_recebida",
        "qtde", "quantidade", "qtd", "qtde_item", "quantidade_item"
    };

    /** Nomes possíveis do valor total do item. */
    private static final String[] COLUNAS_VALOR = {
        "valor_total", "valortotal", "vlr_total", "vlrtotal",
        "valor_item", "valoritem", "valor"
    };

    /** Nomes possíveis do valor unitário — usado só se não houver total. */
    private static final String[] COLUNAS_UNITARIO = {
        "valor_unitario", "valorunitario", "vlr_unitario", "vlrunitario",
        "preco_unitario", "precounitario", "valor_unit", "preco"
    };

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        try {
            // Este painel foi escrito sem nunca poder executar nada neste
            // Oracle. Em vez de adivinhar o nome e o formato das colunas
            // mais uma vez, esta rota pergunta ao banco — e é ela que a tela
            // de erro manda abrir quando a consulta falha.
            if ("/diagnostico".equals(req.getPathInfo())) {
                JsonObject o = new JsonObject();
                o.addProperty("ok", true);
                o.add("colunasItensEntrada", GSON.toJsonTree(dao.colunas("MATERIAL", "ITENSENTRADA")));
                o.add("colunasNotaFiscal",   GSON.toJsonTree(dao.colunas("MATERIAL", "NOTAFISCAL")));
                o.add("amostraDataEntrada",  GSON.toJsonTree(dao.amostraData()));
                escrever(resp, GSON.toJson(o));
                return;
            }

            if ("/sql".equals(req.getPathInfo())) {
                JsonObject o = new JsonObject();
                o.addProperty("ok", true);
                o.addProperty("sql", dao.sql());
                escrever(resp, GSON.toJson(o));
                return;
            }

            LocalDate hoje = LocalDate.now();
            LocalDate fim  = hoje;
            LocalDate ini  = hoje.withDayOfMonth(1).minusMonths(MESES - 1L);

            List<Map<String, Object>> atual    = dao.buscar(ini, fim);
            List<Map<String, Object>> anterior = dao.buscar(ini.minusYears(1), fim.minusYears(1));

            JsonObject r = montar(ini, fim, atual, anterior);
            // De onde saiu a data — o painel mostra, porque foi justamente
            // aqui que ele já devolveu zero calado uma vez.
            r.getAsJsonObject("diag").addProperty("tipoDataEntrada", dao.tipoDataEntrada());
            escrever(resp, GSON.toJson(r));

        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro no painel de recebimento de diesel", e);
            resp.setStatus(500);
            String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            escrever(resp, "{\"ok\":false,\"erro\":\"" + msg.replace("\\", " ").replace("\"", "'").replace("\n", " ") + "\"}");
        }
    }

    private void escrever(HttpServletResponse resp, String corpo) throws IOException {
        resp.getWriter().print(corpo);
        resp.getWriter().flush();
    }

    // ── Montagem do painel ────────────────────────────────────────────────

    static JsonObject montar(LocalDate ini, LocalDate fim,
                             List<Map<String, Object>> atual,
                             List<Map<String, Object>> anterior) {

        Periodo pa = agregar(atual);
        Periodo pb = agregar(anterior);

        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("atualizadoEm", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        DateTimeFormatter br = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        r.addProperty("periodo",          ini.format(br) + " a " + fim.format(br));
        r.addProperty("periodoAnterior",  ini.minusYears(1).format(br) + " a " + fim.minusYears(1).format(br));
        // O mês corrente está pela metade dos dois lados; a tela diz isso.
        r.addProperty("cortadoEm", fim.format(br));

        r.add("atual",    resumo(pa));
        r.add("anterior", resumo(pb));
        r.add("variacao", variacao(pa, pb));

        // ── Série mês a mês, um par por mês da janela ─────────────────────
        JsonArray serie = new JsonArray();
        for (int i = 0; i < MESES; i++) {
            LocalDate m = ini.plusMonths(i);
            String chaveA = m.toString().substring(0, 7);
            String chaveB = m.minusYears(1).toString().substring(0, 7);

            Balde a = pa.meses.getOrDefault(chaveA, Balde.VAZIO);
            Balde b = pb.meses.getOrDefault(chaveB, Balde.VAZIO);

            JsonObject o = new JsonObject();
            o.addProperty("mes",   chaveA);
            o.addProperty("label", rotuloMes(chaveA));
            o.addProperty("mesAnterior",   chaveB);
            o.addProperty("labelAnterior", rotuloMes(chaveB));
            o.addProperty("litros",          a.litros);
            o.addProperty("valor",           a.valor);
            o.addProperty("preco",           a.preco());
            o.addProperty("litrosAnterior",  b.litros);
            o.addProperty("valorAnterior",   b.valor);
            o.addProperty("precoAnterior",   b.preco());
            o.addProperty("varLitros", pct(a.litros, b.litros));
            o.addProperty("varPreco",  pct(a.preco(), b.preco()));
            serie.add(o);
        }
        r.add("meses", serie);

        r.add("fornecedores", confronto(pa.fornecedores, pb.fornecedores, pa.litros));
        r.add("materiais",    confronto(pa.materiais,    pb.materiais,    pa.litros));

        // Diagnóstico: de qual coluna saiu a quantidade e de qual saiu o
        // valor. Nomeia também todas as colunas que a consulta devolveu,
        // para o caso de nenhuma candidata ter casado.
        JsonObject diag = new JsonObject();
        diag.addProperty("itens", atual.size());
        diag.addProperty("colunaQtde",  pa.colunaQtde  == null ? "" : pa.colunaQtde);
        diag.addProperty("colunaValor", pa.colunaValor == null ? "" : pa.colunaValor);
        JsonArray cols = new JsonArray();
        if (!atual.isEmpty()) atual.get(0).keySet().forEach(cols::add);
        diag.add("colunas", cols);
        r.add("diag", diag);

        return r;
    }

    private static JsonObject resumo(Periodo p) {
        JsonObject o = new JsonObject();
        o.addProperty("litros", p.litros);
        o.addProperty("valor",  p.valor);
        o.addProperty("preco",  p.preco());
        o.addProperty("notas",  p.notas.size());
        return o;
    }

    private static JsonObject variacao(Periodo a, Periodo b) {
        JsonObject o = new JsonObject();
        o.addProperty("litros", pct(a.litros, b.litros));
        o.addProperty("valor",  pct(a.valor,  b.valor));
        o.addProperty("preco",  pct(a.preco(), b.preco()));
        return o;
    }

    /**
     * Lista do período atual com o mesmo nome no período anterior ao lado.
     * Ordena pelo atual, mas quem só apareceu no ano passado entra no fim —
     * fornecedor que sumiu é informação, não ausência.
     */
    private static JsonArray confronto(Map<String, Balde> atual, Map<String, Balde> anterior, BigDecimal total) {
        Set<String> nomes = new LinkedHashSet<>(atual.keySet());
        nomes.addAll(anterior.keySet());

        List<String> ordenados = new ArrayList<>(nomes);
        ordenados.sort(Comparator.comparing(
                (String n) -> atual.getOrDefault(n, Balde.VAZIO).litros).reversed());

        JsonArray arr = new JsonArray();
        for (String n : ordenados) {
            Balde a = atual.getOrDefault(n, Balde.VAZIO);
            Balde b = anterior.getOrDefault(n, Balde.VAZIO);
            JsonObject o = new JsonObject();
            o.addProperty("nome", n);
            o.addProperty("litros", a.litros);
            o.addProperty("valor",  a.valor);
            o.addProperty("preco",  a.preco());
            o.addProperty("litrosAnterior", b.litros);
            o.addProperty("precoAnterior",  b.preco());
            o.addProperty("varLitros", pct(a.litros, b.litros));
            o.addProperty("pct", total.signum() == 0 ? BigDecimal.ZERO
                    : a.litros.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP));
            arr.add(o);
        }
        return arr;
    }

    // ── Agregação ─────────────────────────────────────────────────────────

    static Periodo agregar(List<Map<String, Object>> linhas) {
        Periodo p = new Periodo();
        if (linhas.isEmpty()) return p;

        p.colunaQtde  = escolherColuna(linhas, COLUNAS_QTDE);
        p.colunaValor = escolherColuna(linhas, COLUNAS_VALOR);
        String colUnit = p.colunaValor == null ? escolherColuna(linhas, COLUNAS_UNITARIO) : null;

        for (Map<String, Object> l : linhas) {
            BigDecimal litros = p.colunaQtde == null ? BigDecimal.ZERO : decimal(l.get(p.colunaQtde));
            BigDecimal valor;
            if (p.colunaValor != null) {
                valor = decimal(l.get(p.colunaValor));
            } else if (colUnit != null) {
                // Sem coluna de total: unitário × quantidade. É o mesmo
                // número, e é melhor que deixar o valor zerado na tela.
                valor = decimal(l.get(colUnit)).multiply(litros);
            } else {
                valor = BigDecimal.ZERO;
            }
            if (litros.signum() == 0 && valor.signum() == 0) continue;

            String mes  = texto(l.get("data_entrada"));
            mes = mes.length() >= 7 ? mes.substring(0, 7) : "";
            String forn = vazioVira(texto(l.get("fornecedor_nome")), "Sem fornecedor na OC");
            String mat  = vazioVira(texto(l.get("material_descricao")), "Sem descrição");

            p.litros = p.litros.add(litros);
            p.valor  = p.valor.add(valor);
            p.notas.add(texto(l.get("nota_serie")) + "|" + texto(l.get("nota_numero")));

            p.meses.computeIfAbsent(mes, k -> new Balde()).soma(litros, valor);
            p.fornecedores.computeIfAbsent(forn, k -> new Balde()).soma(litros, valor);
            p.materiais.computeIfAbsent(mat, k -> new Balde()).soma(litros, valor);
        }
        return p;
    }

    /** Primeira coluna candidata que existe e traz algum número diferente de zero. */
    static String escolherColuna(List<Map<String, Object>> linhas, String[] candidatas) {
        String presenteMasZerada = null;
        for (String c : candidatas) {
            boolean existe = false;
            for (Map<String, Object> l : linhas) {
                if (!l.containsKey(c)) break;
                existe = true;
                if (decimal(l.get(c)).signum() != 0) return c;
            }
            // Coluna existe mas veio zerada em tudo: fica como último recurso,
            // e a próxima candidata ainda tem chance de trazer valor.
            if (existe && presenteMasZerada == null) presenteMasZerada = c;
        }
        return presenteMasZerada;
    }

    static final class Periodo {
        BigDecimal litros = BigDecimal.ZERO;
        BigDecimal valor  = BigDecimal.ZERO;
        final Set<String> notas = new LinkedHashSet<>();
        final Map<String, Balde> meses        = new TreeMap<>();
        final Map<String, Balde> fornecedores = new LinkedHashMap<>();
        final Map<String, Balde> materiais    = new LinkedHashMap<>();
        String colunaQtde;
        String colunaValor;

        BigDecimal preco() { return Balde.preco(valor, litros); }
    }

    static final class Balde {
        static final Balde VAZIO = new Balde();
        BigDecimal litros = BigDecimal.ZERO;
        BigDecimal valor  = BigDecimal.ZERO;

        void soma(BigDecimal l, BigDecimal v) { litros = litros.add(l); valor = valor.add(v); }
        BigDecimal preco() { return preco(valor, litros); }

        static BigDecimal preco(BigDecimal valor, BigDecimal litros) {
            return litros.signum() == 0 ? BigDecimal.ZERO
                    : valor.divide(litros, 4, RoundingMode.HALF_UP);
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────

    /**
     * Variação percentual. Sem base não existe percentual: devolve null, e a
     * tela escreve "novo" em vez de um "+100%" que não quer dizer nada.
     */
    static BigDecimal pct(BigDecimal agora, BigDecimal antes) {
        if (antes == null || antes.signum() == 0) return null;
        return agora.subtract(antes).multiply(BigDecimal.valueOf(100))
                    .divide(antes, 1, RoundingMode.HALF_UP);
    }

    /** "2026-03" -> "mar/26". */
    static String rotuloMes(String aaaaMm) {
        String[] nomes = { "jan","fev","mar","abr","mai","jun","jul","ago","set","out","nov","dez" };
        if (aaaaMm == null || !aaaaMm.matches("\\d{4}-\\d{2}")) return aaaaMm == null ? "" : aaaaMm;
        int m = Integer.parseInt(aaaaMm.substring(5, 7));
        if (m < 1 || m > 12) return aaaaMm;
        return nomes[m - 1] + "/" + aaaaMm.substring(2, 4);
    }

    private static String vazioVira(String v, String padrao) {
        return v == null || v.isEmpty() || "null".equalsIgnoreCase(v) ? padrao : v;
    }

    private static String texto(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    static BigDecimal decimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
