package br.com.lopes.fluxo.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Onde os documentos de admissão ficam salvos no disco do servidor.
 *
 * FORA da pasta do webapp de propósito: o deploy.sh apaga e recria a pasta
 * do WAR a cada `git pull && ./deploy.sh` (é assim que o Mapa Gerencial e
 * outros já funcionam) — gravar ali dentro significa perder todo documento
 * enviado no próximo deploy. Por padrão usa a pasta do próprio Tomcat
 * (catalina.base, ex.: /opt/tomcat9), que o deploy nunca mexe; dá para
 * apontar para outro lugar com ADMISSAO_DOCUMENTOS_DIR.
 */
public final class ArmazenamentoAdmissaoUtil {

    private ArmazenamentoAdmissaoUtil() {}

    public static Path diretorioBase() {
        String env = System.getenv("ADMISSAO_DOCUMENTOS_DIR");
        String base = (env != null && !env.isBlank())
                ? env.trim()
                : System.getProperty("catalina.base", ".") + "/admissao-documentos";
        return Path.of(base);
    }

    /**
     * Salva o arquivo em admissao-documentos/{cpf}/{idTipoDocumento}-{uuid}.{ext},
     * apagando o arquivo anterior daquele mesmo (cpf, tipo) se existir — reenviar
     * um documento substitui, não acumula.
     *
     * @return o caminho relativo salvo no banco (ex.: "12345678901/3-ab12cd.jpg")
     */
    public static String salvar(String cpfSoDigitos, int idTipoDocumento, String nomeArquivoOriginal,
                                 InputStream conteudo, String caminhoAnteriorParaApagar) throws IOException {
        Path pastaCpf = diretorioBase().resolve(cpfSoDigitos);
        Files.createDirectories(pastaCpf);

        String ext = extensao(nomeArquivoOriginal);
        String nomeArquivo = idTipoDocumento + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path destino = pastaCpf.resolve(nomeArquivo);
        Files.copy(conteudo, destino, StandardCopyOption.REPLACE_EXISTING);

        if (caminhoAnteriorParaApagar != null && !caminhoAnteriorParaApagar.isBlank()) {
            apagar(caminhoAnteriorParaApagar);
        }
        return cpfSoDigitos + "/" + nomeArquivo;
    }

    public static Path resolver(String caminhoRelativo) {
        return diretorioBase().resolve(caminhoRelativo).normalize();
    }

    /** Confere que o caminho resolvido continua dentro da pasta base — trilha (cpf/arquivo) vem do banco, não de entrada do usuário, mas a checagem é barata. */
    public static boolean dentroDaBase(Path caminho) {
        return caminho.startsWith(diretorioBase().normalize());
    }

    public static void apagar(String caminhoRelativo) {
        try {
            Path p = resolver(caminhoRelativo);
            if (dentroDaBase(p)) Files.deleteIfExists(p);
        } catch (IOException ignorado) {
            // arquivo órfão não é motivo para falhar a operação principal
        }
    }

    private static String extensao(String nomeOriginal) {
        if (nomeOriginal == null) return "";
        int p = nomeOriginal.lastIndexOf('.');
        if (p < 0 || p == nomeOriginal.length() - 1) return "";
        String ext = nomeOriginal.substring(p).toLowerCase().replaceAll("[^a-z0-9.]", "");
        return ext.length() > 6 ? "" : ext;   // extensão absurda: ignora em vez de propagar lixo
    }
}
