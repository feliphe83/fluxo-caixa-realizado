package br.com.lopes.fluxo.agendamento;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formatação dos valores que vão no texto das mensagens de WhatsApp.
 *
 * Existe porque as consultas do ERP passam por
 * {@link br.com.lopes.fluxo.util.RowMapperUtil}, que devolve datas já
 * convertidas para texto ISO ("2026-04-17T00:00:00"). Jogar esse valor
 * direto na mensagem produz uma data ilegível — {@link #data} reconhece o
 * formato e devolve dd/MM/yyyy.
 */
final class FormatoMensagem {

    /** Sem o símbolo: o "R$" já está escrito no texto das mensagens. */
    private static final NumberFormat VALOR = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));
    private static final NumberFormat QUANTIDADE = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));
    static {
        VALOR.setMinimumFractionDigits(2);
        VALOR.setMaximumFractionDigits(2);
    }

    private FormatoMensagem() {}

    static String texto(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    static double numero(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    /** Duas casas, separador pt-BR: 1234.5 -> "1.234,50". */
    static String valor(Object v) {
        return VALOR.format(numero(v));
    }

    /** Sem casas fixas: 15000 -> "15.000"; 1.5 -> "1,5". */
    static String quantidade(Object v) {
        return QUANTIDADE.format(numero(v));
    }

    /**
     * dd/MM/yyyy a partir do texto ISO que o RowMapperUtil produz
     * ("2026-04-17" ou "2026-04-17T00:00:00"). Qualquer outra coisa volta
     * como está — inclusive vazio, quando a consulta não trouxe data.
     */
    static String data(Object v) {
        String s = texto(v);
        if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
            String d = s.substring(8, 10), m = s.substring(5, 7), a = s.substring(0, 4);
            if (ehNumero(a) && ehNumero(m) && ehNumero(d)) return d + "/" + m + "/" + a;
        }
        return s;
    }

    private static boolean ehNumero(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}
