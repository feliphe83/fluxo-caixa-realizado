package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.ServicoCorteDAO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administração dos tipos de serviço considerados CORTE DE CANA no Controle
 * de Serviços Agrícola.
 *
 * GET  /api/servicos-corte              -> os configurados
 * GET  /api/servicos-corte/disponiveis  -> todos os tipos de serviço do ERP
 * POST /api/servicos-corte              -> grava a lista inteira
 *        Body: {"codigos":[5558,5554,...]}
 *
 * Só administrador. A consulta do relatório é montada com esses códigos
 * concatenados em cláusulas IN, então quem escreve aqui escreve SQL — e é
 * por isso que gravar exige o mesmo nível de acesso das outras telas de
 * cadastro, e que todo código passa por Integer.parseInt antes de entrar.
 */
@WebServlet("/api/servicos-corte/*")
public class ServicoCorteServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ServicoCorteServlet.class.getName());

    private final Gson gson = new Gson();
    private final ServicoCorteDAO dao = new ServicoCorteDAO();

    private boolean isAdmin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("administrador"));
    }

    private void json(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().print(body);
        resp.getWriter().flush();
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        json(resp, "{\"ok\":false,\"erro\":\"" + msg.replace("\"", "'").replace("\n", " ") + "\"}");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Cache-Control", "no-store");
        if (!isAdmin(req)) { erro(resp, 403, "Acesso restrito a administradores"); return; }

        String path = req.getPathInfo();
        try {
            List<Map<String, Object>> lista = "/disponiveis".equals(path)
                    ? dao.disponiveis()
                    : dao.configurados();

            JsonArray arr = new JsonArray();
            for (Map<String, Object> m : lista) {
                JsonObject o = new JsonObject();
                o.addProperty("cod", (Integer) m.get("cod"));
                o.addProperty("descricao", (String) m.get("descricao"));
                arr.add(o);
            }
            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.add("data", arr);
            json(resp, gson.toJson(r));

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao listar serviços de corte", e);
            erro(resp, 500, e.getMessage() == null ? e.getClass().getName() : e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso restrito a administradores"); return; }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = req.getReader()) {
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
        }

        // LinkedHashSet: a tela pode mandar o mesmo código duas vezes (dois
        // cliques, lista colada com repetição) e cod_tiposervico é chave
        // primária — deixar passar viraria erro de duplicidade no meio do
        // INSERT, com a tabela já apagada.
        Set<Integer> codigos = new LinkedHashSet<>();
        try {
            JsonElement raiz = JsonParser.parseString(sb.toString());
            JsonArray arr = raiz.getAsJsonObject().getAsJsonArray("codigos");
            if (arr == null) { erro(resp, 400, "Informe a lista de códigos"); return; }
            for (JsonElement e : arr) {
                String s = e.getAsString().trim();
                if (s.isEmpty()) continue;
                if (!s.matches("\\d+")) { erro(resp, 400, "Código inválido: " + s); return; }
                codigos.add(Integer.parseInt(s));
            }
        } catch (RuntimeException e) {
            erro(resp, 400, "JSON inválido");
            return;
        }

        try {
            HttpSession s = req.getSession(false);
            Object nome = s == null ? null : s.getAttribute("nome");
            dao.gravar(new ArrayList<>(codigos), nome == null ? "?" : String.valueOf(nome));
            json(resp, "{\"ok\":true,\"total\":" + codigos.size() + "}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao gravar serviços de corte", e);
            erro(resp, 500, e.getMessage() == null ? e.getClass().getName() : e.getMessage());
        }
    }
}
