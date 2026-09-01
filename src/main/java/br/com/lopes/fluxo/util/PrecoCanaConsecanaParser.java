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
    // Um número: 1-3 dígitos, vírgula, 3-4 dígitos — tolerando até 2 espaços
    // em CADA junção entre caracteres. Mesmo motivo da âncora compactada: a
    // linha de "líquido após deduções" sai em negrito, mais espaçada, e essa
    // linha inclui os dois números — então eles também podem vir com espaço
    // entre dígito e dígito ("1 , 2 9 2 2"), não só a palavra do rótulo.
    private static final String UM_NUMERO =
            "[0-9](?:\\s{0,2}[0-9]){0,2}\\s{0,2},\\s{0,2}[0-9](?:\\s{0,2}[0-9]){2,3}";
    // \D+? (preguiçoso, não guloso): para no primeiro número válido depois
    // do primeiro, em vez de arriscar pular pra um número mais distante.
    private static final Pattern DOIS_NUMEROS = Pattern.compile(
            "(" + UM_NUMERO + ")\\D+?(" + UM_NUMERO + ")");

    // As duas linhas em que "numerosApos" ancora — sem espaço nenhum (ver
    // por quê em numerosApos/compactar): a extração de PDF às vezes imprime
    // essas linhas (que no documento saem em negrito, mais espaçadas) com um
    // espaço entre CADA letra ("A P Ó S"), não só entre palavras.
    private static final String ANCORA_PARTICIPACAO = "PARTICIPACAODAMATERIA";
    private static final String ANCORA_LIQUIDO = "APOSDEDUCOES";

    /** Lê um PDF já convertido em texto. Lança se faltar algo essencial. */
    public static Registro ler(String texto) {
        String t = texto.replace('\u00a0', ' ');

        Matcher mMes = MES.matcher(t);
        if (!mMes.find()) throw new IllegalArgumentException(
                "não achei o mês da publicação (linha \"Mês: .../ANO\") — este PDF é do CONSECANA-AL?");
        Integer mes = MESES.get(semAcento(mMes.group(1)));
        if (mes == null) throw new IllegalArgumentException("mês não reconhecido: " + mMes.group(1));
        int ano = Integer.parseInt(mMes.group(2));

        double[] participacao = numerosApos(t, ANCORA_PARTICIPACAO);
        double[] liquido = numerosApos(t, ANCORA_LIQUIDO);

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

    /**
     * Os dois números que vêm depois da âncora (no mês, acumulado).
     *
     * A âncora é achada num texto COMPACTADO (maiúsculas, sem acento, sem
     * espaço nenhum — nem entre palavras, nem dentro delas): essas duas
     * linhas saem em negrito no PDF, com as letras mais espaçadas do que o
     * resto do documento, e pelo menos uma extração já reproduziu isso
     * como um espaço entre CADA letra ("A P Ó S D E D U Ç Õ E S") — um
     * `\s*` entre palavras não pega esse caso, porque o espaço está dentro
     * da própria palavra. Compactando tudo (tira espaço e acento antes de
     * comparar) a forma como o extrator espaçou deixa de importar.
     *
     * Achada a âncora no texto compactado, o índice mapeado por
     * {@link #compactar} devolve a posição correspondente no texto
     * ORIGINAL, e é dali que os dois números são procurados — os números
     * em si não sofrem esse espaçamento extra, então continuam sendo lidos
     * do jeito de sempre.
     */
    private static double[] numerosApos(String texto, String ancoraSemEspaco) {
        Compacto c = compactar(texto);
        int pos = c.texto().indexOf(ancoraSemEspaco);
        if (pos < 0) throw new IllegalArgumentException(
                "não achei a linha esperada (" + ancoraSemEspaco + ") no PDF");
        int inicioNoOriginal = c.mapa()[pos];
        int fimNoOriginal = c.mapa()[pos + ancoraSemEspaco.length() - 1] + 1;

        Matcher n = DOIS_NUMEROS.matcher(texto).region(fimNoOriginal, texto.length());
        if (!n.find()) {
            // Duas rodadas de regex já tentaram adivinhar o espaçamento e
            // não bastou — em vez de arriscar um terceiro palpite, mostra o
            // texto de verdade que a extração do PDF produziu ali (antes e
            // depois da âncora), pra decidir com o dado real na mão.
            throw new IllegalArgumentException(
                    "achei a linha mas não os dois valores (no mês e acumulado) depois dela — texto extraído ali: "
                    + trechoVisivel(texto, inicioNoOriginal, fimNoOriginal));
        }
        return new double[]{ numero(n.group(1)), numero(n.group(2)) };
    }

    /** Um trecho do texto original em volta de [inicio, fim), com quebra de linha e espaço tornados visíveis, pra caber numa mensagem de erro. */
    private static String trechoVisivel(String texto, int inicio, int fim) {
        int de = Math.max(0, inicio - 60);
        int ate = Math.min(texto.length(), fim + 240);
        String bruto = texto.substring(de, ate);
        String visivel = bruto.replace("\\", "\\\\").replace("\n", "⏎").replace("\t", "→");
        return "\"" + visivel + "\"";
    }

    /** texto: maiúsculas, sem acento, sem espaço. mapa[i] = posição no texto ORIGINAL do caractere que está em texto.charAt(i). */
    private record Compacto(String texto, int[] mapa) {}

    private static Compacto compactar(String original) {
        // NFD decompõe cada acentuada em base + marca de combinação — e como
        // é sempre uma marca só nos acentos deste texto (Ç, Ã, Õ, É, Ó...), o
        // índice de cada caractere não muda: dá pra usar a posição em
        // "semAcentoMesmoTamanho" como se fosse a posição no original.
        String semAcentoMesmoTamanho = java.text.Normalizer.normalize(original, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase();
        StringBuilder sb = new StringBuilder(semAcentoMesmoTamanho.length());
        int[] mapa = new int[semAcentoMesmoTamanho.length()];
        int n = 0;
        for (int i = 0; i < semAcentoMesmoTamanho.length(); i++) {
            char ch = semAcentoMesmoTamanho.charAt(i);
            if (Character.isWhitespace(ch)) continue;
            sb.append(ch);
            mapa[n++] = i;
        }
        return new Compacto(sb.toString(), java.util.Arrays.copyOf(mapa, n));
    }

    private static double numero(String s) {
        // Tira todo espaço primeiro — UM_NUMERO agora aceita espaço entre
        // dígitos ("1 , 2 9 2 2"), e Double.parseDouble não aceita.
        String semEspaco = s.replaceAll("\\s+", "");
        return Double.parseDouble(semEspaco.replace(".", "").replace(',', '.'));
    }

    private static String semAcento(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase().trim();
    }
}
