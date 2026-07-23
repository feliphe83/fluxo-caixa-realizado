package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.JasperReportUtil;
import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.OrdemCompraPdfTokenCache;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gera e devolve o PDF formatado (layout do relatório Jasper original) de
 * uma Ordem de Compra específica. Pensado pra ser um link clicável direto na
 * resposta do Dr. Alfredo (chat) — por isso NÃO usa header X-Agro-Api-Key
 * nem sessão de login (ver AuthFilter, rota liberada em /api/publico/*); o
 * token opaco (gerado por OrdemCompraPdfTokenCache quando /api/financeiro/
 * ordem-compra é consultado com "nroc") é a própria credencial de acesso,
 * válido por tempo limitado.
 *
 * GET /api/publico/ordem-compra-pdf?token=...
 *
 * O relatório original (reports/ordem-compra.jrxml) filtra por período de
 * vencimento da parcela — aqui usamos um período fixo bem largo (1900 a
 * 2050) porque o token já restringe a consulta a uma única ordem (NROC).
 *
 * Uma mesma ordem pode ter mais de uma parcela vinculada a documentos
 * diferentes (pagamento parcelado) — nesse caso o PDF sai com mais de uma
 * página, uma por parcela/documento, repetindo os itens e mudando só o
 * bloco de vencimento/valor no rodapé (comportamento herdado do relatório
 * original, não é um bug desta adaptação).
 */
@WebServlet("/api/publico/ordem-compra-pdf")
public class OrdemCompraPdfServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(OrdemCompraPdfServlet.class.getName());
    private static final String RELATORIO = "/reports/ordem-compra.jrxml";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String token = req.getParameter("token");
        Integer nroc = OrdemCompraPdfTokenCache.resolver(token);
        if (nroc == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.setContentType("text/plain;charset=UTF-8");
            resp.getWriter().print("Link expirado ou inválido. Peça ao Dr. Alfredo pra consultar a ordem de compra novamente.");
            return;
        }

        try (Connection conn = OracleConnectionUtil.getConnection();
             InputStream logo = getServletContext().getResourceAsStream("/img/logo.png")) {

            JasperReport relatorio = JasperReportUtil.compilar(RELATORIO);

            Map<String, Object> params = new HashMap<>();
            params.put("NROC", new BigDecimal(nroc));
            params.put("DATAINICIO", "1900-01-01");
            params.put("DATAFIM", "2050-01-01");
            params.put("LOGO", logo);

            JasperPrint print = JasperFillManager.fillReport(relatorio, params, conn);
            byte[] pdf = JasperExportManager.exportReportToPdf(print);

            resp.setContentType("application/pdf");
            resp.setHeader("Content-Disposition", "inline; filename=\"ordem-compra-" + nroc + ".pdf\"");
            resp.setContentLength(pdf.length);
            resp.getOutputStream().write(pdf);
            resp.getOutputStream().flush();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao gerar PDF da ordem de compra " + nroc, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("text/plain;charset=UTF-8");
            resp.getWriter().print("Falha ao gerar o PDF: " + e.getMessage());
        }
    }
}
