package br.com.lopes.fluxo.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Onde os PDFs de NF-e detectados no e-mail de Compras ficam salvos no disco
 * do servidor — mesmo raciocínio de {@link ArmazenamentoAdmissaoUtil}: FORA
 * da pasta do webapp, porque o deploy.sh apaga e recria essa pasta a cada
 * {@code git pull && ./deploy.sh}. Por padrão usa a pasta do próprio Tomcat
 * (catalina.base), configurável por NF_EMAIL_ANEXOS_DIR.
 */
public final class ArmazenamentoNfEmailUtil {

    private ArmazenamentoNfEmailUtil() {}

    private static final DateTimeFormatter PASTA_MES = DateTimeFormatter.ofPattern("yyyyMM");

    public static Path diretorioBase() {
        String env = System.getenv("NF_EMAIL_ANEXOS_DIR");
        String base = (env != null && !env.isBlank())
                ? env.trim()
                : System.getProperty("catalina.base", ".") + "/nf-email-anexos";
        return Path.of(base);
    }

    /**
     * Salva o PDF em nf-email-anexos/{AAAAMM}/{uuid}.pdf — agrupado por mês só
     * para a pasta não virar uma única listagem enorme com o tempo.
     *
     * @return o caminho relativo salvo no banco (ex.: "202608/ab12cd34.pdf")
     */
    public static String salvar(byte[] conteudo) throws IOException {
        String pastaMes = LocalDate.now().format(PASTA_MES);
        Path pasta = diretorioBase().resolve(pastaMes);
        Files.createDirectories(pasta);

        String nomeArquivo = UUID.randomUUID() + ".pdf";
        Path destino = pasta.resolve(nomeArquivo);
        Files.copy(new java.io.ByteArrayInputStream(conteudo), destino, StandardCopyOption.REPLACE_EXISTING);
        return pastaMes + "/" + nomeArquivo;
    }

    public static Path resolver(String caminhoRelativo) {
        return diretorioBase().resolve(caminhoRelativo).normalize();
    }

    /** Trilha vem do banco, não de entrada externa, mas a checagem é barata. */
    public static boolean dentroDaBase(Path caminho) {
        return caminho.startsWith(diretorioBase().normalize());
    }

    public static void apagar(String caminhoRelativo) {
        if (caminhoRelativo == null || caminhoRelativo.isBlank()) return;
        try {
            Path p = resolver(caminhoRelativo);
            if (dentroDaBase(p)) Files.deleteIfExists(p);
        } catch (IOException ignorado) {
            // arquivo órfão não é motivo para falhar a operação principal
        }
    }
}
