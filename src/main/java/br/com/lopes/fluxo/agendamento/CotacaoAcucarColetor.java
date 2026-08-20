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
