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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET /api/demonstrativo-financeiro        -> balancete do último mês fechado
 * GET /api/demonstrativo-financeiro/sql    -> o SQL literal, já com o mês
 *
 * O QUE ESTA ROTA FAZ E O QUE NÃO FAZ. Ela devolve o balancete cru — uma
 * linha por conta contábil, do grau 1 ao 5 — e o mês até onde a
 * contabilidade fechou. Ela NÃO monta as linhas do DRE, porque o de/para
 * entre conta contábil e linha do demonstrativo ainda não foi carregado
 * neste projeto. Enquanto não for, a tela mostra o ano corrente pelos
 * valores fixos e diz, à vista, que é isso que está fazendo.
 *
 * Preferir isso a inventar uma classificação por prefixo de conta não é
 * excesso de cuidado: num DRE, jogar uma conta na linha errada não deixa
 * rastro nenhum na tela — os totais continuam somando, só que erradas.
 */
@WebServlet({"/api/demonstrativo-financeiro", "/api/demonstrativo-financeiro/*"})
public class DemonstrativoFinanceiroServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(DemonstrativoFinanceiroServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final DemonstrativoFinanceiroDAO dao = new DemonstrativoFinanceiroDAO();

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
            if (anomes == null) anomes = dao.ultimoAnomesFechado();

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
                escrever(resp, "{\"ok\":false,\"erro\":\"A contabilidade não devolveu nenhum mês fechado\"}");
                return;
            }

            escrever(resp, GSON.toJson(montar(anomes, dao.saldos(anomes))));

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

    static JsonObject montar(int anomes, List<Map<String, Object>> contas) {
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

        // O de/para conta → linha do DRE ainda não existe neste projeto.
        // Dizer isso no JSON é o que permite a tela avisar em vez de mostrar
        // um número montado no chute.
        r.add("linhas", null);
        r.addProperty("mapaCarregado", false);
        return r;
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
