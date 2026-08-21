package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.HttpUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * As fontes externas do painel de indicadores econômicos.
 *
 * ── POR QUE PELO SERVIDOR, E NÃO PELO NAVEGADOR ──
 *
 * O painel que serviu de modelo busca tudo do navegador. Isso tem três
 * problemas que o servidor resolve:
 *
 *  1. as chaves de API ficavam à vista no código-fonte da página — qualquer
 *     um que abrisse o HTML as levava;
 *  2. um cache só serve a TV e todos os computadores; do navegador, cada
 *     tela aberta é mais uma batida na fonte, e fonte gratuita tem limite;
 *  3. a TV da parede pode não ter internet no cliente. Pelo servidor, ela
 *     mostra o mesmo número que todo mundo.
 *
 * ── CADA FONTE CAI SOZINHA ──
 *
 * Uma fonte fora do ar não pode derrubar o painel: cada uma é buscada e
 * guardada separadamente, e o que falhou vai no JSON como erro, ao lado do
 * que deu certo. Num painel de indicadores, meia tela com dado bom é útil;
 * tela em branco não é.
 *
 * O cache guarda o ÚLTIMO SUCESSO. Se a fonte cair, o painel continua
 * mostrando o número de antes, dizendo de quando ele é — melhor do que um
 * buraco, desde que a idade esteja escrita.
 */
public class IndicadoresEconomicosDAO {

    private static final Logger LOG = Logger.getLogger(IndicadoresEconomicosDAO.class.getName());

    /** Cotação muda o dia inteiro; índice do BCB e CEPEA, uma vez por dia. */
    private static final long TTL_COTACAO = 10 * 60 * 1000L;
    private static final long TTL_DIARIO  = 3 * 60 * 60 * 1000L;
    /** Notícia não muda de minuto a minuto; meia hora chega e poupa a fonte. */
    private static final long TTL_NOTICIAS = 30 * 60 * 1000L;

    /** Manchetes recentes do Google Notícias em português (%s = a busca). */
    private static final String URL_NOTICIAS =
        "https://news.google.com/rss/search?q=%s&hl=pt-BR&gl=BR&ceid=BR:pt-419";

    private static final String URL_DOLAR =
        "https://economia.awesomeapi.com.br/json/last/USD-BRL";
    private static final String URL_DOLAR_HIST =
        "https://api.frankfurter.app/%s..%s?from=USD&to=BRL";
    private static final String URL_BCB =
        "https://api.bcb.gov.br/dados/serie/bcdata.sgs.%d/dados/ultimos/%d?formato=json";
    /**
     * A série da poupança (195) NÃO aceita "ultimos/N" — devolve 400 para
     * qualquer N, conferido de 100 a 380. Ela só responde por intervalo de
     * datas, porque é uma série de aniversário: um rendimento por dia do mês.
     */
    private static final String URL_BCB_PERIODO =
        "https://api.bcb.gov.br/dados/serie/bcdata.sgs.%d/dados"
      + "?formato=json&dataInicial=%s&dataFinal=%s";
    private static final String URL_CEPEA =
        "https://www.cepea.org.br/br/widgetproduto.js.php"
      + "?id_indicador%5B%5D=208&id_indicador%5B%5D=209";

    /**
     * O açúcar NY nº 11 não está aqui.
     *
     * O painel de modelo usava a financialmodelingprep (chaves mortas hoje) e
     * depois um serviço interno da usina que só responde de dentro da rede.
     * Agora o açúcar vem do MySQL: um coletor busca os vencimentos na bolsa e
     * grava (CotacaoAcucarColetor); ver acucar()/acucarDiario()/acucarMensal() mais abaixo.
     */

    // ── Cache ─────────────────────────────────────────────────────────────

    private record NoCache(JsonElement valor, long quando, String erro) {}

    private static final Map<String, NoCache> CACHE = new ConcurrentHashMap<>();

    private interface Busca { JsonElement executar() throws Exception; }

    /**
     * Busca com cache e queda para o último sucesso.
     *
     * @return {dado, idadeMinutos, erro} — erro preenchido quando a fonte
     *         falhou, mesmo que ainda haja dado velho para mostrar.
     */
    private JsonObject comCache(String chave, long ttl, Busca busca) {
        NoCache atual = CACHE.get(chave);
        long agora = System.currentTimeMillis();

        if (atual != null && atual.erro() == null && (agora - atual.quando()) < ttl) {
            return envelope(atual.valor(), agora - atual.quando(), null);
        }
        try {
            JsonElement novo = busca.executar();
            CACHE.put(chave, new NoCache(novo, agora, null));
            return envelope(novo, 0, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Fonte " + chave + " falhou", e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            // Guarda o dado velho, mas anota o erro: quem lê tem de saber
            // que aquilo não veio agora.
            if (atual != null && atual.valor() != null) {
                return envelope(atual.valor(), agora - atual.quando(), msg);
            }
            return envelope(null, 0, msg);
        }
    }

    private static JsonObject envelope(JsonElement dado, long idadeMs, String erro) {
        JsonObject o = new JsonObject();
        o.add("dado", dado);
        o.addProperty("idadeMinutos", Math.round(idadeMs / 60000.0));
        if (erro != null) o.addProperty("erro", erro);
        return o;
    }

    // ── Dólar ─────────────────────────────────────────────────────────────

    public JsonObject dolar() {
        return comCache("dolar", TTL_COTACAO, () -> {
            JsonObject raiz = JsonParser.parseString(HttpUtil.get(URL_DOLAR)).getAsJsonObject();
            JsonObject d = raiz.getAsJsonObject("USDBRL");
            JsonObject o = new JsonObject();
            o.addProperty("data", texto(d, "create_date"));
            o.addProperty("ultimo",   numero(d, "bid"));
            o.addProperty("abertura", numero(d, "ask"));
            o.addProperty("alta",     numero(d, "high"));
            o.addProperty("baixa",    numero(d, "low"));
            o.addProperty("variacao", numero(d, "pctChange"));
            return o;
        });
    }

    /** Série do dólar dos últimos dias, para o gráfico. */
    public JsonObject dolarHistorico(int dias) {
        return comCache("dolarHist" + dias, TTL_COTACAO, () -> {
            java.time.LocalDate fim = java.time.LocalDate.now();
            java.time.LocalDate ini = fim.minusDays(dias);
            String url = String.format(URL_DOLAR_HIST, ini, fim);
            JsonObject raiz = JsonParser.parseString(HttpUtil.get(url)).getAsJsonObject();
            JsonObject rates = raiz.getAsJsonObject("rates");

            JsonArray arr = new JsonArray();
            // TreeMap pela chave ISO: a data em texto ordena sozinha em ordem
            // cronológica, e a API não promete a ordem do objeto.
            for (String dia : new java.util.TreeSet<>(rates.keySet())) {
                JsonObject p = new JsonObject();
                p.addProperty("data", dia);
                p.addProperty("valor", rates.getAsJsonObject(dia).get("BRL").getAsDouble());
                arr.add(p);
            }
            return arr;
        });
    }

    /**
     * O dólar por mês, últimos {@code meses} meses — um ponto por mês, o
     * fechamento do último pregão de cada um. O mês corrente entra com a
     * cotação mais recente que houver dele.
     */
    public JsonObject dolarMensal(int meses) {
        return comCache("dolarMensal" + meses, TTL_COTACAO, () -> {
            java.time.LocalDate fim = java.time.LocalDate.now();
            java.time.LocalDate ini = fim.minusMonths(meses - 1L).withDayOfMonth(1);
            String url = String.format(URL_DOLAR_HIST, ini, fim);
            JsonObject rates = JsonParser.parseString(HttpUtil.get(url))
                    .getAsJsonObject().getAsJsonObject("rates");

            // Última cotação de cada mês: percorrendo as datas em ordem, o
            // valor de cada mês vai sendo sobrescrito até sobrar o do fim.
            java.util.TreeMap<Integer, Double> porMes = new java.util.TreeMap<>();
            for (String dia : new java.util.TreeSet<>(rates.keySet())) {
                java.time.LocalDate d = java.time.LocalDate.parse(dia);
                int anoMes = d.getYear() * 100 + d.getMonthValue();
                porMes.put(anoMes, rates.getAsJsonObject(dia).get("BRL").getAsDouble());
            }
            JsonArray arr = new JsonArray();
            for (Map.Entry<Integer, Double> e : porMes.entrySet()) {
                JsonObject p = new JsonObject();
                p.addProperty("anoMes", e.getKey());
                p.addProperty("valor", e.getValue());
                arr.add(p);
            }
            return arr;
        });
    }

    // ── Indicadores do Banco Central ──────────────────────────────────────

    /** Código da série no SGS e como o painel a chama. */
    private static final Object[][] SERIES = {
        {  433, "IPCA"     },
        {  188, "INPC"     },
        {  189, "IGP-M"    },
        { 7453, "INCC"     },
    };

    public JsonObject indicadores() {
        return comCache("indicadores", TTL_DIARIO, () -> {
            JsonArray arr = new JsonArray();
            for (Object[] s : SERIES) {
                int codigo = (int) s[0];
                String nome = (String) s[1];
                List<double[]> meses = serieMensal(codigo, 13);
                if (meses.isEmpty()) continue;
                arr.add(linhaIndicador(nome, meses));
            }
            JsonObject poupanca = poupanca();
            if (poupanca != null) arr.add(poupanca);
            return arr;
        });
    }

    /** @return lista de {anoMes, valorPercentual}, da mais antiga para a mais nova. */
    private List<double[]> serieMensal(int codigo, int quantos) throws Exception {
        JsonArray dados = JsonParser.parseString(
                HttpUtil.get(String.format(URL_BCB, codigo, quantos))).getAsJsonArray();
        List<double[]> out = new ArrayList<>();
        for (JsonElement el : dados) {
            JsonObject o = el.getAsJsonObject();
            String[] p = o.get("data").getAsString().split("/");
            if (p.length < 3) continue;
            double anoMes = Integer.parseInt(p[2]) * 100 + Integer.parseInt(p[1]);
            out.add(new double[]{ anoMes, Double.parseDouble(o.get("valor").getAsString().replace(',', '.')) });
        }
        return out;
    }

    /**
     * Uma linha da tabela: mês, acumulado no ano e em 12 meses.
     *
     * O acumulado é COMPOSTO, não somado: 1% em janeiro e 1% em fevereiro
     * dão 2,01% no ano, não 2%. Somar índice de preço é o erro mais comum
     * desse tipo de tabela, e a diferença cresce com a inflação.
     */
    private JsonObject linhaIndicador(String nome, List<double[]> meses) {
        double[] ultimo = meses.get(meses.size() - 1);
        int ano = (int) (ultimo[0] / 100);

        double noAno = 1, em12 = 1;
        for (double[] m : meses) if ((int) (m[0] / 100) == ano) noAno *= 1 + m[1] / 100;
        // Doze meses, e não a lista inteira: a consulta pede 13 para o caso
        // de a série já ter publicado o mês corrente.
        int desde = Math.max(0, meses.size() - 12);
        for (int i = desde; i < meses.size(); i++) em12 *= 1 + meses.get(i)[1] / 100;

        JsonObject o = new JsonObject();
        o.addProperty("nome", nome);
        o.addProperty("anoMes", (int) ultimo[0]);
        o.addProperty("noMes", arred(ultimo[1]));
        o.addProperty("noAno", arred((noAno - 1) * 100));
        o.addProperty("em12Meses", arred((em12 - 1) * 100));
        return o;
    }

    /**
     * Poupança: a série 195 é DIÁRIA (o rendimento do mês que começa naquele
     * dia), então o acumulado sai pegando uma entrada por mês. Sem isso, os
     * trinta valores de um mesmo mês entrariam todos no composto e o ano
     * apareceria com um rendimento absurdo.
     */
    private JsonObject poupanca() {
        try {
            java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            java.time.LocalDate fim = java.time.LocalDate.now();
            java.time.LocalDate ini = fim.minusMonths(13).withDayOfMonth(1);
            JsonArray dados = JsonParser.parseString(HttpUtil.get(
                    String.format(URL_BCB_PERIODO, 195, ini.format(fmt), fim.format(fmt))))
                    .getAsJsonArray();
            Map<Integer, Double> porMes = new LinkedHashMap<>();
            for (JsonElement el : dados) {
                JsonObject o = el.getAsJsonObject();
                String[] p = o.get("data").getAsString().split("/");
                if (p.length < 3) continue;
                int anoMes = Integer.parseInt(p[2]) * 100 + Integer.parseInt(p[1]);
                // A primeira entrada de cada mês é a que vale; as seguintes
                // são o mesmo rendimento começando em outro dia.
                porMes.putIfAbsent(anoMes, Double.parseDouble(o.get("valor").getAsString().replace(',', '.')));
            }
            List<double[]> meses = new ArrayList<>();
            new java.util.TreeSet<>(porMes.keySet())
                    .forEach(k -> meses.add(new double[]{ k, porMes.get(k) }));
            if (meses.isEmpty()) return null;
            // Só os 12 últimos: o intervalo pede 13 meses para garantir que o
            // primeiro do ano corrente esteja inteiro na lista.
            if (meses.size() > 12) meses.subList(0, meses.size() - 12).clear();
            return linhaIndicador("Poupança", meses);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Poupança indisponível", e);
            return null;
        }
    }

    // ── CEPEA ─────────────────────────────────────────────────────────────

    private static final Pattern LINHA_CEPEA =
        Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern CELULA_CEPEA =
        Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    /**
     * Etanol anidro e hidratado de Alagoas, do widget do CEPEA.
     *
     * O widget devolve JavaScript com um document.write de uma tabela HTML —
     * não há API. Lê-se o que veio, e por isso a extração é tolerante: se o
     * formato mudar, a lista vem vazia e o painel mostra o resto.
     */
    public JsonObject cepea() {
        return comCache("cepea", TTL_DIARIO, () -> {
            String corpo = HttpUtil.get(URL_CEPEA);
            JsonArray arr = new JsonArray();
            Matcher mLinha = LINHA_CEPEA.matcher(corpo);
            while (mLinha.find()) {
                List<String> celulas = new ArrayList<>();
                Matcher mCel = CELULA_CEPEA.matcher(mLinha.group(1));
                while (mCel.find()) celulas.add(limpar(mCel.group(1)));
                if (celulas.size() < 3) continue;

                String valorTexto = celulas.get(2).replace("R$", "").trim()
                        .replace(".", "").replace(",", ".");
                double valor;
                try { valor = Double.parseDouble(valorTexto); }
                catch (NumberFormatException e) { continue; }

                JsonObject o = new JsonObject();
                o.addProperty("data", celulas.get(0));
                o.addProperty("produto", celulas.get(1).replaceAll("\\s+", " ").trim());
                o.addProperty("valor", valor);
                arr.add(o);
            }
            return arr;
        });
    }

    private static String limpar(String html) {
        return html.replaceAll("<[^>]+>", " ")
                   .replace("&nbsp;", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    // ── Açúcar NY nº 11 ───────────────────────────────────────────────────
    //
    // Vem do MySQL, não mais de uma fonte HTTP na hora. Um coletor busca os
    // vencimentos na bolsa de tempos em tempos e grava o retrato no banco
    // (ver CotacaoAcucarColetor / CotacaoAcucarScheduler); aqui só se lê. Por
    // isso NÃO passa pelo cache de memória das fontes externas: a leitura já
    // é barata e local, e a idade que importa é a da COLETA — quando a bolsa
    // foi consultada —, não a de quando esta página bateu no banco. Essa
    // idade vem gravada junto e é o que o envelope carrega.

    private final CotacaoAcucarDAO cotacaoAcucar = new CotacaoAcucarDAO();

    public JsonObject acucar() {
        try {
            CotacaoAcucarDAO.Retrato r = cotacaoAcucar.lerVencimentos();
            if (r == null) {
                return envelope(null, 0,
                        "a cotação do açúcar ainda não foi coletada");
            }
            return envelope(r.dado(), r.idadeMinutos() * 60000L, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Não foi possível ler a cotação do açúcar", e);
            return envelope(null, 0, mensagem(e));
        }
    }

    /** Fechamento diário do primeiro vencimento, últimos {@code dias} pregões. */
    public JsonObject acucarDiario(int dias) {
        try {
            JsonArray hist = cotacaoAcucar.lerDiario(dias);
            if (hist.size() == 0) {
                return envelope(null, 0,
                        "o histórico diário do açúcar ainda está sendo formado");
            }
            return envelope(hist, 0, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Não foi possível ler o histórico diário do açúcar", e);
            return envelope(null, 0, mensagem(e));
        }
    }

    /** Fechamento mensal do primeiro vencimento, últimos {@code meses} meses. */
    public JsonObject acucarMensal(int meses) {
        try {
            JsonArray hist = cotacaoAcucar.lerMensal(meses);
            if (hist.size() == 0) {
                return envelope(null, 0,
                        "o histórico mensal do açúcar ainda está sendo formado");
            }
            return envelope(hist, 0, null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Não foi possível ler o histórico mensal do açúcar", e);
            return envelope(null, 0, mensagem(e));
        }
    }

    private static String mensagem(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    // ── Notícias (Google Notícias RSS) ────────────────────────────────────
    //
    // Duas buscas, açúcar e dólar, cada uma virando uma lista de manchetes
    // recentes com veículo, link e há quanto tempo saíram. É de graça e sem
    // chave; o cache de meia hora serve a TV e todos os computadores de uma
    // batida só. Se o Google não responder, o painel mostra o resto — uma
    // parede sem a coluna de notícias ainda informa preço.

    private static final Pattern ITEM_RSS = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL);
    private static final Pattern TITULO_RSS = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL);
    private static final Pattern LINK_RSS = Pattern.compile("<link>(.*?)</link>", Pattern.DOTALL);
    private static final Pattern DATA_RSS = Pattern.compile("<pubDate>(.*?)</pubDate>", Pattern.DOTALL);
    private static final Pattern FONTE_RSS = Pattern.compile("<source[^>]*>(.*?)</source>", Pattern.DOTALL);

    public JsonObject noticias() {
        return comCache("noticias", TTL_NOTICIAS, () -> {
            JsonObject o = new JsonObject();
            // Buscas AMPLAS de propósito: cotação de câmbio rende matéria toda
            // hora, e o feed estreito ("dólar real câmbio") trazia notícia de
            // ontem. Com o termo aberto e ordenando pela mais nova, o painel
            // mostra o que saiu hoje.
            o.add("acucar", manchetes("açúcar cotação OR preço OR safra", 6));
            // "dólar real": o par de moedas mantém no câmbio do Brasil (sem
            // "dólar cotação", que trazia matéria de mercado estrangeiro) e
            // tem volume para render notícia de poucas horas atrás.
            o.add("dolar",  manchetes("dólar real", 6));
            return o;
        });
    }

    private JsonArray manchetes(String busca, int limite) throws Exception {
        String url = String.format(URL_NOTICIAS,
                java.net.URLEncoder.encode(busca, java.nio.charset.StandardCharsets.UTF_8));
        String xml = HttpUtil.get(url);

        // Lê TODAS as manchetes e ordena pela mais recente — o feed não vem
        // em ordem de tempo, então pegar as primeiras trazia notícia velha.
        List<JsonObject> itens = new ArrayList<>();
        Matcher mItem = ITEM_RSS.matcher(xml);
        while (mItem.find()) {
            String item = mItem.group(1);
            String titulo = grupo(TITULO_RSS, item);
            if (titulo.isEmpty()) continue;

            // O título vem "Manchete - Veículo"; o veículo também está na tag
            // <source>. Prefiro a tag; na falta, corto pelo último " - ".
            String veiculo = grupo(FONTE_RSS, item);
            if (veiculo.isEmpty()) {
                int corte = titulo.lastIndexOf(" - ");
                if (corte > 0) { veiculo = titulo.substring(corte + 3); titulo = titulo.substring(0, corte); }
            } else {
                String sufixo = " - " + veiculo;
                if (titulo.endsWith(sufixo)) titulo = titulo.substring(0, titulo.length() - sufixo.length());
            }

            JsonObject n = new JsonObject();
            n.addProperty("titulo", limparTexto(titulo));
            n.addProperty("veiculo", limparTexto(veiculo));
            n.addProperty("link", grupo(LINK_RSS, item).trim());
            n.addProperty("idadeMinutos", idadeDe(grupo(DATA_RSS, item)));
            itens.add(n);
        }
        itens.sort(java.util.Comparator.comparingLong(x -> x.get("idadeMinutos").getAsLong()));

        // Uma mesma matéria sai em vários veículos com o título idêntico —
        // depois de ordenar pela mais nova, fica só a primeira de cada título.
        JsonArray arr = new JsonArray();
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (JsonObject n : itens) {
            if (arr.size() >= limite) break;
            String chave = n.get("titulo").getAsString().toLowerCase();
            if (vistos.add(chave)) arr.add(n);
        }
        return arr;
    }

    private static String grupo(Pattern p, String texto) {
        Matcher m = p.matcher(texto);
        return m.find() ? m.group(1) : "";
    }

    /** "Wed, 19 Aug 2026 08:00:09 GMT" -> minutos desde então (0 se não parsear). */
    private static long idadeDe(String pubDate) {
        try {
            java.time.ZonedDateTime dt = java.time.ZonedDateTime.parse(pubDate.trim(),
                    java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
            long min = java.time.Duration.between(dt, java.time.ZonedDateTime.now()).toMinutes();
            return Math.max(0, min);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String limparTexto(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&#39;", "'").replace("&quot;", "\"").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").trim();
    }

    // ── Utilidades ────────────────────────────────────────────────────────

    private static String texto(JsonObject o, String campo) {
        JsonElement e = o.get(campo);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static double numero(JsonObject o, String campo) {
        try { return Double.parseDouble(texto(o, campo).replace(',', '.')); }
        catch (NumberFormatException e) { return 0; }
    }

    private static double arred(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
