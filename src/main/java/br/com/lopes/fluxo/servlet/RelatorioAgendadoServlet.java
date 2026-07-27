package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.RelatorioAgendadoDAO;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administração dos agendamentos de envio de relatório por WhatsApp (tela
 * Administração → Relatórios WhatsApp). Só administradores acessam.
 *
 * GET    /api/admin/relatorio-agendado             -> lista todos
 * GET    /api/admin/relatorio-agendado/usuarios     -> usuários ativos (pra montar o multi-select de destinatários)
 * GET    /api/admin/relatorio-agendado/{id}         -> detalhe (com destinatários)
 * POST   /api/admin/relatorio-agendado              -> cria
 * PUT    /api/admin/relatorio-agendado/{id}         -> atualiza
 * PUT    /api/admin/relatorio-agendado/{id}/ativo   -> liga/desliga (body {ativo:true|false})
 * DELETE /api/admin/relatorio-agendado/{id}         -> exclui
 *
 * Body (POST/PUT): { tipoRelatorio, nome, diaSemana (1-7, ver DayOfWeek),
 *                     horaEnvio ("HH:mm"), parametros (objeto livre,
 *                     específico do tipoRelatorio), destinatarios:[idUsuario,...] }
 *
 * Mesmo padrão de servlet "satélite" de /api/admin/* que De-Para Tipo
 * Serviço/Classe Operativa já usam neste projeto (mapeamento mais específico
 * vence o de AdminServlet, sem precisar centralizar tudo lá).
 */
@WebServlet("/api/admin/relatorio-agendado/*")
public class RelatorioAgendadoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(RelatorioAgendadoServlet.class.getName());
    private final Gson gson = new Gson();
    private final RelatorioAgendadoDAO dao = new RelatorioAgendadoDAO();

    private boolean isAdmin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("administrador"));
    }

    private void json(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().print(body);
        resp.getWriter().flush();
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        json(resp, "{\"ok\":false,\"erro\":\"" + msg.replace("\"", "'").replace("\n", " ") + "\"}");
    }

    private JsonObject lerBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = req.getReader()) {
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        String path = req.getPathInfo();
        try {
            if (path == null || path.equals("/")) {
                List<Map<String, Object>> lista = dao.listar();
                JsonArray arr = new JsonArray();
                for (Map<String, Object> m : lista) arr.add(comParametrosParseados(m));
                json(resp, "{\"ok\":true,\"data\":" + arr + "}");
            } else if ("/usuarios".equals(path)) {
                json(resp, "{\"ok\":true,\"data\":" + gson.toJson(dao.listarUsuariosAtivos()) + "}");
            } else {
                int id = Integer.parseInt(path.substring(1));
                Map<String, Object> m = dao.buscarPorId(id);
                if (m == null) { erro(resp, 404, "Agendamento não encontrado"); return; }
                json(resp, "{\"ok\":true,\"data\":" + comParametrosParseados(m) + "}");
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao consultar relatório agendado", e);
            erro(resp, 500, e.getMessage());
        }
    }

    private JsonObject comParametrosParseados(Map<String, Object> m) {
        JsonObject o = gson.toJsonTree(m).getAsJsonObject();
        String parametrosStr = m.get("parametros") == null ? null : String.valueOf(m.get("parametros"));
        JsonElement parsed = (parametrosStr == null || parametrosStr.isBlank())
                ? new JsonObject()
                : JsonParser.parseString(parametrosStr);
        o.add("parametros", parsed);
        return o;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        try {
            JsonObject b = lerBody(req);
            if (!b.has("tipoRelatorio") || b.get("tipoRelatorio").getAsString().isBlank()) {
                erro(resp, 400, "Informe o tipo de relatório");
                return;
            }
            String erroValidacao = validar(b);
            if (erroValidacao != null) { erro(resp, 400, erroValidacao); return; }

            long idUsuarioCriacao = (long) req.getSession(false).getAttribute("idUsuario");
            int id = dao.criar(
                    b.get("tipoRelatorio").getAsString(),
                    b.get("nome").getAsString(),
                    b.get("diaSemana").getAsInt(),
                    b.get("horaEnvio").getAsString() + ":00",
                    gson.toJson(b.get("parametros")),
                    idUsuarioCriacao,
                    lerDestinatarios(b));
            json(resp, "{\"ok\":true,\"id\":" + id + "}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao criar relatório agendado", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        String path = req.getPathInfo();
        if (path == null || path.equals("/")) { erro(resp, 400, "Informe o id na URL"); return; }
        try {
            JsonObject b = lerBody(req);
            if (path.endsWith("/ativo")) {
                int id = Integer.parseInt(path.substring(1, path.length() - "/ativo".length()));
                dao.alternarAtivo(id, b.get("ativo").getAsBoolean());
                json(resp, "{\"ok\":true}");
                return;
            }

            int id = Integer.parseInt(path.substring(1));
            String erroValidacao = validar(b);
            if (erroValidacao != null) { erro(resp, 400, erroValidacao); return; }

            dao.atualizar(
                    id,
                    b.get("nome").getAsString(),
                    b.get("diaSemana").getAsInt(),
                    b.get("horaEnvio").getAsString() + ":00",
                    gson.toJson(b.get("parametros")),
                    lerDestinatarios(b));
            json(resp, "{\"ok\":true}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao atualizar relatório agendado", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        String path = req.getPathInfo();
        if (path == null || path.equals("/")) { erro(resp, 400, "Informe o id na URL"); return; }
        try {
            dao.excluir(Integer.parseInt(path.substring(1)));
            json(resp, "{\"ok\":true}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao excluir relatório agendado", e);
            erro(resp, 500, e.getMessage());
        }
    }

    private List<Integer> lerDestinatarios(JsonObject b) {
        List<Integer> lista = new ArrayList<>();
        if (b.has("destinatarios") && b.get("destinatarios").isJsonArray()) {
            for (JsonElement e : b.get("destinatarios").getAsJsonArray()) lista.add(e.getAsInt());
        }
        return lista;
    }

    private String validar(JsonObject b) {
        if (!b.has("nome") || b.get("nome").getAsString().isBlank()) return "Informe o nome do agendamento";
        if (!b.has("diaSemana")) return "Informe o dia da semana";
        int dia = b.get("diaSemana").getAsInt();
        if (dia < 1 || dia > 7) return "Dia da semana inválido (use 1=segunda a 7=domingo)";
        if (!b.has("horaEnvio") || b.get("horaEnvio").getAsString().isBlank()) return "Informe a hora de envio";
        try {
            LocalTime.parse(b.get("horaEnvio").getAsString());
        } catch (Exception e) {
            return "Hora de envio inválida (use HH:mm)";
        }
        if (!b.has("destinatarios") || !b.get("destinatarios").isJsonArray() || b.get("destinatarios").getAsJsonArray().isEmpty()) {
            return "Escolha ao menos um destinatário";
        }
        return null;
    }
}
