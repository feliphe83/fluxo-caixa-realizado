package br.com.lopes.fluxo.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Gera PDF de uma página HTML (ex.: combustivel-dashboard.html) chamando o
 * Chromium/Chrome instalado no servidor em modo headless — reaproveita o CSS
 * de impressão da própria página em vez de recriar o relatório num motor de
 * PDF separado (JasperReports etc.).
 *
 * Configuração necessária:
 *   - Variável de ambiente CHROMIUM_BIN com o caminho do binário (ex.:
 *     /usr/bin/google-chrome-stable). Se não configurada, tenta os caminhos
 *     comuns do Ubuntu nessa ordem.
 *
 * Cada execução usa um --user-data-dir temporário e exclusivo: sem isso o
 * Chrome tenta abrir o perfil padrão do usuário do serviço (HOME do Tomcat)
 * e pode ficar pendurado esperando o lock do perfil (SingletonLock) ou
 * falhar por diretório não gravável — foi a causa de um travamento em
 * produção onde o processo nunca terminava.
 *
 * A saída do processo vai para um arquivo temporário (não para um pipe): ler
 * o pipe com readAllBytes() antes do waitFor() bloqueia pra sempre se o
 * Chrome não sair, e o timeout nunca chega a valer — o scheduler de
 * relatórios (thread única) ficava travado junto.
 *
 * Limitação conhecida: a página só recebe os dados via fetch() assíncrono
 * depois de carregar. O --virtual-time-budget dá um orçamento de tempo pra
 * isso resolver antes de imprimir, o que cobre o caso normal — mas não há
 * garantia formal; se o PDF sair incompleto, o próximo passo é usar o Chrome
 * DevTools Protocol pra esperar um sinal explícito da página.
 */
public final class ChromiumPdfUtil {

    // Google Chrome (.deb) primeiro. O Chromium via snap fica FORA da lista de
    // propósito: o confinamento do snap dá a ele um /tmp privado — o PDF é
    // escrito lá dentro e fica invisível pro processo Java, que conclui que o
    // arquivo não foi gerado (aconteceu em produção quando o snap ganhou a
    // prioridade). Se um dia só houver o snap na máquina, não use — instale o
    // Chrome via .deb ou aponte CHROMIUM_BIN pra outro binário não confinado.
    private static final String[] CAMINHOS_PADRAO = {
        "/usr/bin/google-chrome-stable",
        "/usr/bin/google-chrome",
        "/usr/bin/chromium-browser",
        "/usr/bin/chromium",
    };

    // 8 minutos: o --virtual-time-budget pausa o relógio virtual enquanto há
    // fetch() pendente, então o tempo real de execução é dominado pela API da
    // página (ex.: o dashboard de combustível com ~26 semanas leva vários
    // minutos consultando o Oracle) — um timeout curto (90s) matava o Chrome
    // no meio de uma geração que terminaria bem (observado ~5min30s em
    // produção).
    private static final Duration TIMEOUT = Duration.ofMinutes(8);

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

    /** Quanto tempo simulado a página tem para carregar antes do PDF sair. */
    private static final int TEMPO_VIRTUAL_PADRAO_MS = 8000;

    /** Gera o PDF da URL informada e devolve os bytes prontos (ex.: pra anexar no WhatsApp). */
    public static byte[] gerarPdf(String url) throws Exception {
        return gerarPdf(url, TEMPO_VIRTUAL_PADRAO_MS);
    }

    /**
     * @param tempoVirtualMs quanto esperar a página montar. Tela que consulta
     *        o Oracle antes de desenhar precisa de mais que os 8s padrão —
     *        estourar esse tempo não dá erro, sai um PDF pela metade, que é
     *        pior porque parece que funcionou.
     */
    public static byte[] gerarPdf(String url, int tempoVirtualMs) throws Exception {
        String bin = binario();
        File tempPdf = File.createTempFile("relatorio-" + UUID.randomUUID(), ".pdf");
        File logSaida = File.createTempFile("chromium-saida-", ".log");
        Path perfilTemp = Files.createTempDirectory("chromium-perfil-");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                bin,
                "--headless=new",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-extensions",
                "--user-data-dir=" + perfilTemp,
                "--print-to-pdf=" + tempPdf.getAbsolutePath(),
                "--no-pdf-header-footer",
                "--virtual-time-budget=" + tempoVirtualMs,
                url
            );
            pb.redirectErrorStream(true);
            pb.redirectOutput(logSaida);

            Process p = pb.start();
            boolean terminou = p.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!terminou) {
                p.destroyForcibly();
                p.waitFor(10, TimeUnit.SECONDS);
                throw new RuntimeException("Chromium não terminou dentro do tempo limite ("
                        + TIMEOUT.toSeconds() + "s). Saída: " + lerSaida(logSaida));
            }
            if (p.exitValue() != 0) {
                throw new RuntimeException("Chromium retornou código " + p.exitValue() + ": " + lerSaida(logSaida));
            }
            if (!tempPdf.exists() || tempPdf.length() == 0) {
                throw new RuntimeException("Chromium não gerou o arquivo PDF. Saída: " + lerSaida(logSaida));
            }
            return Files.readAllBytes(tempPdf.toPath());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempPdf.delete();
            //noinspection ResultOfMethodCallIgnored
            logSaida.delete();
            apagarRecursivo(perfilTemp);
        }
    }

    private static String lerSaida(File logSaida) {
        try {
            String s = Files.readString(logSaida.toPath());
            return s.length() > 1500 ? s.substring(s.length() - 1500) : s;
        } catch (IOException e) {
            return "(não foi possível ler a saída do Chromium)";
        }
    }

    private static void apagarRecursivo(Path dir) {
        try (Stream<Path> caminhos = Files.walk(dir)) {
            caminhos.sorted(Comparator.reverseOrder()).forEach(pth -> {
                //noinspection ResultOfMethodCallIgnored
                pth.toFile().delete();
            });
        } catch (IOException ignorado) {
            // diretório temporário — se sobrar, o SO limpa
        }
    }
}
