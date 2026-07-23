package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.OrdemCompraDAO;
import br.com.lopes.fluxo.util.AgroConsultaCache;
import br.com.lopes.fluxo.util.ChatPermissaoUtil;
import br.com.lopes.fluxo.util.DataParamUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
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
 *        [&sessionId=...]
 *
 * Resposta: { "ok": true, "totalEncontrado": N, "truncado": bool,
 *             "data": [ { ...colunas da consulta... }, ... ] }
 *
 * Período de vencimento (da parcela vinculada à ordem de compra, não a data
 * da própria ordem) é obrigatório. O agente recebe no máximo MAX_LINHAS; o
 * resultado completo fica no AgroConsultaCache para exportação em Excel
 * pelo front-end do chat.
 */
@WebServlet("/api/financeiro/ordem-compra")
public class OrdemCompraServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(OrdemCompraServlet.class.getName());
    private static final int MAX_LINHAS = 30;

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

            List<Map<String, Object>> lista = dao.buscar(dataIniVcto, dataFimVcto);

            // Guarda o resultado COMPLETO para exportação (Excel) pelo
            // front-end do chat — o agente de IA só recebe a versão truncada.
            AgroConsultaCache.guardar(sessionId, montarTitulo(dataIniVcto, dataFimVcto), lista);

            int total = lista.size();
            boolean truncado = total > MAX_LINHAS;
            List<Map<String, Object>> listaLimitada = truncado ? lista.subList(0, MAX_LINHAS) : lista;

            JsonObject resultado = new JsonObject();
            resultado.addProperty("ok", true);
            resultado.addProperty("totalEncontrado", total);
            resultado.addProperty("truncado", truncado);
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

    private static String montarTitulo(String dataIniVcto, String dataFimVcto) {
        return "Ordem de Compra — Vencimento " + dataIniVcto.trim() + " a " + dataFimVcto.trim();
    }
}
