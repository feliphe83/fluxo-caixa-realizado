package br.com.lopes.fluxo.util;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Gera PDF de uma página HTML (ex.: combustivel-dashboard.html) chamando o
 * Chromium instalado no servidor em modo headless — reaproveita o CSS de
 * impressão da própria página em vez de recriar o relatório num motor de PDF
 * separado (JasperReports etc.).
 *
 * Configuração necessária:
 *   - Variável de ambiente CHROMIUM_BIN com o caminho do binário (ex.:
 *     /usr/bin/chromium-browser, /usr/bin/chromium ou /usr/bin/google-chrome).
 *     Se não configurada, tenta os caminhos comuns do Ubuntu nessa ordem.
 *
 * Limitação conhecida: a página só recebe os dados via fetch() assíncrono
 * depois de carregar (ex.: combustivel-dashboard.html chama
 * /api/combustivel-dashboard e só desenha os gráficos quando a resposta
 * chega). O Chromium headless com --print-to-pdf espera o evento "load" da
 * página, o que normalmente já é suficiente pra esse fetch (rápido) e os
 * gráficos (Chart.js, síncrono) terminarem — mas não há garantia formal.
 * Se o PDF gerado às vezes sair incompleto/em branco, o próximo passo é usar
 * o Chrome DevTools Protocol pra esperar um sinal explícito da página antes
 * de imprimir, em vez do --print-to-pdf simples da linha de comando.
 */
public final class ChromiumPdfUtil {

    private static final String[] CAMINHOS_PADRAO = {
        "/usr/bin/chromium-browser",
        "/usr/bin/chromium",
        "/snap/bin/chromium",   // instalação via "sudo apt install chromium-browser" no Ubuntu 20.04+ cai aqui (é um snap por trás)
        "/usr/bin/google-chrome",
        "/usr/bin/google-chrome-stable",
    };

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private ChromiumPdfUtil() {}

    private static String binario() {
        String env = System.getenv("CHROMIUM_BIN");
        if (env != null && !env.isBlank() && new File(env).canExecute()) return env;
        for (String caminho : CAMINHOS_PADRAO) {
            if (new File(caminho).canExecute()) return caminho;
        }
        throw new IllegalStateException(
            "Nenhum binário do Chromium encontrado. Instale (ex.: apt install chromium-browser) "
            + "ou configure a variável de ambiente CHROMIUM_BIN com o caminho do executável.");
    }

    /** Gera o PDF da URL informada e devolve os bytes prontos (ex.: pra anexar no WhatsApp). */
    public static byte[] gerarPdf(String url) throws Exception {
        String bin = binario();
        File tempPdf = File.createTempFile("relatorio-" + UUID.randomUUID(), ".pdf");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                bin,
                "--headless=new",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--print-to-pdf=" + tempPdf.getAbsolutePath(),
                "--print-to-pdf-no-header",
                "--no-pdf-header-footer",
                "--virtual-time-budget=8000",
                url
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            byte[] saida = p.getInputStream().readAllBytes();
            boolean terminou = p.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!terminou) {
                p.destroyForcibly();
                throw new RuntimeException("Chromium não terminou dentro do tempo limite (" + TIMEOUT.toSeconds() + "s)");
            }
            if (p.exitValue() != 0) {
                throw new RuntimeException("Chromium retornou código " + p.exitValue() + ": " + new String(saida));
            }
            if (!tempPdf.exists() || tempPdf.length() == 0) {
                throw new RuntimeException("Chromium não gerou o arquivo PDF (saída: " + new String(saida) + ")");
            }
            return Files.readAllBytes(tempPdf.toPath());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempPdf.delete();
        }
    }
}
