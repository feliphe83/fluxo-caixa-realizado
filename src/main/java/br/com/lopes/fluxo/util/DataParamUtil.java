package br.com.lopes.fluxo.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalização dos parâmetros de data das ferramentas do chat: o agente de
 * IA (e o usuário brasileiro) pode mandar a data em vários formatos; os DAOs
 * esperam sempre yyyy-MM-dd.
 */
public final class DataParamUtil {

    private static final Pattern ISO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern BR  = Pattern.compile("^(\\d{2})[/.-](\\d{2})[/.-](\\d{4})$");
    private static final Pattern BR_COMPACTA = Pattern.compile("^(\\d{2})(\\d{2})(\\d{4})$");

    private DataParamUtil() {}

    /**
     * Aceita yyyy-MM-dd, dd/MM/yyyy, dd-MM-yyyy, dd.MM.yyyy e ddMMyyyy.
     *
     * @return a data em yyyy-MM-dd; null se vazio ou formato não reconhecido
     */
    public static String normalizar(String data) {
        if (data == null) return null;
        String d = data.trim();
        if (d.isEmpty()) return null;

        if (ISO.matcher(d).matches()) return d;

        Matcher br = BR.matcher(d);
        if (br.matches()) return br.group(3) + "-" + br.group(2) + "-" + br.group(1);

        Matcher compacta = BR_COMPACTA.matcher(d);
        if (compacta.matches()) return compacta.group(3) + "-" + compacta.group(2) + "-" + compacta.group(1);

        return null;
    }

    /** true se o valor veio preenchido mas em formato não reconhecido. */
    public static boolean invalida(String original, String normalizada) {
        return original != null && !original.isBlank() && normalizada == null;
    }
}
