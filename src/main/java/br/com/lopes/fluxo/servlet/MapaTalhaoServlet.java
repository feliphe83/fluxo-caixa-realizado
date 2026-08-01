package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.MapaTalhaoDAO;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dados do mapa de talhões.
 *
 * GET /api/mapa-talhoes/safras        -> safras cadastradas (a atual marcada)
 * GET /api/mapa-talhoes?safra=NNNN    -> um registro por talhão da safra
 *
 * Fora de /api/agricola/* de propósito: aquele prefixo é reservado às
 * ferramentas do chatbot e exige a chave X-Agro-Api-Key no AuthFilter. Aqui
 * a autenticação é a sessão normal do navegador.
 */
@WebServlet("/api/mapa-talhoes/*")
public class MapaTalhaoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(MapaTalhaoServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final MapaTalhaoDAO dao = new MapaTalhaoDAO();

    private void json(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().print(body);
        resp.getWriter().flush();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        try {
            if ("/safras".equals(path)) {
                json(resp, "{\"ok\":true,\"data\":" + GSON.toJson(dao.safras()) + "}");
                return;
            }

            String safra = req.getParameter("safra");
            if (safra == null || !safra.matches("\\d{1,10}")) {
                resp.setStatus(400);
                json(resp, "{\"ok\":false,\"erro\":\"Informe a safra\"}");
                return;
            }

            List<Map<String, Object>> talhoes = dao.buscar(safra);
            json(resp, "{\"ok\":true,\"safra\":\"" + safra + "\",\"data\":" + GSON.toJson(talhoes) + "}");

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no mapa de talhões", e);
            resp.setStatus(500);
            // Desembrulha até a causa raiz: o DAO envolve a SQLException num
            // RuntimeException, e sem isso a mensagem do Oracle (ORA-...) —
            // a única que diz o que de fato quebrou — não chega na tela.
            Throwable raiz = e;
            while (raiz.getCause() != null && raiz.getCause() != raiz) raiz = raiz.getCause();
            String msg = raiz.getMessage() == null ? raiz.getClass().getSimpleName() : raiz.getMessage();
            json(resp, "{\"ok\":false,\"erro\":\"" + msg.replace("\"", "'").replace("\n", " ") + "\"}");
        }
    }
}
