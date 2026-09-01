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
 * O ANO É PARTIDO EM DOIS, como a controladoria acompanha:
 *   SAFRA      setembro a fevereiro  (a moagem)
 *   ENTRESSAFRA março a agosto       (a parada e a manutenção)
 * São seis meses cada, e é o recorte do painel que serviu de modelo.
 *
 * DEVOLVE A LINHA CRUA, por mês × grupo × empenho × objeto de custo, e não
 * um resumo. A tela
 * troca de mês, liga o acumulado, filtra negócio e abre grupo sem voltar ao
 * servidor — e, o que importa mais, todos esses recortes saem da MESMA
 * consulta. Se cada interação fosse uma ida ao Oracle, uma delas acabaria
 * somando diferente das outras e ninguém saberia qual acreditar.
 *
 * São seis meses de dezenas de empenhos: algumas centenas de linhas, longe
 * de ser volume que peça agregação no banco.
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
            if ("/itens".equals(rota)) {
                escrever(resp, GSON.toJson(itens(req)));
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

    /**
     * ini/fim em AAAAMM. Sem eles, o semestre corrente: entressafra de março
     * a agosto, safra de setembro a fevereiro.
     */
    static int[] periodo(HttpServletRequest req) {
        Integer ini = anomes(req.getParameter("ini"));
        Integer fim = anomes(req.getParameter("fim"));
        if (ini != null && fim != null && ini <= fim) return new int[]{ ini, fim };

        LocalDate h = LocalDate.now();
        int ano = h.getYear(), mes = h.getMonthValue();
        if (mes >= 3 && mes <= 8) return new int[]{ ano * 100 + 3, ano * 100 + 8 };
        // Setembro a dezembro abre a safra do próprio ano; janeiro e
        // fevereiro ainda são o fim da safra que começou no ano anterior.
        int inicio = mes >= 9 ? ano : ano - 1;
        return new int[]{ inicio * 100 + 9, (inicio + 1) * 100 + 2 };
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

    // ── Itens (4º nível: material + fornecedor / contrato) ────────────────

    private JsonObject itens(HttpServletRequest req) {
        JsonObject r = new JsonObject();
        int[] meses = mesesDe(req.getParameter("meses"));
        int empenho = req.getParameter("empenho") != null
                && req.getParameter("empenho").trim().matches("-?\\d+")
                ? Integer.parseInt(req.getParameter("empenho").trim()) : Integer.MIN_VALUE;
        String objeto = req.getParameter("objeto");
        if (meses.length == 0 || empenho == Integer.MIN_VALUE) {
            r.addProperty("ok", false);
            r.addProperty("erro", "informe ao menos um mês e o empenho");
            return r;
        }
        List<Map<String, Object>> linhas = dao.itens(meses, empenho, objeto);
        JsonArray arr = new JsonArray();
        double soma = 0;
        for (Map<String, Object> l : linhas) {
            double v = decimal(l.get("valor")).doubleValue();
            soma += v;
            // qtde_aprovada é a que efetivamente virou compra; "quantidade" (a
            // pedida) só entra se aquela não vier preenchida. Sem nenhuma das
            // duas, não dá pra saber o valor unitário — fica null, não zero,
            // pra tela não mostrar um "R$ 0,00" que não existe de verdade.
            double qtdeAprovada = decimal(l.get("qtde_aprovada")).doubleValue();
            double qtdePedida = decimal(l.get("quantidade")).doubleValue();
            double qtde = qtdeAprovada > 0 ? qtdeAprovada : qtdePedida;
            Double quantidade = qtde > 0 ? Double.valueOf(qtde) : null;
            Double valorUnitario = qtde > 0 ? Double.valueOf(v / qtde) : null;

            JsonObject o = new JsonObject();
            o.addProperty("anomes", decimal(l.get("anomes")).intValue());
            o.addProperty("valor", v);
            o.addProperty("origem", texto(l.get("origem")));
            o.addProperty("codMaterial", texto(l.get("cod_material")));
            o.addProperty("material", texto(l.get("descricao_material")));
            o.addProperty("codFornecedor", texto(l.get("cod_fornecedor")));
            o.addProperty("fornecedor", texto(l.get("nome_fornecedor")));
            o.addProperty("nroc", texto(l.get("nroc")));
            o.addProperty("cotacao", texto(l.get("nr_cotacao")));
            o.addProperty("solicitacao", texto(l.get("nr_solicitacao")));
            o.addProperty("contrato", texto(l.get("numerocontrato")));
            o.addProperty("contratoResumo", texto(l.get("contrato_resumo")));
            o.addProperty("quantidade", quantidade);
            o.addProperty("valorUnitario", valorUnitario);
            arr.add(o);
        }
        r.addProperty("ok", true);
        r.add("itens", arr);
        r.addProperty("soma", soma);
        return r;
    }

    /** "202509,202510" -> {202509,202510}; ignora o que não for AAAAMM válido. */
    static int[] mesesDe(String v) {
        if (v == null || v.isBlank()) return new int[0];
        java.util.List<Integer> ms = new java.util.ArrayList<>();
        for (String p : v.split(",")) {
            Integer a = anomes(p);
            if (a != null && !ms.contains(a)) ms.add(a);
        }
        int[] out = new int[ms.size()];
        for (int i = 0; i < out.length; i++) out[i] = ms.get(i);
        return out;
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

        // Os meses do período, TODOS — inclusive os que não tiveram
        // lançamento. Mês sem movimento sumindo da tira faria a sequência
        // pular de maio para julho, e quem lê acha que junho não existiu.
        JsonArray meses = new JsonArray();
        for (int a = ini; a <= fim; a = proximo(a)) {
            JsonObject m = new JsonObject();
            m.addProperty("anomes", a);
            m.addProperty("label", rotuloMes(a));
            m.addProperty("nome", nomeMes(a));
            meses.add(m);
        }
        r.add("meses", meses);

        TreeSet<String> negocios = new TreeSet<>();
        JsonArray arr = new JsonArray();
        for (Map<String, Object> l : linhas) {
            String n = texto(l.get("negocio"));
            if (!n.isEmpty()) negocios.add(n);

            BigDecimal o = decimal(l.get("orcado"));
            BigDecimal rr = decimal(l.get("realizado"));
            // Linha zerada dos dois lados é ruído do plano de contas.
            if (o.signum() == 0 && rr.signum() == 0) continue;

            JsonObject e = new JsonObject();
            e.addProperty("anomes", decimal(l.get("anomes")).intValue());
            e.addProperty("negocio", n);
            e.addProperty("codGrupo", texto(l.get("cod_grupoempenho")));
            e.addProperty("grupo", vazioVira(texto(l.get("grupo")), "Sem grupo"));
            e.addProperty("codEmpenho", texto(l.get("cod_empenho")));
            e.addProperty("empenho", vazioVira(texto(l.get("empenho")), "Sem descrição"));
            e.addProperty("codObjeto", texto(l.get("cod_objeto")));
            e.addProperty("objeto", vazioVira(texto(l.get("objeto")), "Sem objeto de custo"));
            e.addProperty("tipo", vazioVira(texto(l.get("tipo")), "Compra"));
            e.addProperty("orcado", o);
            e.addProperty("realizado", rr);
            arr.add(e);
        }
        r.add("negocios", GSON.toJsonTree(negocios));
        r.add("linhas", arr);
        r.addProperty("totalLinhas", arr.size());
        return r;
    }

    /** 202512 -> 202601. Somar 1 em AAAAMM pularia para o mês 13. */
    static int proximo(int anomes) {
        int ano = anomes / 100, mes = anomes % 100;
        return mes >= 12 ? (ano + 1) * 100 + 1 : ano * 100 + mes + 1;
    }

    /** 202603 -> "Março". */
    static String nomeMes(int anomes) {
        String[] nomes = { "Janeiro","Fevereiro","Março","Abril","Maio","Junho",
                           "Julho","Agosto","Setembro","Outubro","Novembro","Dezembro" };
        int m = anomes % 100;
        return (m >= 1 && m <= 12) ? nomes[m - 1] : String.valueOf(anomes);
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
