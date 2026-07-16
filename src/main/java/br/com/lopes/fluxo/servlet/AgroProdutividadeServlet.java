package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AgroProdutividadeDAO;
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
 * AuthFilter) e categoria chat_agricola da sessão de chat.
 *
 * GET /api/agricola/produtividade?safra=NNNN
 *        [&fazenda=N] [&talhao=N] [&frente=N] [&tipoCorte=N] [&variedade=trecho]
 *        [&dataIni=yyyy-MM-dd&dataFim=yyyy-MM-dd]
 *        [&agrupar=fazenda|talhao|variedade|detalhado] [&sessionId=...]
 *
 * Resposta: { "ok": true, "totalEncontrado": N, "truncado": bool,
 *             "data": [ { ...colunas da consulta... }, ... ] }
 *
 * Safra é obrigatória; os demais filtros são opcionais e, se vierem em
 * formato inválido, são apenas ignorados (não bloqueiam a consulta).
 *
 * Por padrão (sem agrupar, ou agrupar=geral), devolve um único total (área,
 * produção, TCH/ATR médios ponderados) da safra filtrada — é o formato
 * certo para "qual foi a produtividade da safra". Com agrupar=fazenda,
 * talhao ou variedade, a mesma soma sai quebrada por dimensão — use para
 * "produtividade por fazenda" ou "top N mais produtivo". agrupar=detalhado
 * (linha a linha, por ordem de colheita) só quando pedido explicitamente —
 * nesse modo o agente só vê uma amostra truncada e não deve tentar somar
 * sozinho a partir dela.
 *
 * O agente recebe no máximo MAX_LINHAS; o resultado completo fica no
 * AgroConsultaCache para exportação em Excel pelo front-end do chat.
 */
@WebServlet("/api/agricola/produtividade")
public class AgroProdutividadeServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AgroProdutividadeServlet.class.getName());
    private static final int MAX_LINHAS = 40;

    private final Gson gson = new Gson();
    private final AgroProdutividadeDAO dao = new AgroProdutividadeDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            String sessionId = req.getParameter("sessionId");
            String negado = ChatPermissaoUtil.verificarAcesso(sessionId, ChatPermissaoUtil.AGRICOLA, "consultas de produtividade");
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

            // Filtros opcionais: valor ausente/vazio/inválido é tratado como
            // "sem filtro" em vez de erro.
            Integer codFazenda = lerInteiro(req.getParameter("fazenda"));
            Integer codTalhao = lerInteiro(req.getParameter("talhao"));
            Integer codFrente = lerInteiro(req.getParameter("frente"));
            Integer codTipoCorte = lerInteiro(req.getParameter("tipoCorte"));
            String variedade = req.getParameter("variedade");
            String dataIni = DataParamUtil.normalizar(req.getParameter("dataIni"));
            String dataFim = DataParamUtil.normalizar(req.getParameter("dataFim"));
            String agrupar = req.getParameter("agrupar");

            List<Map<String, Object>> lista = dao.buscar(safra, codFazenda, codTalhao, codFrente,
                    codTipoCorte, variedade, dataIni, dataFim, agrupar);

            // Guarda o resultado COMPLETO para exportação (Excel) pelo
            // front-end do chat — o agente de IA só recebe a versão truncada.
            AgroConsultaCache.guardar(sessionId, montarTitulo(safra, codFazenda, agrupar), lista);

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
            LOG.log(Level.SEVERE, "Erro no servlet agro-produtividade", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    private static Integer lerInteiro(String v) {
        if (v == null || v.isBlank() || !v.trim().matches("\\d+")) return null;
        return Integer.valueOf(v.trim());
    }

    private static String montarTitulo(String safra, Integer codFazenda, String agrupar) {
        StringBuilder t = new StringBuilder("Produtividade");
        if (agrupar != null && !agrupar.isBlank()) t.append(" por ").append(agrupar.trim());
        t.append(" — Safra ").append(safra);
        if (codFazenda != null) t.append(" · Fazenda ").append(codFazenda);
        return t.toString();
    }
}
