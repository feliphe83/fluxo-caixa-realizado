package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AgricolaTalhaoDAO;
import br.com.lopes.fluxo.util.AgroConsultaCache;
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
 * GET /api/agricola/talhoes?safra=NNNN
 *
 * Resposta: { "ok": true, "totalEncontrado": N, "truncado": bool,
 *             "data": [ { ...colunas da consulta... }, ... ] }
 *
 * O resultado é limitado a MAX_LINHAS registros — uma safra inteira pode ter
 * centenas de talhões, e devolver tudo de uma vez estoura o limite de
 * contexto do modelo de IA (já aconteceu com Groq e OpenAI durante os
 * testes). Se truncado, o agente é instruído (via system prompt) a avisar
 * o usuário e sugerir uma pergunta mais específica.
 */
@WebServlet("/api/agricola/talhoes")
public class AgricolaTalhaoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AgricolaTalhaoServlet.class.getName());
    private static final int MAX_LINHAS = 40;

    private final Gson gson = new Gson();
    private final AgricolaTalhaoDAO dao = new AgricolaTalhaoDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            String safra = req.getParameter("safra");
            if (safra == null || safra.isBlank()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Parâmetro safra é obrigatório\"}");
                return;
            }

            List<Map<String, Object>> lista = dao.buscar(safra.trim());

            // Guarda o resultado COMPLETO para exportação (Excel/PDF) pelo
            // front-end do chat — o agente de IA só recebe a versão truncada.
            String sessionId = req.getParameter("sessionId");
            AgroConsultaCache.guardar(sessionId, "Talhões — Safra " + safra.trim(), lista);

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
            LOG.log(Level.SEVERE, "Erro no servlet agricola-talhoes", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }
}
