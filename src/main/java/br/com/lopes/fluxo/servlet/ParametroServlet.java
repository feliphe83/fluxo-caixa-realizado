package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.ParametroDAO;
import com.google.gson.Gson;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parâmetros gerais da intranet.
 *
 * GET  /api/parametros  -> qualquer usuário logado; as telas precisam ler
 *                          a safra padrão para saber o que abrir
 * POST /api/parametros  -> só administrador; é configuração de empresa,
 *                          não preferência de quem está usando
 */
@WebServlet("/api/parametros")
public class ParametroServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ParametroServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final ParametroDAO dao = new ParametroDAO();

    private boolean admin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("administrador"));
    }

    private String quem(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        Object v = s == null ? null : s.getAttribute("logon");
        return v == null ? "?" : String.valueOf(v);
    }

    private void json(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().print(body);
        resp.getWriter().flush();
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        json(resp, "{\"ok\":false,\"erro\":\"" + String.valueOf(msg).replace("\"", "'").replace("\n", " ") + "\"}");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            json(resp, "{\"ok\":true,\"data\":" + GSON.toJson(dao.todos())
                     + ",\"descricoes\":" + GSON.toJson(dao.descricoes()) + "}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao ler parâmetros", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!admin(req)) { erro(resp, 403, "Apenas administradores podem alterar parâmetros"); return; }
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = req.getReader()) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            }
            JsonObject b = JsonParser.parseString(sb.toString()).getAsJsonObject();

            Map<String, String> valores = new LinkedHashMap<>();
            for (String chave : b.keySet()) {
                if (b.get(chave).isJsonNull()) continue;
                valores.put(chave, b.get(chave).getAsString());
            }
            if (valores.isEmpty()) { erro(resp, 400, "Nada a gravar"); return; }

            // A safra padrão é digitada à mão e vai parar em consulta ao ERP:
            // sem esta checagem, um valor com espaço ou letra viraria uma
            // consulta que não traz nada e ninguém entende por quê.
            String safra = valores.get(ParametroDAO.SAFRA_PADRAO);
            if (safra != null && !safra.trim().matches("\\d{1,4}")) {
                erro(resp, 400, "Safra padrão deve ser um número, como 76"); return;
            }
            String qtde = valores.get(ParametroDAO.SAFRA_QUANTIDADE);
            if (qtde != null) {
                int n;
                try { n = Integer.parseInt(qtde.trim()); } catch (Exception e) { n = -1; }
                if (n < 1 || n > 20) { erro(resp, 400, "Quantidade de safras deve ficar entre 1 e 20"); return; }
            }

            int n = dao.gravar(valores, quem(req));
            json(resp, "{\"ok\":true,\"alterados\":" + n + "}");

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao gravar parâmetros", e);
            erro(resp, 500, e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Corpo inválido ao gravar parâmetros", e);
            erro(resp, 400, "Requisição inválida: " + e.getMessage());
        }
    }
}
