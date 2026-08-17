package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
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
 * Reenvio manual de alertas do WhatsApp.
 *
 * Um alerta só sai uma vez por destinatário — quem segura a repetição é a
 * tabela fc_alerta_oc_enviado. Quando é preciso mandar de novo (a mensagem
 * se perdeu, o número mudou, alguém pediu para reenviar), o caminho era
 * apagar a linha no banco pelo terminal. Esta tela faz isso.
 *
 * GET  /api/admin/alerta-reenvio/tipos               -> tipos existentes
 * GET  /api/admin/alerta-reenvio?tipo=X&numeros=a,b  -> o que já foi enviado
 * POST /api/admin/alerta-reenvio/liberar             -> apaga os ids escolhidos
 *        Body: {"ids":[1,2,3]}
 *
 * Só administrador. Procurar e liberar são chamadas separadas de propósito:
 * apagar é irreversível, e quem apaga tem de ter visto antes o que casou.
 */
@WebServlet("/api/admin/alerta-reenvio/*")
public class AlertaReenvioServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AlertaReenvioServlet.class.getName());

    /** Teto por chamada: quem cola uma planilha inteira erra o alvo, não acerta. */
    private static final int MAX_NUMEROS = 100;

    private final Gson gson = new Gson();
    private final AlertaOcPendenteDAO dao = new AlertaOcPendenteDAO();

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

        try {
            if ("/tipos".equals(req.getPathInfo())) {
                json(resp, "{\"ok\":true,\"data\":" + gson.toJson(dao.tipos()) + "}");
                return;
            }

            String tipo = req.getParameter("tipo");
            if (tipo == null || tipo.isBlank()) { erro(resp, 400, "Informe o tipo de alerta"); return; }

            List<String> numeros = separar(req.getParameter("numeros"));
            if (numeros.isEmpty()) { erro(resp, 400, "Informe ao menos um número"); return; }
            if (numeros.size() > MAX_NUMEROS) {
                erro(resp, 400, "Muitos números de uma vez (máximo " + MAX_NUMEROS + ")"); return;
            }

            List<Map<String, Object>> achados = dao.procurar(tipo.trim(), numeros);

            // Quais dos números pedidos não têm registro nenhum: sem isso, um
            // contrato digitado errado se confunde com um que nunca foi
            // enviado, e são coisas bem diferentes.
            Set<String> comRegistro = new LinkedHashSet<>();
            for (Map<String, Object> a : achados) {
                for (String n : numeros) {
                    if (AlertaOcPendenteDAO.mesmoNumero(String.valueOf(a.get("numero")), n)) comRegistro.add(n);
                }
            }
            List<String> semRegistro = new ArrayList<>(numeros);
            semRegistro.removeAll(comRegistro);

            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.add("data", gson.toJsonTree(achados));
            r.add("semRegistro", gson.toJsonTree(semRegistro));
            json(resp, gson.toJson(r));

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao procurar envios de alerta", e);
            erro(resp, 500, e.getMessage() == null ? e.getClass().getName() : e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso restrito a administradores"); return; }
        if (!"/liberar".equals(req.getPathInfo())) { erro(resp, 404, "Não encontrado"); return; }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = req.getReader()) {
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
        }

        List<Integer> ids = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(sb.toString()).getAsJsonObject().getAsJsonArray("ids");
            if (arr == null || arr.size() == 0) { erro(resp, 400, "Nada selecionado"); return; }
            for (JsonElement e : arr) {
                String s = e.getAsString().trim();
                if (!s.matches("\\d+")) { erro(resp, 400, "Registro inválido: " + s); return; }
                ids.add(Integer.parseInt(s));
            }
        } catch (RuntimeException e) {
            erro(resp, 400, "JSON inválido");
            return;
        }

        try {
            HttpSession s = req.getSession(false);
            Object quem = s == null ? null : s.getAttribute("logon");
            int n = dao.liberar(ids);
            LOG.info("Reenvio de alerta liberado por " + quem + ": " + n + " registro(s).");
            json(resp, "{\"ok\":true,\"liberados\":" + n + "}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao liberar reenvio de alerta", e);
            erro(resp, 500, e.getMessage() == null ? e.getClass().getName() : e.getMessage());
        }
    }

    /** Aceita vírgula, ponto-e-vírgula, espaço ou uma por linha — o que o usuário colar. */
    private static List<String> separar(String texto) {
        List<String> fora = new ArrayList<>();
        if (texto == null) return fora;
        Set<String> vistos = new LinkedHashSet<>();
        for (String p : texto.split("[,;\\s]+")) {
            String v = p.trim();
            if (!v.isEmpty()) vistos.add(v);
        }
        fora.addAll(vistos);
        return fora;
    }
}
