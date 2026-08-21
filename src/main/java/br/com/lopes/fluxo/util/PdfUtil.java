package br.com.lopes.fluxo.util;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

/**
 * Extrai o texto de um PDF.
 *
 * Usa o OpenPDF, que já está no projeto por causa da geração de PDF das
 * ordens de compra (veio junto com o JasperReports) — então dá para LER PDF
 * sem nenhuma dependência nova. Serve para importar a tabela de preço da cana
 * do CONSECANA, que só é publicada em PDF.
 */
public final class PdfUtil {

    private PdfUtil() {}

    /** O texto de todas as páginas, uma emendada na outra com quebra de linha. */
    public static String extrairTexto(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extrator = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            int paginas = reader.getNumberOfPages();
            for (int p = 1; p <= paginas; p++) {
                sb.append(extrator.getTextFromPage(p)).append('\n');
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }
}
