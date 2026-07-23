package br.com.lopes.fluxo.util;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gera e valida os tokens usados no link direto do PDF de uma Ordem de
 * Compra (/api/financeiro/ordem-compra-pdf?token=...). O link é pensado pra
 * ser clicado a partir da resposta do Dr. Alfredo (chat), então não pode
 * depender de header (X-Agro-Api-Key) nem de sessão de login — o token
 * opaco e de validade curta é a própria credencial de acesso.
 */
public final class OrdemCompraPdfTokenCache {

    private static final long VALIDADE_MS = 48 * 60 * 60 * 1000L; // 48 horas
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final class Entrada {
        final int nroc;
        final long expiraEm;
        Entrada(int nroc, long expiraEm) {
            this.nroc = nroc;
            this.expiraEm = expiraEm;
        }
    }

    private static final ConcurrentHashMap<String, Entrada> TOKENS = new ConcurrentHashMap<>();

    private OrdemCompraPdfTokenCache() {}

    public static String gerarToken(int nroc) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TOKENS.put(token, new Entrada(nroc, System.currentTimeMillis() + VALIDADE_MS));
        limparExpirados();
        return token;
    }

    /** @return o nroc associado, ou null se o token não existe/expirou. */
    public static Integer resolver(String token) {
        if (token == null || token.isBlank()) return null;
        Entrada e = TOKENS.get(token);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expiraEm) {
            TOKENS.remove(token);
            return null;
        }
        return e.nroc;
    }

    private static void limparExpirados() {
        long agora = System.currentTimeMillis();
        TOKENS.entrySet().removeIf(en -> agora > en.getValue().expiraEm);
    }
}
