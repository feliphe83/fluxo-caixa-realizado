package br.com.lopes.fluxo.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Gera o dump do banco MySQL "intranet" chamando o mysqldump instalado no
 * servidor, e devolve já compactado em .zip (um único arquivo .sql dentro) —
 * mesmo raciocínio de {@link ChromiumPdfUtil}: processo externo, saída para
 * arquivo temporário (nunca lida por um pipe antes do waitFor(), que trava
 * pra sempre se o processo não sair), timeout com destroyForcibly, e limpeza
 * de temporários no finally.
 *
 * A senha do MySQL vai por variável de ambiente do processo filho (MYSQL_PWD),
 * não como argumento de linha de comando — argumento de linha de comando fica
 * visível pra qualquer usuário local via "ps aux"; variável de ambiente do
 * processo filho, só pro dono do processo (ou root).
 */
public final class BackupBancoUtil {

    private static final String DB_HOST = "localhost";
    private static final int DB_PORT = 3306;
    private static final String DB_NOME = "intranet";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    // mysqldump do pacote mysql-client do Ubuntu/Debian primeiro (mesmo alvo de
    // produção do ChromiumPdfUtil — .deb, não snap). MYSQLDUMP_BIN sobrepõe.
    private static final String[] CAMINHOS_PADRAO = {
        "/usr/bin/mysqldump",
        "/usr/local/bin/mysqldump",
        "/usr/local/mysql/bin/mysqldump",
    };

    // Backup do banco da própria aplicação (não o Oracle do ERP): não deve
    // levar minutos. Um teto folgado só para não travar o agendador se o
    // processo pendurar por algum motivo (disco cheio, etc.).
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private static final DateTimeFormatter NOME_ARQUIVO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private BackupBancoUtil() {}

    private static String binario() {
        String env = System.getenv("MYSQLDUMP_BIN");
        if (env != null && !env.isBlank() && new File(env).canExecute()) return env;
        for (String caminho : CAMINHOS_PADRAO) {
            if (new File(caminho).canExecute()) return caminho;
        }
        throw new IllegalStateException(
            "mysqldump não encontrado. Instale (ex.: apt install mysql-client) "
            + "ou configure a variável de ambiente MYSQLDUMP_BIN com o caminho do executável.");
    }

    /** Registro de um backup gerado — nome sugerido pro download e os bytes do .zip. */
    public record Resultado(String nomeArquivo, byte[] conteudoZip) {}

    /**
     * Roda o mysqldump, compacta o resultado em .zip e devolve pronto pra
     * salvar em disco ou enviar como download.
     */
    public static Resultado gerarBackup() throws Exception {
        String bin = binario();
        File tempSql = File.createTempFile("backup-intranet-" + UUID.randomUUID(), ".sql");
        File logSaida = File.createTempFile("mysqldump-saida-", ".log");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                bin,
                "-h", DB_HOST,
                "-P", String.valueOf(DB_PORT),
                "-u", DB_USER,
                "--single-transaction",
                "--routines",
                "--triggers",
                "--events",
                "--default-character-set=utf8mb4",
                DB_NOME
            );
            pb.environment().put("MYSQL_PWD", DB_PASS);
            pb.redirectErrorStream(false);
            pb.redirectOutput(tempSql);
            pb.redirectError(logSaida);

            Process p = pb.start();
            boolean terminou = p.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!terminou) {
                p.destroyForcibly();
                p.waitFor(10, TimeUnit.SECONDS);
                throw new RuntimeException("mysqldump não terminou dentro do tempo limite ("
                        + TIMEOUT.toSeconds() + "s). Saída: " + lerSaida(logSaida));
            }
            if (p.exitValue() != 0) {
                throw new RuntimeException("mysqldump retornou código " + p.exitValue() + ": " + lerSaida(logSaida));
            }
            if (!tempSql.exists() || tempSql.length() == 0) {
                throw new RuntimeException("mysqldump não gerou nenhum conteúdo. Saída: " + lerSaida(logSaida));
            }

            String nomeBase = "backup-intranet-" + LocalDateTime.now().format(NOME_ARQUIVO);
            byte[] zip = compactar(tempSql, nomeBase + ".sql");
            return new Resultado(nomeBase + ".zip", zip);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempSql.delete();
            //noinspection ResultOfMethodCallIgnored
            logSaida.delete();
        }
    }

    private static byte[] compactar(File sql, String nomeEntrada) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            zos.putNextEntry(new ZipEntry(nomeEntrada));
            Files.copy(sql.toPath(), zos);
            zos.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static String lerSaida(File logSaida) {
        try {
            String s = Files.readString(logSaida.toPath());
            return s.length() > 1500 ? s.substring(s.length() - 1500) : s;
        } catch (IOException e) {
            return "(não foi possível ler a saída do mysqldump)";
        }
    }
}
