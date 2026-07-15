package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.FinanceiroContasPagarDAO;
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
 * API interna consumida pelo agente de IA (via n8n) — não é usada pelo
 * front-end web. Autenticação por header X-Agro-Api-Key (ver AuthFilter) e
 * categoria chat_financeiro da sessão de chat.
 *
 * GET /api/financeiro/contas-apagar?dataIniVcto=yyyy-MM-dd&dataFimVcto=yyyy-MM-dd
 *        [&dataIniEntrada=yyyy-MM-dd&dataFimEntrada=yyyy-MM-dd]
 *        [&fornecedor=trecho do nome] [&sessionId=...]
 *
 * Resposta: { "ok": true, "totalEncontrado": N, "truncado": bool,
 *             "data": [ { ...colunas da consulta... }, ... ] }
 *
 * Período de vencimento é obrigatório. As linhas são largas (inclui até 10
 * alterações de vencimento), então o agente recebe no máximo MAX_LINHAS; o
 * resultado completo fica no AgroConsultaCache para exportação em Excel.
 */
@WebServlet("/api/financeiro/contas-apagar")
public class FinanceiroContasPagarServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(FinanceiroContasPagarServlet.class.getName());
    private static final int MAX_LINHAS = 30;

    private final Gson gson = new Gson();
    private final FinanceiroContasPagarDAO dao = new FinanceiroContasPagarDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            String sessionId = req.getParameter("sessionId");
            String negado = ChatPermissaoUtil.verificarAcesso(sessionId, ChatPermissaoUtil.FINANCEIRO, "consultas financeiras");
            if (negado != null) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.print("{\"ok\":false,\"erro\":\"" + negado + "\"}");
                return;
            }

            String dataIniVcto = DataParamUtil.normalizar(req.getParameter("dataIniVcto"));
            String dataFimVcto = DataParamUtil.normalizar(req.getParameter("dataFimVcto"));
            if (dataIniVcto == null || dataFimVcto == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Parâmetros dataIniVcto e dataFimVcto são obrigatórios (período de vencimento), nos formatos yyyy-MM-dd ou dd/mm/aaaa\"}");
                return;
            }

            String dataIniEntrada = DataParamUtil.normalizar(req.getParameter("dataIniEntrada"));
            String dataFimEntrada = DataParamUtil.normalizar(req.getParameter("dataFimEntrada"));
            if (DataParamUtil.invalida(req.getParameter("dataIniEntrada"), dataIniEntrada)
             || DataParamUtil.invalida(req.getParameter("dataFimEntrada"), dataFimEntrada)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Datas devem estar no formato yyyy-MM-dd ou dd/mm/aaaa\"}");
                return;
            }
            boolean temIniEntrada = dataIniEntrada != null;
            boolean temFimEntrada = dataFimEntrada != null;
            if (temIniEntrada != temFimEntrada) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Informe dataIniEntrada e dataFimEntrada juntas (ou nenhuma)\"}");
                return;
            }

            String fornecedor = req.getParameter("fornecedor");

            List<Map<String, Object>> lista = dao.buscar(dataIniVcto, dataFimVcto,
                    dataIniEntrada, dataFimEntrada, fornecedor);

            // Guarda o resultado COMPLETO para exportação (Excel) pelo
            // front-end do chat — o agente de IA só recebe a versão truncada.
            AgroConsultaCache.guardar(sessionId,
                    montarTitulo(dataIniVcto, dataFimVcto, dataIniEntrada, dataFimEntrada, fornecedor), lista);

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
            LOG.log(Level.SEVERE, "Erro no servlet financeiro-contas-apagar", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    private static String montarTitulo(String vi, String vf, String ei, String ef, String fornecedor) {
        StringBuilder t = new StringBuilder("Contas a Pagar — Vencimento ")
                .append(vi.trim()).append(" a ").append(vf.trim());
        if (ei != null && !ei.isBlank()) t.append(" · Entrada ").append(ei.trim()).append(" a ").append(ef.trim());
        if (fornecedor != null && !fornecedor.isBlank()) t.append(" · ").append(fornecedor.trim());
        return t.toString();
    }
}
