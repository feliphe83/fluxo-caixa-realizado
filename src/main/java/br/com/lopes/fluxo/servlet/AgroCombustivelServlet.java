package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AgroCombustivelDAO;
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
 * GET /api/agricola/combustivel?dataIni=yyyy-MM-dd&dataFim=yyyy-MM-dd
 *        [&codEquipamento=N] [&codTipoCliente=N] [&combustivel=trecho]
 *        [&agrupar=equipamento|detalhado] [&sessionId=...]
 *
 * Resposta: { "ok": true, "totalEncontrado": N, "truncado": bool,
 *             "data": [ { ...colunas da consulta... }, ... ] }
 *
 * Período de abastecimento é obrigatório; os demais filtros são opcionais e,
 * se vierem em formato inválido, são apenas ignorados (não bloqueiam a
 * consulta) — o agente de IA às vezes manda um valor vazio/errado num filtro
 * opcional mesmo sem o usuário ter pedido esse filtro.
 *
 * Por padrão (sem agrupar, ou agrupar=combustivel), os litros vêm somados
 * direto do banco por combustível, do maior para o menor consumo — é o
 * formato certo para "quanto de combustível foi abastecido". Use
 * agrupar=equipamento para "top N por consumo" por equipamento.
 * agrupar=detalhado (linha a linha) só quando pedido explicitamente — nesse
 * modo o agente só vê uma amostra truncada (MAX_LINHAS) e não deve tentar
 * somar/ranquear a partir dela.
 *
 * O agente recebe no máximo MAX_LINHAS; o resultado completo fica no
 * AgroConsultaCache para exportação em Excel pelo front-end do chat.
 */
@WebServlet("/api/agricola/combustivel")
public class AgroCombustivelServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AgroCombustivelServlet.class.getName());
    private static final int MAX_LINHAS = 40;

    private final Gson gson = new Gson();
    private final AgroCombustivelDAO dao = new AgroCombustivelDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            String sessionId = req.getParameter("sessionId");
            String negado = ChatPermissaoUtil.verificarAcesso(sessionId, ChatPermissaoUtil.AGRICOLA, "consultas de combustível");
            if (negado != null) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.print("{\"ok\":false,\"erro\":\"" + negado + "\"}");
                return;
            }

            String dataIni = DataParamUtil.normalizar(req.getParameter("dataIni"));
            String dataFim = DataParamUtil.normalizar(req.getParameter("dataFim"));
            if (dataIni == null || dataFim == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Parâmetros dataIni e dataFim são obrigatórios (período de abastecimento), nos formatos yyyy-MM-dd ou dd/mm/aaaa\"}");
                return;
            }

            // Filtros opcionais: valor ausente/vazio/inválido é tratado como
            // "sem filtro" em vez de erro — evita travar a consulta quando o
            // agente manda um parâmetro em branco para algo que o usuário
            // não pediu.
            Integer codEquipamento = lerInteiro(req.getParameter("codEquipamento"));
            Integer codTipoCliente = lerInteiro(req.getParameter("codTipoCliente"));
            String combustivel = req.getParameter("combustivel");
            String agrupar = req.getParameter("agrupar");

            List<Map<String, Object>> lista = dao.buscar(dataIni, dataFim, codEquipamento, codTipoCliente, combustivel, agrupar);

            // Guarda o resultado COMPLETO para exportação (Excel) pelo
            // front-end do chat — o agente de IA só recebe a versão truncada.
            AgroConsultaCache.guardar(sessionId, montarTitulo(dataIni, dataFim, codEquipamento, combustivel, agrupar), lista);

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
            LOG.log(Level.SEVERE, "Erro no servlet agro-combustivel", e);
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

    private static String montarTitulo(String dataIni, String dataFim, Integer codEquipamento,
                                       String combustivel, String agrupar) {
        StringBuilder t = new StringBuilder("Combustível");
        if (agrupar != null && !agrupar.isBlank()) t.append(" por ").append(agrupar.trim());
        t.append(" — ").append(dataIni).append(" a ").append(dataFim);
        if (codEquipamento != null) t.append(" · Equipamento ").append(codEquipamento);
        if (combustivel != null && !combustivel.isBlank()) t.append(" · ").append(combustivel.trim());
        return t.toString();
    }
}
