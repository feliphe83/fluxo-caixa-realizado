package br.com.lopes.fluxo.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decodifica a chave de acesso da NF-e (44 dígitos) a partir do texto de um
 * PDF de DANFE.
 *
 * A chave é preferível a tentar reconhecer rótulos como "Nº"/"Série" no texto
 * do DANFE: o layout do documento muda de impressora fiscal para impressora
 * fiscal (cada fornecedor imprime o seu), mas a chave é um dado embutido, em
 * posições fixas, sempre nos mesmos 44 dígitos — e ela sozinha já contém o
 * CNPJ do emitente, a série e o número da nota, sem precisar achar rótulo
 * nenhum:
 *
 *   cUF(2) AAMM(4) CNPJ(14) mod(2) série(3) número(9) tpEmis(1) cNF(8) DV(1)
 *   posição:   0-1   2-5      6-19  20-21   22-24     25-33     34     35-42 43
 *
 * Antes de aceitar uma sequência de 44 dígitos como chave de verdade, o
 * dígito verificador (módulo 11) é conferido — sem isso, qualquer número
 * comprido no PDF (código de barras de boleto, protocolo, etc.) poderia ser
 * confundido com a chave.
 */
public final class NfeChaveUtil {

    private NfeChaveUtil() {}

    /** A chave como um bloco só de 44 dígitos (comum na legenda do código de barras). */
    private static final Pattern CHAVE_COMPACTA = Pattern.compile("\\d{44}");
    /** A chave impressa em 11 blocos de 4 dígitos, separados por espaço/ponto/quebra de linha. */
    private static final Pattern CHAVE_EM_BLOCOS = Pattern.compile("(?:\\d{4}[ \\t.\\r\\n]{1,3}){10}\\d{4}");

    public static final class Chave {
        public final String chave44;
        public final String cnpjEmitente;
        public final String serie;
        public final String numero;

        private Chave(String chave44) {
            this.chave44 = chave44;
            this.cnpjEmitente = chave44.substring(6, 20);
            this.serie = String.valueOf(Long.parseLong(chave44.substring(22, 25)));
            this.numero = String.valueOf(Long.parseLong(chave44.substring(25, 34)));
        }
    }

    /**
     * Procura no texto todas as sequências de 44 dígitos candidatas (soltas
     * ou em blocos de 4) e devolve a primeira cujo dígito verificador bate.
     * Null se nenhuma candidata for uma chave válida.
     */
    public static Chave extrair(String texto) {
        if (texto == null || texto.isEmpty()) return null;
        for (String candidata : candidatas(texto)) {
            if (dvValido(candidata)) return new Chave(candidata);
        }
        return null;
    }

    private static List<String> candidatas(String texto) {
        List<String> lista = new ArrayList<>();
        Matcher compacta = CHAVE_COMPACTA.matcher(texto);
        while (compacta.find()) lista.add(compacta.group());
        Matcher blocos = CHAVE_EM_BLOCOS.matcher(texto);
        while (blocos.find()) lista.add(blocos.group().replaceAll("[^0-9]", ""));
        return lista;
    }

    /** Módulo 11: pesos 2..9 (repetindo) da direita para a esquerda sobre os 43 primeiros dígitos. */
    static boolean dvValido(String chave44) {
        if (chave44.length() != 44) return false;
        int soma = 0, peso = 2;
        for (int i = 42; i >= 0; i--) {
            soma += (chave44.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        int dv = resto < 2 ? 0 : 11 - resto;
        return dv == (chave44.charAt(43) - '0');
    }

    /**
     * Indícios de que um PDF é um DANFE mesmo quando não foi possível achar
     * (ou validar) a chave dentro dele — típico de PDF escaneado, sem camada
     * de texto. Serve para não descartar silenciosamente um anexo que parece
     * ser nota fiscal: a tela mostra esses casos como "sem chave lida", para
     * conferência manual.
     */
    public static boolean pareceDanfe(String texto, String nomeArquivo) {
        String t = texto == null ? "" : texto.toUpperCase();
        if (t.contains("DANFE") || t.contains("NOTA FISCAL ELETR") || t.contains("CHAVE DE ACESSO")) return true;
        String nome = nomeArquivo == null ? "" : nomeArquivo.toUpperCase();
        return t.isBlank() && (nome.contains("NF") || nome.contains("DANFE") || nome.contains("NOTA"));
    }
}
