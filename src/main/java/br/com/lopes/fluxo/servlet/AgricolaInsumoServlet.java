package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AgricolaInsumoDAO;
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
 * front-end web. Autenticação por header X-Agro-Api-Key (ver AuthFilter),
 * não por sessão de login.
 *
 * GET /api/agricola/insumos?safra=NNNN
 *        [&fazenda=NN] [&dataIni=yyyy-MM-dd] [&dataFim=yyyy-MM-dd]
 *        [&insumo=trecho da descrição] [&sessionId=...]
 *
 * Resposta: { "ok": true, "totalEncontrado": N, "truncado": bool,
 *             "data": [ { ...colunas da consulta... }, ... ] }
 *
 * Safra é obrigatória; os demais filtros são opcionais mas recomendados —
 * uma safra inteira tem dezenas de milhares de apontamentos de insumo. O
 * resultado devolvido ao agente é limitado a MAX_LINHAS registros (limite de
 * contexto do modelo de IA); o resultado completo fica no AgroConsultaCache
 * para exportação em Excel pelo front-end do chat.
 */
@WebServlet("/api/agricola/insumos")
public class AgricolaInsumoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AgricolaInsumoServlet.class.getName());
    private static final int MAX_LINHAS = 40;

    private final Gson gson = new Gson();
    private final AgricolaInsumoDAO dao = new AgricolaInsumoDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            String sessionId = req.getParameter("sessionId");
            String negado = ChatPermissaoUtil.verificarAcesso(sessionId, ChatPermissaoUtil.AGRICOLA, "consultas agrícolas");
            if (negado != null) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.print("{\"ok\":false,\"erro\":\"" + negado + "\"}");
                return;
            }

            String safra = req.getParameter("safra");
            if (safra == null || safra.isBlank()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Parâmetro safra é obrigatório\"}");
                return;
            }
            safra = safra.trim();

            Integer fazenda = null;
            String fazendaParam = req.getParameter("fazenda");
            if (fazendaParam != null && !fazendaParam.isBlank()) {
                try {
                    fazenda = Integer.valueOf(fazendaParam.trim());
                } catch (NumberFormatException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.print("{\"ok\":false,\"erro\":\"Parâmetro fazenda deve ser o código numérico da fazenda\"}");
                    return;
                }
            }

            String dataIni = DataParamUtil.normalizar(req.getParameter("dataIni"));
            String dataFim = DataParamUtil.normalizar(req.getParameter("dataFim"));
            if (DataParamUtil.invalida(req.getParameter("dataIni"), dataIni)
             || DataParamUtil.invalida(req.getParameter("dataFim"), dataFim)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Datas devem estar no formato yyyy-MM-dd ou dd/mm/aaaa\"}");
                return;
            }

            String insumo = req.getParameter("insumo");

            List<Map<String, Object>> lista = dao.buscar(safra, fazenda, dataIni, dataFim, insumo);

            // Guarda o resultado COMPLETO para exportação (Excel) pelo
            // front-end do chat — o agente de IA só recebe a versão truncada.
            AgroConsultaCache.guardar(sessionId, montarTitulo(safra, fazenda, dataIni, dataFim, insumo), lista);

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
            LOG.log(Level.SEVERE, "Erro no servlet agricola-insumos", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    private static String montarTitulo(String safra, Integer fazenda,
                                       String dataIni, String dataFim, String insumo) {
        StringBuilder t = new StringBuilder("Insumos — Safra ").append(safra);
        if (fazenda != null) t.append(" · Fazenda ").append(fazenda);
        boolean temIni = dataIni != null && !dataIni.isBlank();
        boolean temFim = dataFim != null && !dataFim.isBlank();
        if (temIni && temFim)      t.append(" · ").append(dataIni.trim()).append(" a ").append(dataFim.trim());
        else if (temIni)           t.append(" · a partir de ").append(dataIni.trim());
        else if (temFim)           t.append(" · até ").append(dataFim.trim());
        if (insumo != null && !insumo.isBlank()) t.append(" · ").append(insumo.trim());
        return t.toString();
    }
}
