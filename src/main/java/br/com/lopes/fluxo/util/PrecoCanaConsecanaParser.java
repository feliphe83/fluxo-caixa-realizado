package br.com.lopes.fluxo.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê a tabela de preço do kg de ATR do CONSECANA-AL/SE a partir do texto do
 * PDF que o sindicato publica todo mês.
 *
 * De cada publicação interessam quatro números e o mês a que se referem:
 *
 *  - o preço da matéria-prima (participação de 60% nos preços dos produtos),
 *    NO MÊS e ACUMULADO na safra — é o "bruto";
 *  - o preço líquido após as deduções legais (1,5%), NO MÊS e ACUMULADO — é
 *    o "líquido".
 *
 * O painel mostra uma linha por mês (o do mês) e uma linha "Acumulado" (a da
 * publicação mais recente). Por isso guardamos os dois de cada PDF.
 *
 * A leitura ancora em frases fixas do documento e pega os dois números
 * decimais logo depois — assim resiste a mudança de espaçamento na extração,
 * que é o que mais varia de um PDF para outro.
 */
public final class PrecoCanaConsecanaParser {

    private PrecoCanaConsecanaParser() {}

    public record Registro(int anomes, String rotulo,
                           double bruto, double liquido,
                           double acumBruto, double acumLiquido) {}

    // Chaves sem acento — a entrada é normalizada antes de procurar.
    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("JANEIRO", 1), Map.entry("FEVEREIRO", 2), Map.entry("MARCO", 3),
            Map.entry("ABRIL", 4), Map.entry("MAIO", 5), Map.entry("JUNHO", 6),
            Map.entry("JULHO", 7), Map.entry("AGOSTO", 8), Map.entry("SETEMBRO", 9),
            Map.entry("OUTUBRO", 10), Map.entry("NOVEMBRO", 11), Map.entry("DEZEMBRO", 12));

    private static final String[] ABREV =
            { "Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez" };

    private static final Pattern MES = Pattern.compile(
            "M[eê]s:\\s*([A-Z\\u00c0-\\u00dc]+)\\s*/\\s*(\\d{4})", Pattern.CASE_INSENSITIVE);
    // Dois decimais com vírgula logo após a âncora (tolera pontilhado e espaços).
    private static final Pattern DOIS_NUMEROS = Pattern.compile(
            "([0-9]{1,3},[0-9]{3,4})\\D+([0-9]{1,3},[0-9]{3,4})");

    /** Lê um PDF já convertido em texto. Lança se faltar algo essencial. */
    public static Registro ler(String texto) {
        String t = texto.replace('\u00a0', ' ');

        Matcher mMes = MES.matcher(t);
        if (!mMes.find()) throw new IllegalArgumentException(
                "não achei o mês da publicação (linha \"Mês: .../ANO\") — este PDF é do CONSECANA-AL?");
        Integer mes = MESES.get(semAcento(mMes.group(1)));
        if (mes == null) throw new IllegalArgumentException("mês não reconhecido: " + mMes.group(1));
        int ano = Integer.parseInt(mMes.group(2));

        double[] participacao = numerosApos(t, "PARTICIPA[ÇC][ÃA]O DA MAT[ÉE]RIA");
        double[] liquido = numerosApos(t, "AP[ÓO]S DEDU[ÇC][ÕO]ES");

        int anomes = ano * 100 + mes;
        String rotulo = ABREV[mes - 1] + "/" + ano;
        Registro r = new Registro(anomes, rotulo,
                participacao[0], liquido[0], participacao[1], liquido[1]);
        conferir(r);
        return r;
    }

    /**
     * Barreira contra parse errado: o líquido é o bruto menos 1,5% de
     * deduções, então tem de ficar entre ~97% e 100% dele, e os valores caem
     * numa faixa estreita de R$/kg ATR. Se algo disso não bate, o mais
     * provável é que a extração tenha pego um número da tabela em vez do da
     * linha certa — melhor recusar com uma mensagem do que gravar errado.
     */
    private static void conferir(Registro r) {
        confereProporcao(r.bruto(), r.liquido(), "do mês");
        confereProporcao(r.acumBruto(), r.acumLiquido(), "acumulado");
    }

    private static void confereProporcao(double bruto, double liquido, String qual) {
        if (bruto < 0.3 || bruto > 5.0 || liquido < 0.3 || liquido > 5.0) {
            throw new IllegalArgumentException("valores " + qual
                    + " fora da faixa esperada (bruto " + bruto + ", líquido " + liquido
                    + ") — a leitura do PDF pode ter pego a coluna errada");
        }
        double razao = liquido / bruto;
        if (razao < 0.96 || razao > 1.0) {
            throw new IllegalArgumentException("líquido " + qual + " não bate com o bruto após as "
                    + "deduções (bruto " + bruto + ", líquido " + liquido + ") — leitura suspeita");
        }
    }

    /** Os dois números que vêm depois da âncora (no mês, acumulado). */
    private static double[] numerosApos(String texto, String ancoraRegex) {
        Matcher a = Pattern.compile(ancoraRegex, Pattern.CASE_INSENSITIVE).matcher(texto);
        if (!a.find()) throw new IllegalArgumentException(
                "não achei a linha esperada (" + ancoraRegex + ") no PDF");
        Matcher n = DOIS_NUMEROS.matcher(texto).region(a.end(), texto.length());
        if (!n.find()) throw new IllegalArgumentException(
                "achei a linha mas não os dois valores (no mês e acumulado) depois dela");
        return new double[]{ numero(n.group(1)), numero(n.group(2)) };
    }

    private static double numero(String s) {
        return Double.parseDouble(s.trim().replace(".", "").replace(',', '.'));
    }

    private static String semAcento(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase().trim();
    }
}
