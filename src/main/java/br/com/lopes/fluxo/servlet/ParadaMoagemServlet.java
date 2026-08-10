package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.agendamento.MensagemParadaMoagem;
import br.com.lopes.fluxo.dao.ParadaMoagemDAO;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controle de paradas da moagem.
 *
 * GET  /api/parada/situacao   -> a parada aberta (ou null), partes e destinatários
 * GET  /api/parada/paradas    -> histórico
 * POST /api/parada/parar      -> abre a parada e avisa no WhatsApp
 * POST /api/parada/retornar   -> fecha a parada e avisa o retorno
 * GET/POST /api/parada/config -> partes e destinatários (administrador)
 */
@WebServlet("/api/parada/*")
public class ParadaMoagemServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ParadaMoagemServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final ParadaMoagemDAO dao = new ParadaMoagemDAO();

    private boolean logado(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && s.getAttribute("logon") != null;
    }

    private boolean admin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("administrador"));
    }

    private long idUsuario(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        Object v = s == null ? null : s.getAttribute("idUsuario");
        return v instanceof Number n ? n.longValue() : 0;
    }

    private String nome(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        Object v = s == null ? null : s.getAttribute("nome");
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
        json(resp, "{\"ok\":false,\"erro\":" + GSON.toJson(String.valueOf(msg)) + "}");
    }

    private String rota(HttpServletRequest req) {
        String p = req.getPathInfo();
        return p == null ? "" : p.replaceAll("^/|/$", "");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!logado(req)) { erro(resp, 401, "Sessão expirada"); return; }
        try {
            switch (rota(req)) {
                case "situacao" -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("ok", true);
                    o.addProperty("admin", admin(req));
                    o.add("aberta", GSON.toJsonTree(dao.aberta()));
                    o.add("partes", GSON.toJsonTree(dao.partes(true)));
                    o.add("destinatarios", GSON.toJsonTree(dao.destinatarios()));
                    json(resp, GSON.toJson(o));
                }
                case "paradas" -> json(resp, "{\"ok\":true,\"data\":" + GSON.toJson(
                        dao.listar(req.getParameter("de"), req.getParameter("ate"), 300)) + "}");
                case "config" -> {
                    if (!admin(req)) { erro(resp, 403, "Apenas administradores"); return; }
                    json(resp, "{\"ok\":true,\"partes\":" + GSON.toJson(dao.partes(false))
                             + ",\"destinatarios\":" + GSON.toJson(dao.destinatarios()) + "}");
                }
                default -> erro(resp, 404, "Rota desconhecida");
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro no controle de paradas", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!logado(req)) { erro(resp, 401, "Sessão expirada"); return; }
        try {
            JsonObject b = corpo(req);
            switch (rota(req)) {
                case "parar"    -> parar(req, resp, b);
                case "retornar" -> retornar(req, resp, b);
                case "config"   -> {
                    if (!admin(req)) { erro(resp, 403, "Apenas administradores"); return; }
                    config(resp, b);
                }
                default -> erro(resp, 404, "Rota desconhecida");
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro no controle de paradas", e);
            erro(resp, 500, e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Corpo inválido no controle de paradas", e);
            erro(resp, 400, "Requisição inválida: " + e.getMessage());
        }
    }

    // ── Parar ─────────────────────────────────────────────────────────────

    private void parar(HttpServletRequest req, HttpServletResponse resp, JsonObject b)
            throws SQLException, IOException {
        String inicio = texto(b, "inicio");
        String motivo = texto(b, "motivo");
        if (inicio == null || MensagemParadaMoagem.instante(inicio) == null) {
            erro(resp, 400, "Informe a data e a hora em que parou"); return;
        }
        if (motivo == null) { erro(resp, 400, "Informe o motivo da parada"); return; }

        int id = dao.abrir(inicio.replace('T', ' '), motivo, texto(b, "parte"), texto(b, "previsao"),
                           idUsuario(req), nome(req));
        if (id < 0) {
            // Duas pessoas registrando a mesma parada mandariam dois avisos ao
            // grupo, e o segundo faria todo mundo achar que parou de novo.
            erro(resp, 409, "Já existe uma parada em aberto. Registre o retorno antes de abrir outra.");
            return;
        }

        Map<String, Object> p = dao.buscar(id);
        String resultado = avisar(MensagemParadaMoagem.parada(p));
        dao.registrarAviso(id, false, resultado);

        json(resp, "{\"ok\":true,\"id\":" + id + ",\"aviso\":" + GSON.toJson(resultado)
                 + ",\"mensagem\":" + GSON.toJson(MensagemParadaMoagem.parada(p)) + "}");
    }

    // ── Retornar ──────────────────────────────────────────────────────────

    private void retornar(HttpServletRequest req, HttpServletResponse resp, JsonObject b)
            throws SQLException, IOException {
        Map<String, Object> aberta = dao.aberta();
        if (aberta == null) { erro(resp, 409, "Não há parada em aberto."); return; }

        String retorno = texto(b, "retorno");
        if (retorno == null || MensagemParadaMoagem.instante(retorno) == null) {
            erro(resp, 400, "Informe a data e a hora do retorno"); return;
        }
        // Retorno antes do início daria tempo parado negativo, e o aviso sairia
        // dizendo que a moagem voltou antes de parar.
        if (MensagemParadaMoagem.instante(retorno)
                .isBefore(MensagemParadaMoagem.instante(String.valueOf(aberta.get("inicio"))))) {
            erro(resp, 400, "O retorno não pode ser anterior ao início da parada."); return;
        }

        int id = ((Number) aberta.get("id")).intValue();
        Map<String, Object> p = dao.fechar(id, retorno.replace('T', ' '), idUsuario(req), nome(req));
        if (p == null) { erro(resp, 409, "Esta parada já foi encerrada por outra pessoa."); return; }

        String resultado = avisar(MensagemParadaMoagem.retorno(p));
        dao.registrarAviso(id, true, resultado);

        json(resp, "{\"ok\":true,\"id\":" + id + ",\"aviso\":" + GSON.toJson(resultado)
                 + ",\"mensagem\":" + GSON.toJson(MensagemParadaMoagem.retorno(p)) + "}");
    }

    /**
     * Manda para todo mundo da lista.
     *
     * Nunca lança: a parada já está gravada, e falhar o WhatsApp não pode
     * desfazer o registro nem impedir a tela de responder. O que aconteceu
     * fica escrito na própria parada e aparece na tela de quem registrou.
     */
    private String avisar(String texto) {
        List<Map<String, Object>> lista;
        try {
            lista = dao.destinatarios();
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Não foi possível ler os destinatários da parada", e);
            return "Falha ao ler os destinatários: " + e.getMessage();
        }
        if (lista.isEmpty()) return "Nenhum destinatário cadastrado — ninguém foi avisado.";

        int ok = 0;
        List<String> falhas = new ArrayList<>();
        for (Map<String, Object> u : lista) {
            String tel = String.valueOf(u.get("telefone"));
            String nome = String.valueOf(u.get("nome"));
            if (tel == null || tel.isBlank() || "null".equals(tel)) {
                falhas.add(nome + " (sem telefone)");
                continue;
            }
            try {
                EvolutionApiUtil.enviarTexto(tel, texto);
                ok++;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Falha ao avisar " + nome + " sobre a parada", e);
                falhas.add(nome);
            }
        }
        if (falhas.isEmpty()) return "Enviado para " + ok + " pessoa(s).";
        return "Enviado para " + ok + " de " + lista.size()
             + ". Não foi para: " + String.join(", ", falhas) + ".";
    }

    // ── Configuração ──────────────────────────────────────────────────────

    private void config(HttpServletResponse resp, JsonObject b) throws SQLException, IOException {
        if (b.has("destinatarios") && b.get("destinatarios").isJsonArray()) {
            List<Integer> ids = new ArrayList<>();
            for (JsonElement el : b.getAsJsonArray("destinatarios")) {
                try { ids.add(el.getAsInt()); } catch (Exception ignore) {}
            }
            dao.gravarDestinatarios(ids);
        }
        if (b.has("partes") && b.get("partes").isJsonArray()) {
            JsonArray arr = b.getAsJsonArray("partes");
            int ordem = 0;
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String d = texto(o, "descricao");
                if (d == null) continue;
                Integer id = o.has("id") && !o.get("id").isJsonNull() ? o.get("id").getAsInt() : null;
                String ativo = "N".equalsIgnoreCase(texto(o, "ativo")) ? "N" : "S";
                dao.salvarParte(id, d, ativo, ++ordem);
            }
        }
        json(resp, "{\"ok\":true}");
    }

    // ── Auxiliares ────────────────────────────────────────────────────────

    private JsonObject corpo(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = req.getReader()) {
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
        }
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    private static String texto(JsonObject o, String c) {
        if (!o.has(c) || o.get(c).isJsonNull()) return null;
        String v = o.get(c).getAsString();
        return v.isBlank() ? null : v.trim();
    }
}
