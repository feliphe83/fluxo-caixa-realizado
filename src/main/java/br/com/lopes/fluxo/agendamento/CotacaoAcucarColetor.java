package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.CotacaoAcucarDAO;
import br.com.lopes.fluxo.dao.CotacaoAcucarDAO.Vencimento;
import br.com.lopes.fluxo.util.HttpUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Busca os vencimentos do açúcar NY nº 11 na bolsa e grava no MySQL.
 *
 * ── DE ONDE VEM ──
 *
 * O painel lia de um serviço interno da usina (179.97.38.58:5000) que só
 * responde de dentro da rede. A cotação em si é pública — o contrato é o
 * Sugar No. 11 da ICE, negociado em Nova York. Buscamos pelo endpoint de
 * cotação atrasada do TradingView (dez minutos de atraso, o padrão de quem
 * não paga o feed em tempo real da bolsa), um vencimento por consulta.
 *
 * ── QUAIS VENCIMENTOS ──
 *
 * O açúcar nº 11 vence em março, maio, julho e outubro — os códigos H, K, N
 * e V dos futuros. Em vez de manter uma lista fixa (que envelhece), geramos
 * os símbolos dos próximos anos e perguntamos um a um: o que já venceu volta
 * "symbol_not_exists" e fica de fora; o que responde, entra, ordenado pela
 * data de expiração. Assim a lista se renova sozinha a cada pregão.
 */
public class CotacaoAcucarColetor {

    private static final Logger LOG = Logger.getLogger(CotacaoAcucarColetor.class.getName());

    /** Cotação atrasada, um símbolo por chamada. */
    private static final String URL_SYMBOL =
        "https://scanner.tradingview.com/symbol?symbol=%s"
      + "&fields=close,open,high,low,change,change_abs,expiration,description,update_mode";

    /**
     * A SÉRIE HISTÓRICA do açúcar, para preencher o passado de uma vez.
     *
     * O TradingView entrega só o retrato do momento — bom para o preço de
     * agora, mas não conta a história. O Yahoo devolve a série diária do
     * contínuo de primeiro vencimento (SB=F) num JSON limpo. É o passado que
     * os gráficos precisam; o presente continua vindo da coleta de tempo em
     * tempo. %s = a janela (ex.: 6mo, 1y).
     */
    private static final String URL_HISTORICO =
        "https://query1.finance.yahoo.com/v8/finance/chart/SB=F?range=%s&interval=1d";

    /** Códigos de mês dos futuros em que o açúcar nº 11 vence. */
    private static final char[] MESES_CONTRATO = { 'H', 'K', 'N', 'V' };

    /** Mês do futuro -> abreviação em português, para o rótulo "Out26". */
    private static String mesPt(char codigo) {
        return switch (codigo) {
            case 'H' -> "Mar";
            case 'K' -> "Mai";
            case 'N' -> "Jul";
            case 'V' -> "Out";
            default  -> "?";
        };
    }

    private final CotacaoAcucarDAO dao = new CotacaoAcucarDAO();

    /**
     * Consulta a bolsa e grava o retrato no banco.
     *
     * @return quantos vencimentos foram gravados
     * @throws Exception se nenhum vencimento respondeu — aí NÃO grava, para
     *         não apagar o retrato bom com uma coleta vazia por falha de rede
     */
    public int coletar() throws Exception {
        List<Vencimento> ativos = new ArrayList<>();
        int ano = LocalDate.now().getYear();

        // Deste ano até três à frente cobre a curva inteira negociada.
        for (int y = ano; y <= ano + 3; y++) {
            for (char m : MESES_CONTRATO) {
                Vencimento v = consultar("ICEUS:SB" + m + y, m, y);
                if (v != null) ativos.add(v);
            }
        }
        if (ativos.isEmpty()) {
            throw new IllegalStateException("a bolsa não devolveu nenhum vencimento do açúcar");
        }
        ativos.sort((a, b) -> {
            if (a.expiracao == null) return 1;
            if (b.expiracao == null) return -1;
            return a.expiracao.compareTo(b.expiracao);
        });
        int n = dao.gravar(ativos, LocalDate.now());
        LOG.info("Cotação do açúcar coletada: " + n + " vencimento(s).");
        return n;
    }

    /**
     * Preenche o passado do histórico diário a partir do Yahoo, sem mexer no
     * que já foi coletado (INSERT IGNORE lá no DAO). Roda uma vez, quando o
     * histórico ainda está curto — depois é a coleta corrente que mantém o
     * gráfico em dia.
     *
     * Falha em silêncio de propósito: se o Yahoo estiver fora ou barrar o
     * servidor, os gráficos apenas voltam a se formar com o tempo, como já
     * faziam. Preencher o passado é uma melhoria, não um pré-requisito.
     *
     * @param janela a janela do Yahoo, ex.: "6mo", "1y"
     * @return quantos dias novos entraram
     */
    public int backfill(String janela) {
        try {
            String corpo = HttpUtil.get(String.format(URL_HISTORICO, janela));
            java.util.Map<LocalDate, Double> serie = parseHistorico(corpo);
            int novos = dao.gravarHistorico(serie);
            LOG.info("Histórico do açúcar preenchido: " + novos + " dia(s) novos de "
                    + serie.size() + " lidos do Yahoo.");
            return novos;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Não foi possível preencher o passado do açúcar (Yahoo)", e);
            return 0;
        }
    }

    /**
     * Extrai {dia -> fechamento} do JSON do Yahoo (chart/result/timestamp +
     * indicators/quote/close). Ignora pregões sem fechamento (o Yahoo manda
     * null nos feriados dentro da janela). Separado para poder ser testado
     * sem bater na rede.
     */
    static java.util.Map<LocalDate, Double> parseHistorico(String corpo) {
        JsonObject res = JsonParser.parseString(corpo).getAsJsonObject()
                .getAsJsonObject("chart")
                .getAsJsonArray("result").get(0).getAsJsonObject();
        var ts = res.getAsJsonArray("timestamp");
        var closes = res.getAsJsonObject("indicators")
                .getAsJsonArray("quote").get(0).getAsJsonObject()
                .getAsJsonArray("close");

        java.util.Map<LocalDate, Double> serie = new java.util.LinkedHashMap<>();
        for (int i = 0; i < ts.size(); i++) {
            if (closes.get(i).isJsonNull()) continue;   // pregão sem fechamento
            double c = closes.get(i).getAsDouble();
            if (c <= 0) continue;
            // O timestamp é o instante do pregão em UTC; a data do dia em
            // Nova York é o que interessa para o eixo.
            LocalDate dia = java.time.Instant.ofEpochSecond(ts.get(i).getAsLong())
                    .atZone(java.time.ZoneId.of("America/New_York")).toLocalDate();
            serie.put(dia, c);
        }
        return serie;
    }

    /** Um vencimento, ou null se já venceu / não respondeu. */
    private Vencimento consultar(String symbol, char mesCodigo, int ano) {
        try {
            String url = String.format(URL_SYMBOL,
                    URLEncoder.encode(symbol, StandardCharsets.UTF_8));
            JsonObject o = JsonParser.parseString(HttpUtil.get(url)).getAsJsonObject();

            // Símbolo vencido volta {"code":"symbol_not_exists",...}.
            if (o.has("code") || !o.has("close") || o.get("close").isJsonNull()) return null;
            double close = o.get("close").getAsDouble();
            if (close <= 0) return null;

            Vencimento v = new Vencimento();
            v.symbol = symbol;
            v.rotulo = mesPt(mesCodigo) + String.valueOf(ano).substring(2); // Out26
            v.descricao = o.has("description") && !o.get("description").isJsonNull()
                    ? o.get("description").getAsString() : symbol;
            v.expiracao = expiracao(o);
            v.ultimo   = close;
            v.abertura = num(o, "open");
            v.alta     = num(o, "high");
            v.baixa    = num(o, "low");
            // A variação do dia em centavos/libra (change absoluto), como o
            // painel antigo mostrava — seta e valor ao lado do vencimento.
            v.variacao = num(o, "change_abs");
            return v;
        } catch (Exception e) {
            // Um vencimento que não respondeu não derruba a coleta dos outros.
            LOG.log(Level.FINE, "Vencimento " + symbol + " não respondeu", e);
            return null;
        }
    }

    /** expiration vem como inteiro AAAAMMDD (ex.: 20260930). */
    private static LocalDate expiracao(JsonObject o) {
        if (!o.has("expiration") || o.get("expiration").isJsonNull()) return null;
        try {
            int d = o.get("expiration").getAsInt();
            return LocalDate.of(d / 10000, (d / 100) % 100, d % 100);
        } catch (Exception e) {
            return null;
        }
    }

    private static double num(JsonObject o, String campo) {
        if (!o.has(campo) || o.get(campo).isJsonNull()) return 0;
        try { return o.get(campo).getAsDouble(); }
        catch (Exception e) { return 0; }
    }
}
