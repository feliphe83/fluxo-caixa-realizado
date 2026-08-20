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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET /api/balanco-patrimonial -> o balanço do último mês fechado.
 *
 * Usa o MESMO balancete e o MESMO fechamento do Demonstrativo do Resultado
 * ({@link DemonstrativoFinanceiroDAO}). Duas consultas separadas para a
 * mesma contabilidade acabariam, em algum fechamento, mostrando meses
 * diferentes em duas telas do mesmo sistema — e quem visse as duas não teria
 * como saber qual estava certa.
 *
 * ── O QUE FAZ O BALANÇO FECHAR ──
 *
 * 1. O SINAL vem de debito_credito, não de "saldo". A consulta do ERP
 *    devolve saldo em valor absoluto (ver {@link DemonstrativoFinanceiroServlet}).
 *
 * 2. PASSIVO É CREDOR. Chega negativo do balancete e o balanço mostra
 *    positivo, então o lado do passivo inverte. O ativo vai como está.
 *
 * 3. O RESULTADO DO EXERCÍCIO ENTRA NO PATRIMÔNIO. O índice marca todo o
 *    grupo 3 como "NÃO UTILIZAR" — conta a conta ele é o DRE, não o balanço.
 *    Mas o resultado do ano compõe o patrimônio líquido, e sem ele o balanço
 *    não fecha: são exatamente os R$ 27.814,91 mil que faltavam em
 *    "Prejuízos acumulados" quando conferi contra a planilha.
 *
 * Conferido: as 41 linhas de nível 2 batem com a planilha da controladoria
 * no fechamento de abril de 2026, e ativo e passivo dão os mesmos
 * R$ 294.296,63 mil.
 */
@WebServlet({"/api/balanco-patrimonial", "/api/balanco-patrimonial/*"})
public class BalancoPatrimonialServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(BalancoPatrimonialServlet.class.getName());
    private static final Gson GSON = new Gson();
    private static final BigDecimal MIL = BigDecimal.valueOf(1000);

    private final DemonstrativoFinanceiroDAO dao = new DemonstrativoFinanceiroDAO();

    /** conta -> tipo|grupo|nivel|nivel2, já resolvido pelo gerador. */
    private static volatile Map<String, String[]> indice;

    /** Onde o resultado do exercício aterrissa dentro do patrimônio. */
    private static final String NIVEL2_DO_RESULTADO = "Prejuízos acumulados";

    private static final String[] MESES = {
        "janeiro","fevereiro","março","abril","maio","junho",
        "julho","agosto","setembro","outubro","novembro","dezembro"
    };

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try {
            LocalDate fechamento = dao.fechamentoContabil();
            if (fechamento == null) {
                resp.setStatus(500);
                escrever(resp, "{\"ok\":false,\"erro\":\"geral.filial não devolveu o início do período contábil\"}");
                return;
            }
            int anomes = DemonstrativoFinanceiroDAO.anomesDe(fechamento);
            escrever(resp, GSON.toJson(montar(anomes, fechamento, dao.saldos(anomes), indice())));

        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro no balanço patrimonial", e);
            resp.setStatus(500);
            String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            escrever(resp, "{\"ok\":false,\"erro\":\""
                    + msg.replace("\\", " ").replace("\"", "'").replace("\n", " ") + "\"}");
        }
    }

    private void escrever(HttpServletResponse resp, String corpo) throws IOException {
        resp.getWriter().print(corpo);
        resp.getWriter().flush();
    }

    static JsonObject montar(int anomes, LocalDate fechamento,
                             List<Map<String, Object>> contas, Map<String, String[]> indice) {
        int ano = anomes / 100, mes = anomes % 100;

        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("anomes", anomes);
        r.addProperty("ano", ano);
        r.addProperty("mes", mes);
        r.addProperty("mesNome", MESES[mes - 1]);
        r.addProperty("fechadoAte", MESES[mes - 1] + " de " + ano);
        if (fechamento != null) {
            r.addProperty("fechamento", fechamento.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        }
        r.addProperty("atualizadoEm", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        Map<String, BigDecimal> soma = new LinkedHashMap<>();
        JsonArray semLinha = new JsonArray();
        BigDecimal resultadoExercicio = null;

        for (Map<String, Object> c : contas) {
            String conta = texto(c.get("cod_contacontabil_formatado")).trim();

            // O grupo 1 do plano é a conta "3": o resultado do exercício
            // inteiro, já somado pelo próprio balancete.
            if ("3".equals(conta)) { resultadoExercicio = assinado(c); continue; }

            String[] h = indice.get(conta);
            if (h == null) {
                // Conta analítica de patrimônio ou de resultado que ficou
                // sem destino é dinheiro fora do balanço — e um balanço que
                // não fecha por um lado só some, ele não avisa.
                if (conta.chars().filter(ch -> ch == '.').count() == 4
                        && !conta.startsWith("3.")
                        && assinado(c).signum() != 0) {
                    JsonObject o = new JsonObject();
                    o.addProperty("conta", conta);
                    o.addProperty("descricao", texto(c.get("descricao")).trim());
                    o.addProperty("saldo", assinado(c));
                    semLinha.add(o);
                }
                continue;
            }
            soma.merge(chave(h), assinado(c), BigDecimal::add);
        }

        if (resultadoExercicio != null) {
            String[] pl = { "Passivo", "Patrimônio líquido", "Patrimônio líquido", NIVEL2_DO_RESULTADO };
            soma.merge(chave(pl), resultadoExercicio, BigDecimal::add);
        }

        JsonObject linhas = new JsonObject();
        for (Map.Entry<String, BigDecimal> e : soma.entrySet()) {
            // Passivo é credor: chega negativo e o balanço mostra positivo.
            boolean passivo = e.getKey().startsWith("Passivo|");
            BigDecimal v = passivo ? e.getValue().negate() : e.getValue();
            linhas.addProperty(e.getKey(), v.divide(MIL, 2, RoundingMode.HALF_UP));
        }
        r.add("linhas", linhas);

        JsonObject conf = new JsonObject();
        conf.add("contasSemLinhaNoIndice", semLinha);
        conf.addProperty("resultadoExercicio", resultadoExercicio == null ? BigDecimal.ZERO
                : resultadoExercicio.divide(MIL, 2, RoundingMode.HALF_UP));
        r.add("conferencia", conf);
        r.addProperty("contasNoIndice", indice.size());
        r.addProperty("totalContas", contas.size());
        return r;
    }

    /** A chave que a tela usa: os quatro níveis separados por barra. */
    static String chave(String[] h) {
        return h[0] + "|" + h[1] + "|" + h[2] + "|" + h[3];
    }

    /** Saldo COM sinal — a coluna "saldo" do ERP vem em valor absoluto. */
    static BigDecimal assinado(Map<String, Object> c) {
        BigDecimal v = decimal(c.get("saldo")).abs();
        return "C".equalsIgnoreCase(texto(c.get("debito_credito")).trim()) ? v.negate() : v;
    }

    /**
     * O de/para em uso: o importado pela administração e, se ninguém importou
     * nada, o arquivo embutido no sistema.
     */
    static Map<String, String[]> indice() {
        Map<String, String[]> cache = indice;
        if (cache != null) return cache;
        Map<String, String[]> doBanco = new br.com.lopes.fluxo.dao.IndiceContabilDAO().balanco();
        Map<String, String[]> m = doBanco.isEmpty() ? doArquivo() : doBanco;
        LOG.info("De/para do balanço: " + m.size() + " contas ("
               + (doBanco.isEmpty() ? "arquivo do sistema" : "planilha importada") + ")");
        indice = m;
        return m;
    }

    /** Esquece o índice guardado — chamado quando a administração importa outro. */
    static void esquecerIndice() { indice = null; }

    /** O índice embutido no WAR, gerado por ferramentas/gerar-balanco.py. */
    static Map<String, String[]> doArquivo() {
        Map<String, String[]> m = new LinkedHashMap<>();
        try (java.io.InputStream in =
                     BalancoPatrimonialServlet.class.getResourceAsStream("/balanco-indice.csv")) {
            if (in == null) throw new IllegalStateException("balanco-indice.csv não encontrado");
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            String linha;
            while ((linha = br.readLine()) != null) {
                linha = linha.trim();
                if (linha.isEmpty() || linha.startsWith("#")) continue;
                String[] p = linha.split(";", -1);
                if (p.length >= 5) {
                    m.put(p[0].trim(), new String[]{ p[1].trim(), p[2].trim(), p[3].trim(), p[4].trim() });
                }
            }
        } catch (java.io.IOException e) {
            LOG.log(Level.SEVERE, "Erro ao ler o de/para do balanço", e);
        }
        return m;
    }

    private static String texto(Object v) { return v == null ? "" : String.valueOf(v); }

    private static BigDecimal decimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
