package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.EstoqueParadoDAO;
import br.com.lopes.fluxo.dao.EstoqueParadoSnapshotDAO;
import br.com.lopes.fluxo.util.EstoqueParadoCache;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API do Alerta de Estoque Parado (estoque-parado-relatorio.html).
 *
 * GET /api/estoque-parado[?diasLimite=90]
 *
 * Cada chamada é tratada como uma execução do alerta: consulta o Oracle,
 * compara com o snapshot gravado anteriormente e grava um novo (ver
 * {@link EstoqueParadoSnapshotDAO}) — é assim que a série semanal é
 * construída, sem depender de agendamento externo pra isso. Rodar o
 * relatório mais de uma vez no mesmo dia só atualiza o ponto de hoje, não
 * duplica.
 */
@WebServlet("/api/estoque-parado")
public class EstoqueParadoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(EstoqueParadoServlet.class.getName());
    private final Gson gson = new Gson();
    private final EstoqueParadoDAO dao = new EstoqueParadoDAO();
    private final EstoqueParadoSnapshotDAO snapshotDAO = new EstoqueParadoSnapshotDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            int diasLimite = EstoqueParadoDAO.DIAS_LIMITE_PADRAO;
            String param = req.getParameter("diasLimite");
            if (param != null && param.matches("\\d+")) diasLimite = Integer.parseInt(param);

            LocalDate hoje = LocalDate.now();
            // Se o EstoqueParadoHandler já rodou esta consulta há pouco (é ele
            // quem abre esta página no Chromium pra gerar o PDF), reaproveita
            // o resultado em vez de consultar o Oracle de novo — ver
            // EstoqueParadoCache.
            EstoqueParadoCache.Entrada cache = EstoqueParadoCache.valida(hoje, 10 * 60 * 1000L);
            List<Map<String, Object>> itens;
            Map<String, Object> comparacao;
            if (cache != null) {
                itens = cache.itens();
                comparacao = cache.comparacao();
            } else {
                itens = dao.buscar(diasLimite);
                List<Map<String, Object>> anterior = snapshotDAO.buscarAnterior(hoje);
                comparacao = snapshotDAO.comparar(itens, anterior);
                snapshotDAO.salvarSnapshot(itens, hoje);
            }

            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.addProperty("dataExecucao", hoje.toString());
            r.addProperty("diasLimite", diasLimite);
            r.add("itens", gson.toJsonTree(itens));
            r.add("comparacao", gson.toJsonTree(comparacao));
            out.print(gson.toJson(r));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no alerta de estoque parado: " + e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":" + gson.toJson("Falha ao consultar: " + e.getMessage()) + "}");
        }
    }
}
