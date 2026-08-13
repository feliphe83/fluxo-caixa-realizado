package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.ChromiumPdfUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PDF do resumo do Controle de Serviços, pronto para baixar.
 *
 * Antes o resumo saía pelo window.print() do navegador, e o resultado
 * dependia do que cada um tinha configurado na caixa de impressão — margem,
 * orientação, escala, cabeçalho do navegador. Aqui o servidor renderiza com
 * o mesmo Chromium que já gera o relatório de combustível, então o arquivo
 * sai igual para todo mundo.
 *
 * GET /api/controle-servicos/pdf?fazendas=&fornecedor=&dataIni=&dataFim=
 */
@WebServlet("/api/controle-servicos/pdf")
public class ControleServicosPdfServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ControleServicosPdfServlet.class.getName());

    /**
     * A tela consulta o Oracle antes de montar o resumo, e essa consulta é
     * legitimamente lenta. Com os 8s padrão o PDF sairia com o "Consultando…"
     * no lugar dos números.
     */
    private static final int TEMPO_VIRTUAL_MS = 60_000;

    private static String baseUrlInterno() {
        String v = System.getenv("APP_BASE_URL_INTERNO");
        return (v == null || v.isBlank()) ? "http://127.0.0.1:8080/fluxo-caixa" : v;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession sessao = req.getSession(false);
        Object idUsuario = sessao == null ? null : sessao.getAttribute("idUsuario");
        if (idUsuario == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().print("{\"ok\":false,\"erro\":\"Sessão expirada\"}");
            return;
        }

        String dataIni = req.getParameter("dataIni");
        String dataFim = req.getParameter("dataFim");
        if (dataIni == null || !dataIni.matches("\\d{4}-\\d{2}-\\d{2}")
         || dataFim == null || !dataFim.matches("\\d{4}-\\d{2}-\\d{2}")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().print("{\"ok\":false,\"erro\":\"Informe o período completo\"}");
            return;
        }

        // A própria tela monta o resumo; o Chromium só a abre já com os
        // filtros e com resumo=1, que manda montar sem abrir a impressão.
        String destino = "controle-servicos.html"
                + "?dataIni=" + enc(dataIni)
                + "&dataFim=" + enc(dataFim)
                + "&fazendas=" + enc(req.getParameter("fazendas"))
                + "&fornecedor=" + enc(req.getParameter("fornecedor"))
                + "&resumo=1";

        String url = baseUrlInterno() + "/api/interno/sessao-relatorio?idUsuario=" + idUsuario
                + "&redirect=" + enc(destino);

        try {
            LOG.info("Gerando PDF do Controle de Serviços: " + destino);
            byte[] pdf = ChromiumPdfUtil.gerarPdf(url, TEMPO_VIRTUAL_MS);

            resp.setContentType("application/pdf");
            resp.setHeader("Content-Disposition",
                    "attachment; filename=\"controle-servicos-" + dataIni + "-a-" + dataFim + ".pdf\"");
            resp.setHeader("Cache-Control", "no-store");
            resp.setContentLength(pdf.length);
            resp.getOutputStream().write(pdf);
            resp.getOutputStream().flush();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao gerar o PDF do Controle de Serviços", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json;charset=UTF-8");
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            resp.getWriter().print("{\"ok\":false,\"erro\":\""
                    + msg.replace("\"", "'").replace("\n", " ") + "\"}");
        }
    }
}
