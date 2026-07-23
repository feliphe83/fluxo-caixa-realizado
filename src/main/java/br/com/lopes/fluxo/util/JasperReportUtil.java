package br.com.lopes.fluxo.util;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compila e mantém em cache (em memória, por classe de recurso) os relatórios
 * Jasper (.jrxml) usados para gerar PDF. A compilação é cara e o .jrxml não
 * muda em tempo de execução, então basta compilar uma vez por deploy.
 */
public final class JasperReportUtil {

    private static final ConcurrentHashMap<String, JasperReport> CACHE = new ConcurrentHashMap<>();

    private JasperReportUtil() {}

    /**
     * @param caminhoClasspath ex.: "/reports/ordem-compra.jrxml"
     */
    public static JasperReport compilar(String caminhoClasspath) throws JRException, IOException {
        JasperReport ja = CACHE.get(caminhoClasspath);
        if (ja != null) return ja;

        try (InputStream in = JasperReportUtil.class.getResourceAsStream(caminhoClasspath)) {
            if (in == null) {
                throw new IOException("Recurso não encontrado no classpath: " + caminhoClasspath);
            }
            JasperReport compilado = JasperCompileManager.compileReport(in);
            CACHE.putIfAbsent(caminhoClasspath, compilado);
            return CACHE.get(caminhoClasspath);
        }
    }
}
