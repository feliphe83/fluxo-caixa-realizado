package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.agendamento.EstoqueParadoHandler;
import br.com.lopes.fluxo.dao.EstoqueParadoDAO;
import br.com.lopes.fluxo.dao.EstoqueParadoSnapshotDAO;
import br.com.lopes.fluxo.util.ChromiumPdfUtil;
import br.com.lopes.fluxo.util.EstoqueParadoCache;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API do Alerta de Estoque Parado (estoque-parado-relatorio.html), tanto para
 * o envio agendado quanto para a tela do Hub abrir/baixar sob demanda.
 *
 * GET /api/estoque-parado[?diasLimite=90]       -> JSON (itens + comparação)
 * GET /api/estoque-parado/pdf[?diasLimite=90]   -> PDF pronto para download
 * GET /api/estoque-parado/excel[?diasLimite=90] -> planilha pronta para download
 *
 * Cada chamada à raiz é tratada como uma execução do alerta: consulta o
 * Oracle, compara com o snapshot gravado anteriormente e grava um novo (ver
 * {@link EstoqueParadoSnapshotDAO}) — é assim que a série semanal é
 * construída, sem depender de agendamento externo pra isso. Rodar o
 * relatório mais de uma vez no mesmo dia só atualiza o ponto de hoje, não
 * duplica. As rotas /pdf e /excel reaproveitam o resultado dessa consulta
 * (ver {@link EstoqueParadoCache}) em vez de consultar o Oracle de novo.
 */
@WebServlet("/api/estoque-parado/*")
public class EstoqueParadoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(EstoqueParadoServlet.class.getName());
    private final Gson gson = new Gson();
    private final EstoqueParadoDAO dao = new EstoqueParadoDAO();
    private final EstoqueParadoSnapshotDAO snapshotDAO = new EstoqueParadoSnapshotDAO();

    private record Resultado(List<Map<String, Object>> itens, Map<String, Object> comparacao) {}

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rota = req.getPathInfo() == null ? "/" : req.getPathInfo();
        int diasLimite = EstoqueParadoDAO.DIAS_LIMITE_PADRAO;
        String param = req.getParameter("diasLimite");
        if (param != null && param.matches("\\d+")) diasLimite = Integer.parseInt(param);

        try {
            if ("/pdf".equals(rota)) {
                baixarPdf(req, resp, diasLimite);
            } else if ("/excel".equals(rota)) {
                baixarExcel(resp, diasLimite);
            } else {
                json(resp, diasLimite);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no alerta de estoque parado (" + rota + "): " + e.getMessage(), e);
            if (resp.isCommitted()) return;
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().print("{\"ok\":false,\"erro\":" + gson.toJson("Falha ao gerar: " + e.getMessage()) + "}");
        }
    }

    private void json(HttpServletResponse resp, int diasLimite) throws Exception {
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        LocalDate hoje = LocalDate.now();
        Resultado r = calcular(diasLimite, hoje);

        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("dataExecucao", hoje.toString());
        o.addProperty("diasLimite", diasLimite);
        o.add("itens", gson.toJsonTree(r.itens()));
        o.add("comparacao", gson.toJsonTree(r.comparacao()));
        out.print(gson.toJson(o));
    }

    /**
     * Baixa o PDF gerado na hora — mesmo mecanismo do envio agendado
     * (ChromiumPdfUtil abrindo a própria página do relatório), só que
     * disparado pelo clique de quem está logado, em vez do scheduler.
     */
    private void baixarPdf(HttpServletRequest req, HttpServletResponse resp, int diasLimite) throws Exception {
        HttpSession session = req.getSession(false);
        Object idUsuario = session == null ? null : session.getAttribute("idUsuario");
        if (idUsuario == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // Consulta uma vez aqui e guarda no cache antes de abrir o Chromium,
        // que buscaria os mesmos dados de novo (via esta mesma rota "/") se
        // não achasse o cache fresco — ver EstoqueParadoCache.
        LocalDate hoje = LocalDate.now();
        calcular(diasLimite, hoje);

        String destino = "estoque-parado-relatorio.html?diasLimite=" + diasLimite;
        String url = baseUrlInterno() + "/api/interno/sessao-relatorio?idUsuario=" + idUsuario
                + "&redirect=" + URLEncoder.encode(destino, StandardCharsets.UTF_8);
        byte[] pdf = ChromiumPdfUtil.gerarPdf(url, 6 * 60 * 1000);

        resp.setContentType("application/pdf");
        resp.setHeader("Content-Disposition", "attachment; filename=\"estoque-parado-" + hoje + ".pdf\"");
        resp.setContentLength(pdf.length);
        resp.getOutputStream().write(pdf);
        resp.getOutputStream().flush();
    }

    private void baixarExcel(HttpServletResponse resp, int diasLimite) throws Exception {
        LocalDate hoje = LocalDate.now();
        Resultado r = calcular(diasLimite, hoje);
        byte[] excel = EstoqueParadoHandler.montarExcel(r.itens(), r.comparacao());

        resp.setContentType("application/vnd.ms-excel");
        resp.setHeader("Content-Disposition", "attachment; filename=\"estoque-parado-" + hoje + ".xls\"");
        resp.setContentLength(excel.length);
        resp.getOutputStream().write(excel);
        resp.getOutputStream().flush();
    }

    /** Usa o cache do dia se estiver fresco; senão consulta o Oracle e grava o snapshot. */
    private Resultado calcular(int diasLimite, LocalDate hoje) throws Exception {
        EstoqueParadoCache.Entrada cache = EstoqueParadoCache.valida(hoje, 10 * 60 * 1000L);
        if (cache != null) return new Resultado(cache.itens(), cache.comparacao());

        List<Map<String, Object>> itens = dao.buscar(diasLimite);
        List<Map<String, Object>> anterior = snapshotDAO.buscarAnterior(hoje);
        Map<String, Object> comparacao = snapshotDAO.comparar(itens, anterior);
        snapshotDAO.salvarSnapshot(itens, hoje);
        EstoqueParadoCache.preencher(hoje, itens, comparacao);
        return new Resultado(itens, comparacao);
    }

    private static String baseUrlInterno() {
        String v = System.getenv("APP_BASE_URL_INTERNO");
        return (v == null || v.isBlank()) ? "http://127.0.0.1:8080/fluxo-caixa" : v;
    }
}
