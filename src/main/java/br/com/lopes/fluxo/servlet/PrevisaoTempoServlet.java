package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.ChatPermissaoUtil;
import br.com.lopes.fluxo.util.PrevisaoTempoUtil;
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
 * GET /api/agricola/previsao-tempo [&local=texto livre] [&dias=1-16] [&sessionId=...]
 *
 * Previsão de chuva e umidade pros próximos dias (padrão 5, direto do dia de
 * hoje), via Open-Meteo (API pública gratuita, sem chave). Diferente da
 * consulta de chuva já existente no Dr. Alfredo (pontos de coleta
 * históricos, dado interno do Oracle) — esta é sobre o FUTURO, de fonte
 * externa.
 *
 * Sem "local", usa Rio Largo/AL (sede da usina) — se o usuário perguntar
 * por outro município, "local" é geocodificado (restrito ao Brasil) pra
 * achar as coordenadas antes de buscar a previsão.
 *
 * Resposta: { "ok": true, "localConsultado": "Rio Largo, Alagoas, Brasil",
 *             "latitude": N, "longitude": N,
 *             "previsao": [ { "data": "yyyy-MM-dd", "precipitacaoMm": N,
 *             "probabilidadeChuvaPct": N, "umidadeMediaPct": N,
 *             "umidadeMaxPct": N, "umidadeMinPct": N, "temperaturaMaxC": N,
 *             "temperaturaMinC": N }, ... ] }
 */
@WebServlet("/api/agricola/previsao-tempo")
public class PrevisaoTempoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(PrevisaoTempoServlet.class.getName());
    private static final int DIAS_PADRAO = 5;
    private static final int DIAS_MAX = 16; // teto da própria API Open-Meteo

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            String sessionId = req.getParameter("sessionId");
            String negado = ChatPermissaoUtil.verificarAcesso(sessionId, ChatPermissaoUtil.AGRICOLA, "previsão do tempo");
            if (negado != null) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.print("{\"ok\":false,\"erro\":\"" + negado + "\"}");
                return;
            }

            String local = req.getParameter("local");
            int dias = lerInteiro(req.getParameter("dias"), DIAS_PADRAO);
            if (dias < 1) dias = 1;
            if (dias > DIAS_MAX) dias = DIAS_MAX;

            PrevisaoTempoUtil.Coordenada coord;
            try {
                coord = PrevisaoTempoUtil.geocodificar(local);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Falha ao consultar localização", e);
            }
            if (coord == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print("{\"ok\":false,\"erro\":\"Não encontrei a localização '" + escapeJson(local) + "'. Tente com o nome do município (e o estado, se ajudar).\"}");
                return;
            }

            List<Map<String, Object>> previsao;
            try {
                previsao = PrevisaoTempoUtil.buscarPrevisao(coord.latitude, coord.longitude, dias);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Falha ao consultar previsão", e);
            }

            JsonObject resultado = new JsonObject();
            resultado.addProperty("ok", true);
            resultado.addProperty("localConsultado", coord.nomeResolvido);
            resultado.addProperty("latitude", coord.latitude);
            resultado.addProperty("longitude", coord.longitude);
            resultado.add("previsao", gson.toJsonTree(previsao));
            out.print(gson.toJson(resultado));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no servlet previsao-tempo", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    private static int lerInteiro(String v, int padrao) {
        if (v == null || v.isBlank()) return padrao;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\n", " ");
    }
}
