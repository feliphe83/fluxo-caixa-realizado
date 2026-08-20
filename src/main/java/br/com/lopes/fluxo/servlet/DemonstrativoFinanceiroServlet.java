package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.DemonstrativoFinanceiroDAO;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET /api/demonstrativo-financeiro        -> balancete do último mês fechado
 * GET /api/demonstrativo-financeiro/sql    -> o SQL literal, já com o mês
 *
 * Monta as linhas do DRE do ano corrente somando o balancete pelo de/para
 * da controladoria (resources/dre-indice.csv, 232 contas analíticas).
 *
 * ── DUAS COISAS QUE FAZEM O NÚMERO SAIR CERTO ──
 *
 * 1. O SINAL NÃO ESTÁ EM "saldo". A consulta do ERP termina com
 *    "case when ('N'='N') and (sum(saldo) < 0) then sum(saldo)*(-1) else
 *    sum(saldo) end saldo" — como 'N'='N' é sempre verdadeiro, todo saldo
 *    negativo sai positivo dali. O sinal sobrevive só em debito_credito
 *    ('C' = credor = negativo), que existe justamente para isso. Usar
 *    "saldo" direto faria a receita e a despesa somarem no mesmo sentido,
 *    e o resultado sairia errado sem nada na tela denunciar.
 *
 * 2. O DRE É O INVERSO DO BALANCETE. Receita é credora e aparece negativa
 *    no balancete; despesa é devedora e aparece positiva. O demonstrativo
 *    mostra o contrário, então a conversão é -(soma) / 1000 (o quadro é em
 *    R$ mil). Conferido contra a planilha da controladoria: as nove linhas
 *    que vêm de conta batem ao centavo no fechamento de 2026.
 *
 * As linhas restantes (receita líquida, lucro bruto, subtotais e os dois
 * resultados) são derivadas aqui, não mapeadas — somar contas para elas
 * contaria o mesmo dinheiro duas vezes.
 */
@WebServlet({"/api/demonstrativo-financeiro", "/api/demonstrativo-financeiro/*"})
public class DemonstrativoFinanceiroServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(DemonstrativoFinanceiroServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final DemonstrativoFinanceiroDAO dao = new DemonstrativoFinanceiroDAO();

    /** conta contábil formatada ("3.1.1.01.003") -> chave da linha do DRE. */
    private static volatile Map<String, String> indice;

    /** Contas de 3.2.3: apropriam-se ao custo e se anulam. Não é linha do DRE. */
    private static final String APROPRIACAO = "apropriacao";

    private static final String[] MESES = {
        "janeiro","fevereiro","março","abril","maio","junho",
        "julho","agosto","setembro","outubro","novembro","dezembro"
    };

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        try {
            Integer anomes = anomesPedido(req);
            java.time.LocalDate fechamento = null;
            if (anomes == null) {
                fechamento = dao.fechamentoContabil();
                if (fechamento != null) anomes = DemonstrativoFinanceiroDAO.anomesDe(fechamento);
            }

            if ("/sql".equals(req.getPathInfo())) {
                JsonObject o = new JsonObject();
                o.addProperty("ok", true);
                o.addProperty("anomes", anomes);
                o.addProperty("sql", anomes == null ? "" : dao.sql(anomes));
                escrever(resp, GSON.toJson(o));
                return;
            }

            if (anomes == null) {
                resp.setStatus(500);
                escrever(resp, "{\"ok\":false,\"erro\":\"geral.filial não devolveu o início do período contábil\"}");
                return;
            }

            JsonObject r = montar(anomes, dao.saldos(anomes), indice());
            if (fechamento != null) {
                r.addProperty("fechamento",
                        fechamento.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            }
            escrever(resp, GSON.toJson(r));

        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro no demonstrativo financeiro", e);
            resp.setStatus(500);
            String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            escrever(resp, "{\"ok\":false,\"erro\":\""
                    + msg.replace("\\", " ").replace("\"", "'").replace("\n", " ") + "\"}");
        }
    }

    /** ?anomes=202604 força um mês; sem ele, vale o último fechado. */
    private static Integer anomesPedido(HttpServletRequest req) {
        String v = req.getParameter("anomes");
        if (v == null || !v.trim().matches("\\d{6}")) return null;
        int n = Integer.parseInt(v.trim());
        int mes = n % 100;
        return (mes >= 1 && mes <= 12) ? n : null;
    }

    private void escrever(HttpServletResponse resp, String corpo) throws IOException {
        resp.getWriter().print(corpo);
        resp.getWriter().flush();
    }

    static JsonObject montar(int anomes, List<Map<String, Object>> contas,
                             Map<String, String> indice) {
        int ano = anomes / 100, mes = anomes % 100;

        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("anomes", anomes);
        r.addProperty("ano", ano);
        r.addProperty("mes", mes);
        r.addProperty("mesNome", MESES[mes - 1]);
        r.addProperty("fechadoAte", MESES[mes - 1] + " de " + ano);
        r.addProperty("atualizadoEm", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        // Só as colunas que interessam. O balancete devolve catorze, e as
        // outras seis (class, antconta, totais gerais repetidos em toda
        // linha) só engordariam o JSON — num plano de contas grande isso é a
        // diferença entre uma tela que abre e uma que arrasta.
        JsonArray arr = new JsonArray();
        for (Map<String, Object> c : contas) {
            JsonObject o = new JsonObject();
            o.addProperty("conta",      texto(c.get("cod_contacontabil")));
            o.addProperty("formatada",  texto(c.get("cod_contacontabil_formatado")).trim());
            o.addProperty("descricao",  texto(c.get("descricao")).trim());
            o.addProperty("grau",       decimal(c.get("tamanho")).intValue());
            o.addProperty("saldo",      decimal(c.get("saldo")));
            o.addProperty("saldoMes",   decimal(c.get("saldomes")));
            o.addProperty("debito",     decimal(c.get("totaldebito")));
            o.addProperty("credito",    decimal(c.get("totalcredito")));
            arr.add(o);
        }
        r.add("contas", arr);
        r.addProperty("totalContas", arr.size());

        r.add("linhas", linhasDoDre(contas, indice, r));
        r.addProperty("mapaCarregado", indice != null && !indice.isEmpty());
        r.addProperty("contasNoIndice", indice == null ? 0 : indice.size());
        return r;
    }

    // ── O DRE a partir do balancete ───────────────────────────────────────

    private static final BigDecimal MIL = BigDecimal.valueOf(1000);

    static JsonObject linhasDoDre(List<Map<String, Object>> contas,
                                  Map<String, String> indice, JsonObject raiz) {
        if (indice == null || indice.isEmpty()) return null;

        Map<String, BigDecimal> soma = new LinkedHashMap<>();
        JsonArray naoMapeadas = new JsonArray();

        for (Map<String, Object> c : contas) {
            String formatada = texto(c.get("cod_contacontabil_formatado")).trim();
            String chave = indice.get(formatada);

            if (chave == null) {
                // Só a conta ANALÍTICA de RESULTADO precisa de destino aqui.
                //
                // Duas exclusões, e as duas importam:
                //  - a sintética é o somatório das filhas; se entrasse, cada
                //    real seria contado duas vezes (o formato de cinco níveis
                //    é o que as separa);
                //  - o balancete traz o plano inteiro, e as contas de ativo e
                //    passivo (grupos 1 e 2) não pertencem ao demonstrativo.
                //    Sem esta linha o aviso acusava 224 contas "sem destino"
                //    que estavam certas de estar de fora — alarme que se
                //    aprende a ignorar é pior do que alarme nenhum, porque
                //    esconde o dia em que ele estiver certo.
                if (formatada.startsWith("3.")
                        && formatada.chars().filter(ch -> ch == '.').count() == 4
                        && assinado(c).signum() != 0) {
                    JsonObject o = new JsonObject();
                    o.addProperty("conta", formatada);
                    o.addProperty("descricao", texto(c.get("descricao")).trim());
                    o.addProperty("saldo", assinado(c));
                    naoMapeadas.add(o);
                }
                continue;
            }
            soma.merge(chave, assinado(c), BigDecimal::add);
        }

        // -(soma) / 1000: o DRE é o inverso do balancete, e o quadro é em R$ mil.
        Map<String, BigDecimal> l = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : soma.entrySet()) {
            l.put(e.getKey(), e.getValue().negate().divide(MIL, 2, RoundingMode.HALF_UP));
        }
        for (String ch : new String[]{"receita_bruta","tributos","cpv","ociosidade",
                "desp_vendas","desp_admin","outras_op","equivalencia","nao_recorrente",
                "financeiro","ir_csll"}) {
            l.putIfAbsent(ch, BigDecimal.ZERO);
        }

        // Derivadas — a mesma cadeia do demonstrativo oficial.
        BigDecimal receitaLiquida = l.get("receita_bruta").add(l.get("tributos"));
        BigDecimal lucroBruto     = receitaLiquida.add(l.get("cpv")).add(l.get("ociosidade"));
        BigDecimal despesasOp     = l.get("desp_vendas").add(l.get("desp_admin"))
                                     .add(l.get("outras_op")).add(l.get("equivalencia"))
                                     .add(l.get("nao_recorrente"));
        BigDecimal resultadoOp    = lucroBruto.add(despesasOp);
        BigDecimal antesIr        = resultadoOp.add(l.get("financeiro"));
        BigDecimal liquido        = antesIr.subtract(l.get("ir_csll"));
        // Ajustado = líquido sem o não recorrente. Conferido nos cinco anos
        // em que a planilha tem valor nessa linha: 2021 a 2025, todos batem.
        BigDecimal ajustado       = liquido.subtract(l.get("nao_recorrente"));

        l.put("receita_liquida",     receitaLiquida);
        l.put("lucro_bruto",         lucroBruto);
        l.put("despesas_op",         despesasOp);
        l.put("resultado_op",        resultadoOp);
        l.put("antes_ir",            antesIr);
        l.put("resultado_liquido",   liquido);
        l.put("resultado_ajustado",  ajustado);

        JsonObject saida = new JsonObject();
        for (Map.Entry<String, BigDecimal> e : l.entrySet()) {
            if (APROPRIACAO.equals(e.getKey())) continue;   // não é linha do DRE
            saida.addProperty(e.getKey(), e.getValue());
        }

        // Diagnóstico: conta sem destino é dinheiro que sumiu do
        // demonstrativo sem nada na tela avisar. E a apropriação tem que dar
        // zero — se um dia não der, aparece aqui em vez de sumir por dentro.
        JsonObject diag = new JsonObject();
        diag.add("contasSemLinhaNoIndice", naoMapeadas);
        diag.addProperty("apropriacaoCusto",
                l.getOrDefault(APROPRIACAO, BigDecimal.ZERO));
        raiz.add("conferencia", diag);
        return saida;
    }

    /**
     * O saldo COM sinal.
     *
     * A coluna "saldo" da consulta do ERP vem em valor absoluto — o case
     * final dela troca o sinal de todo negativo. Quem guarda a informação é
     * debito_credito: 'C' é credor, e credor é negativo.
     */
    static BigDecimal assinado(Map<String, Object> c) {
        BigDecimal v = decimal(c.get("saldo")).abs();
        return "C".equalsIgnoreCase(texto(c.get("debito_credito")).trim()) ? v.negate() : v;
    }

    /**
     * O de/para em uso: o que a controladoria importou pela administração e,
     * se ninguém importou nada, o arquivo embutido no sistema.
     *
     * A ordem importa: o índice do banco é o que a contabilidade acabou de
     * corrigir, e o do arquivo é o da última geração por script. Preferir o
     * arquivo faria uma importação parecer que não pegou.
     */
    static Map<String, String> indice() {
        Map<String, String> cache = indice;
        if (cache != null) return cache;
        Map<String, String> doBanco = new br.com.lopes.fluxo.dao.IndiceContabilDAO().dre();
        Map<String, String> m = doBanco.isEmpty() ? doArquivo() : doBanco;
        LOG.info("De/para do DRE: " + m.size() + " contas ("
               + (doBanco.isEmpty() ? "arquivo do sistema" : "planilha importada") + ")");
        indice = m;
        return m;
    }

    /** Esquece o índice guardado — chamado quando a administração importa outro. */
    static void esquecerIndice() { indice = null; }

    /** O índice embutido no WAR, gerado por ferramentas/gerar-dre-indice.py. */
    static Map<String, String> doArquivo() {
        Map<String, String> m = new LinkedHashMap<>();
        try (java.io.InputStream in =
                     DemonstrativoFinanceiroServlet.class.getResourceAsStream("/dre-indice.csv")) {
            if (in == null) throw new IllegalStateException("dre-indice.csv não encontrado");
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            String linha;
            while ((linha = br.readLine()) != null) {
                linha = linha.trim();
                if (linha.isEmpty() || linha.startsWith("#")) continue;
                String[] p = linha.split(";", -1);
                if (p.length >= 2) m.put(p[0].trim(), p[1].trim());
            }
        } catch (java.io.IOException e) {
            LOG.log(Level.SEVERE, "Erro ao ler o de/para do DRE", e);
        }
        return m;
    }

    private static String texto(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
