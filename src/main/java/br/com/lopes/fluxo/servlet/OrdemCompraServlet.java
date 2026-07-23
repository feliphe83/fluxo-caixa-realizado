package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.OrdemCompraDAO;
import br.com.lopes.fluxo.util.AgroConsultaCache;
import br.com.lopes.fluxo.util.ChatPermissaoUtil;
import br.com.lopes.fluxo.util.DataParamUtil;
import br.com.lopes.fluxo.util.OrdemCompraPdfTokenCache;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API interna consumida pelo agente de IA (Dr. Alfredo, via n8n) — não é
 * usada pelo front-end web. Autenticação por header X-Agro-Api-Key (ver
 * AuthFilter) e categoria chat_financeiro da sessão de chat.
 *
 * GET /api/financeiro/ordem-compra?dataIniVcto=yyyy-MM-dd&dataFimVcto=yyyy-MM-dd
 *        [&nroc=NNNN] [&fornecedor=trecho do nome] [&sessionId=...]
 *
 * Resposta: { "ok": true, "totalEncontrado": N, "truncado": bool,
 *             "ordens": [ { "nroc", "dataoc", "fornecedor", "valorTotal",
 *             "datavcto", "valorparcela" }, ... ] — uma linha por ordem de
 *             compra (não por item), até MAX_ORDENS, pronta pra "listar
 *             todas as ordens" sem esbarrar no limite de "data",
 *             "ordensTruncado": bool,
 *             "pdfUrl": link direto pro PDF formatado da ordem (só quando
 *             "nroc" é informado — não faz sentido pra uma lista de várias
 *             ordens),
 *             "data": [ { ...colunas da consulta, uma por item... }, ... ] }
 *
 * Período de vencimento (da parcela vinculada à ordem de compra, não a data
 * da própria ordem) é obrigatório mesmo quando "nroc"/"fornecedor" são
 * informados. O agente recebe no máximo MAX_LINHAS em "data" (linha por
 * item); o resultado completo fica no AgroConsultaCache para exportação em
 * Excel pelo front-end do chat. "ordens" existe à parte porque é enxuta o
 * suficiente pra listar todas as ordens de um período/fornecedor sem o
 * mesmo limite (mesmo padrão do campo "parcelas" em contas a pagar).
 */
@WebServlet("/api/financeiro/ordem-compra")
public class OrdemCompraServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(OrdemCompraServlet.class.getName());
    private static final int MAX_LINHAS = 30;
    private static final int MAX_ORDENS = 300;

    private final Gson gson = new Gson();
    private final OrdemCompraDAO dao = new OrdemCompraDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            String sessionId = req.getParameter("sessionId");
            String negado = ChatPermissaoUtil.verificarAcesso(sessionId, ChatPermissaoUtil.FINANCEIRO, "consultas de ordem de compra");
            if (negado != null) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.print("{\"ok\":false,\"erro\":\"" + negado + "\"}");
                return;
            }

            String dataIniVcto = DataParamUtil.normalizar(req.getParameter("dataIniVcto"));
            String dataFimVcto = DataParamUtil.normalizar(req.getParameter("dataFimVcto"));
            if (dataIniVcto == null || dataFimVcto == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Parâmetros dataIniVcto e dataFimVcto são obrigatórios (período de vencimento da parcela), nos formatos yyyy-MM-dd ou dd/mm/aaaa\"}");
                return;
            }

            Integer nroc = lerInteiro(req.getParameter("nroc"));
            String fornecedor = req.getParameter("fornecedor");

            List<Map<String, Object>> lista = dao.buscar(dataIniVcto, dataFimVcto, nroc, fornecedor);

            // Guarda o resultado COMPLETO para exportação (Excel) pelo
            // front-end do chat — o agente de IA só recebe a versão truncada.
            AgroConsultaCache.guardar(sessionId, montarTitulo(dataIniVcto, dataFimVcto, nroc, fornecedor), lista);

            int total = lista.size();
            boolean truncado = total > MAX_LINHAS;
            List<Map<String, Object>> listaLimitada = truncado ? lista.subList(0, MAX_LINHAS) : lista;

            List<Map<String, Object>> ordens = montarOrdens(lista);

            JsonObject resultado = new JsonObject();
            resultado.addProperty("ok", true);
            resultado.addProperty("totalEncontrado", total);
            resultado.addProperty("truncado", truncado);
            resultado.addProperty("ordensTruncado", ordens.size() > MAX_ORDENS);
            resultado.add("ordens", gson.toJsonTree(
                    ordens.size() > MAX_ORDENS ? ordens.subList(0, MAX_ORDENS) : ordens));
            if (nroc != null) {
                resultado.addProperty("pdfUrl", montarPdfUrl(req, nroc));
            }
            resultado.add("data", gson.toJsonTree(listaLimitada));
            out.print(gson.toJson(resultado));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no servlet ordem-compra", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    private static String montarTitulo(String dataIniVcto, String dataFimVcto, Integer nroc, String fornecedor) {
        StringBuilder t = new StringBuilder("Ordem de Compra — Vencimento ")
                .append(dataIniVcto.trim()).append(" a ").append(dataFimVcto.trim());
        if (nroc != null) t.append(" · OC ").append(nroc);
        if (fornecedor != null && !fornecedor.isBlank()) t.append(" · ").append(fornecedor.trim());
        return t.toString();
    }

    /**
     * Uma linha por ordem de compra (não por item) — soma "total" de todos
     * os itens da mesma ordem, mantém os demais dados da primeira ocorrência.
     */
    private static List<Map<String, Object>> montarOrdens(List<Map<String, Object>> lista) {
        Map<Object, Map<String, Object>> porNroc = new LinkedHashMap<>();
        for (Map<String, Object> l : lista) {
            Object nroc = l.get("nroc");
            Map<String, Object> ordem = porNroc.get(nroc);
            if (ordem == null) {
                ordem = new LinkedHashMap<>();
                ordem.put("nroc", nroc);
                ordem.put("dataoc", l.get("dataoc"));
                ordem.put("fornecedor", l.get("nome_fornecedor"));
                ordem.put("valorTotal", 0.0);
                ordem.put("datavcto", l.get("datavcto"));
                ordem.put("valorparcela", l.get("valorparcela"));
                porNroc.put(nroc, ordem);
            }
            Object total = l.get("total");
            if (total instanceof Number n) {
                double atual = (Double) ordem.get("valorTotal");
                ordem.put("valorTotal", arred(atual + n.doubleValue()));
            }
        }
        return new ArrayList<>(porNroc.values());
    }

    private static double arred(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Link direto (sem header/sessão) pro PDF formatado de uma ordem específica. */
    private static String montarPdfUrl(HttpServletRequest req, int nroc) {
        String token = OrdemCompraPdfTokenCache.gerarToken(nroc);
        String base = req.getScheme() + "://" + req.getServerName()
                + (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort())
                + req.getContextPath();
        return base + "/api/publico/ordem-compra-pdf?token=" + token;
    }

    private static Integer lerInteiro(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
