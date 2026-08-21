package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.IndicadoresEconomicosDAO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET /api/indicadores -> tudo o que o painel de indicadores mostra.
 *
 * Uma chamada só, com todas as fontes. Cada uma vem no seu envelope
 * {dado, idadeMinutos, erro}: fonte fora do ar não derruba as outras, e a
 * tela sabe dizer o que está velho em vez de mostrar número antigo como se
 * fosse de agora.
 *
 * OS DERIVADOS SÃO CALCULADOS AQUI, e não na tela. São três fórmulas de
 * conversão que a diretoria usa para decidir preço; com uma cópia na tela
 * de computador e outra na de televisão, bastaria alguém corrigir uma delas
 * para as duas passarem a discordar — e ninguém saberia qual acreditar.
 */
@WebServlet({"/api/indicadores", "/api/indicadores/*"})
public class IndicadoresEconomicosServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(IndicadoresEconomicosServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final IndicadoresEconomicosDAO dao = new IndicadoresEconomicosDAO();

    /**
     * Açúcar VHP no mercado externo, em R$ por tonelada.
     *
     * Preço de bolsa (centavos de dólar por libra-peso) × 22,0462 (libras
     * numa tonelada, dividido por 100 para sair de centavos) × 1,04 (o
     * prêmio de polarização do VHP) × dólar. É a conta que estava no painel
     * antigo, mantida igual para o número não mudar de lugar.
     */
    private static final double LIBRAS_POR_TONELADA = 22.0462;
    private static final double PREMIO_VHP = 1.04;

    /**
     * Frete e demais custos até a usina, somados ao indicador CEPEA do
     * etanol. Estavam escritos direto na conta do painel antigo.
     */
    private static final double AJUSTE_ETANOL = 0.1305;
    /** Rendimento de conversão para hidratado destilaria e fermentação. */
    private static final double REND_DESTILARIA = 0.81;
    private static final double REND_FERMENTACAO = 0.88;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try {
            JsonObject dolar = dao.dolar();
            JsonObject cepea = dao.cepea();
            JsonObject acucar = dao.acucar();

            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.addProperty("atualizadoEm",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            r.add("dolar", dolar);
            // Dois recortes: os últimos pregões (10 dias corridos dão ~7
            // úteis) e um ponto por mês nos últimos três meses.
            r.add("dolarDiario", dao.dolarHistorico(10));
            r.add("dolarMensal", dao.dolarMensal(3));
            r.add("indicadores", dao.indicadores());
            r.add("cepea", cepea);
            r.add("acucar", acucar);
            r.add("acucarDiario", dao.acucarDiario(7));
            r.add("acucarMensal", dao.acucarMensal(3));
            r.add("precoCana", precoCana());
            r.add("produtos", produtos(dolar, cepea, acucar));
            r.add("noticias", dao.noticias());

            escrever(resp, GSON.toJson(r));

        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro no painel de indicadores", e);
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

    // ── Preços derivados ──────────────────────────────────────────────────

    /** As linhas da tabela "Preço dos Produtos", já convertidas. */
    static JsonArray produtos(JsonObject dolar, JsonObject cepea, JsonObject acucar) {
        JsonArray out = new JsonArray();
        double cotacao = caminho(dolar, "dado", "ultimo");

        // Açúcar VHP ME, a partir da bolsa de Nova York.
        JsonElement acDado = acucar == null ? null : acucar.get("dado");
        if (cotacao > 0 && acDado != null && !acDado.isJsonNull()) {
            // Só os cinco primeiros vencimentos: a curva da bolsa vai longe,
            // mas repetir "Açúcar VHP ME" doze vezes não informa mais nada.
            int n = 0;
            for (String[] p : mesesDoAcucar(acDado)) {
                if (n >= 5) break;
                double centavos = numero(p[1]);
                if (centavos <= 0) continue;
                out.add(linha(p[0], "Açúcar VHP ME", "t",
                        centavos * LIBRAS_POR_TONELADA * PREMIO_VHP * cotacao));
                n++;
            }
        }

        // Etanol, a partir do indicador do CEPEA para Alagoas.
        JsonElement cpDado = cepea == null ? null : cepea.get("dado");
        if (cpDado != null && !cpDado.isJsonNull()) {
            for (JsonElement el : cpDado.getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                String produto = o.get("produto").getAsString();
                String data = o.get("data").getAsString();
                double v = o.get("valor").getAsDouble();
                if (produto.contains("Hidratado")) {
                    out.add(linha(data, "Etanol Hidratado · DE", "L",
                            (v + AJUSTE_ETANOL) / REND_DESTILARIA));
                    out.add(linha(data, "Etanol Hidratado · FE", "L",
                            (v + AJUSTE_ETANOL) / REND_FERMENTACAO));
                } else if (produto.contains("Anidro")) {
                    out.add(linha(data, "Etanol Anidro", "L", v + AJUSTE_ETANOL));
                }
            }
        }
        return out;
    }

    /**
     * Os meses de vencimento que o serviço do açúcar devolveu.
     *
     * O formato não é fixo — o serviço é de casa e já mudou de forma. Aceita
     * tanto {dados_historicos:[{mes,ultimo}]} quanto uma lista direta, e
     * ignora o que não reconhecer em vez de estourar.
     */
    private static java.util.List<String[]> mesesDoAcucar(JsonElement dado) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        JsonArray arr = null;
        if (dado.isJsonArray()) {
            arr = dado.getAsJsonArray();
        } else if (dado.isJsonObject() && dado.getAsJsonObject().has("dados_historicos")) {
            JsonElement h = dado.getAsJsonObject().get("dados_historicos");
            if (h.isJsonArray()) arr = h.getAsJsonArray();
        }
        if (arr == null) return out;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String mes = o.has("mes") ? o.get("mes").getAsString().trim() : "";
            String ultimo = o.has("ultimo") ? o.get("ultimo").getAsString() : "";
            if (!mes.isEmpty() && !ultimo.isEmpty()) out.add(new String[]{ mes, ultimo });
        }
        return out;
    }

    private static JsonObject linha(String data, String produto, String unidade, double valor) {
        JsonObject o = new JsonObject();
        o.addProperty("data", data);
        o.addProperty("produto", produto);
        o.addProperty("unidade", unidade);
        o.addProperty("valor", Math.round(valor * 10000.0) / 10000.0);
        return o;
    }

    // ── Preço da cana (CONSECANA-AL) ──────────────────────────────────────

    private static final br.com.lopes.fluxo.dao.PrecoCanaDAO PRECO_CANA =
            new br.com.lopes.fluxo.dao.PrecoCanaDAO();

    /**
     * Últimos meses do preço do kg de ATR, do banco (alimentado pelo import do
     * PDF do CONSECANA na administração). Se o banco não tiver nada, cai para
     * o CSV embutido — assim o painel nunca fica sem a tabela.
     */
    static JsonArray precoCana() {
        try {
            java.util.List<br.com.lopes.fluxo.dao.PrecoCanaDAO.Linha> linhas = PRECO_CANA.ultimos(4);
            if (linhas != null && !linhas.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (var l : linhas) {
                    JsonObject o = new JsonObject();
                    o.addProperty("mes", l.rotulo());
                    o.addProperty("bruto", l.bruto());
                    o.addProperty("liquido", l.liquido());
                    arr.add(o);
                }
                return arr;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Preço da cana do banco indisponível; usando o CSV", e);
        }
        return precoCanaCsv();
    }

    /** Reserva: a tabela digitada no recurso, para quando o banco não responde. */
    static JsonArray precoCanaCsv() {
        JsonArray arr = new JsonArray();
        try (InputStream in = IndicadoresEconomicosServlet.class
                .getResourceAsStream("/preco-cana-sindacucar.csv")) {
            if (in == null) return arr;
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String linha;
            while ((linha = br.readLine()) != null) {
                linha = linha.trim();
                if (linha.isEmpty() || linha.startsWith("#")) continue;
                String[] p = linha.split(";", -1);
                if (p.length < 3) continue;
                JsonObject o = new JsonObject();
                o.addProperty("mes", p[0].trim());
                o.addProperty("bruto", numero(p[1]));
                o.addProperty("liquido", numero(p[2]));
                arr.add(o);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Não foi possível ler o preço da cana", e);
        }
        return arr;
    }

    // ── Utilidades ────────────────────────────────────────────────────────

    private static double caminho(JsonObject raiz, String... campos) {
        JsonElement atual = raiz;
        for (String c : campos) {
            if (atual == null || !atual.isJsonObject()) return 0;
            atual = atual.getAsJsonObject().get(c);
        }
        return (atual == null || atual.isJsonNull()) ? 0 : atual.getAsDouble();
    }

    /**
     * Número que pode chegar nos dois formatos.
     *
     * A VÍRGULA é quem decide. Tendo vírgula, é escrita brasileira e o ponto
     * é separador de milhar ("1.234,56"); não tendo, o ponto é o decimal
     * ("1.4875"). Na primeira versão eu apagava o ponto sempre, e o preço da
     * cana — 1,4875 no arquivo — virava 14.875 na tela. Erro de escala não
     * se disfarça: aparece dez mil vezes maior e ainda assim alguém pode
     * copiar antes de estranhar.
     */
    private static double numero(String v) {
        if (v == null) return 0;
        String t = v.trim();
        if (t.isEmpty()) return 0;
        try {
            return t.indexOf(',') >= 0
                    ? Double.parseDouble(t.replace(".", "").replace(',', '.'))
                    : Double.parseDouble(t);
        } catch (NumberFormatException e) { return 0; }
    }
}
