package br.com.lopes.fluxo.util;

/** Validação de CPF (dígitos verificadores) — sem consultar nenhuma base, só o algoritmo. */
public final class CpfUtil {

    private CpfUtil() {}

    public static String soDigitos(String v) {
        return v == null ? "" : v.replaceAll("\\D", "");
    }

    /** @return true se os 11 dígitos batem com os dois dígitos verificadores. */
    public static boolean valido(String cpf) {
        String d = soDigitos(cpf);
        if (d.length() != 11) return false;
        // "00000000000", "11111111111" etc. batem no algoritmo mas não são CPF real.
        if (d.chars().distinct().count() == 1) return false;

        int dv1 = digitoVerificador(d, 9, 10);
        if (dv1 != d.charAt(9) - '0') return false;
        int dv2 = digitoVerificador(d, 10, 11);
        return dv2 == d.charAt(10) - '0';
    }

    private static int digitoVerificador(String d, int tamanho, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (d.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** "12345678901" -> "123.456.789-01", só para exibição. */
    public static String formatar(String cpf) {
        String d = soDigitos(cpf);
        if (d.length() != 11) return cpf == null ? "" : cpf;
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }
}
