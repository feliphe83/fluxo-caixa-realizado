package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AcessoExternoDAO;
import br.com.lopes.fluxo.dao.ManobraDAO;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Boletim Diário de Operação — a API do módulo de manobra.
 *
 * Atende dois perfis pela mesma porta: a empresa de fora, que só enxerga e só
 * lança o que é dela, e o funcionário da usina, que enxerga tudo. Quem é quem
 * sai da sessão, nunca do corpo da requisição.
 *
 * GET  /api/manobra/inicio      -> o que a tela precisa para abrir
 * GET  /api/manobra/boletins    -> boletins (do próprio usuário, se externo)
 * GET  /api/manobra/boletim?id= -> um boletim com os trechos
 * POST /api/manobra/boletins    -> grava um boletim
 */
@WebServlet("/api/manobra/*")
public class ManobraServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ManobraServlet.class.getName());
    private static final Gson GSON = new Gson();
    private static final int LIMITE_LISTA = 300;

    private final ManobraDAO dao = new ManobraDAO();
    private final AcessoExternoDAO acesso = new AcessoExternoDAO();

    /** id do usuário externo, ou null quando é gente de dentro. */
    private Integer idExterno(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null || !Boolean.TRUE.equals(s.getAttribute("externo"))) return null;
        Object v = s.getAttribute("idExterno");
        return v instanceof Number n ? n.intValue() : null;
    }

    private boolean logado(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && (s.getAttribute("logon") != null
                          || Boolean.TRUE.equals(s.getAttribute("externo")));
    }

    private Object atr(HttpServletRequest req, String nome) {
        HttpSession s = req.getSession(false);
        return s == null ? null : s.getAttribute(nome);
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
        Integer externo = idExterno(req);
        try {
            switch (rota(req)) {
                case "inicio" -> inicio(req, resp, externo);
                case "boletins" -> json(resp, "{\"ok\":true,\"data\":" + GSON.toJson(
                        dao.listar(externo, req.getParameter("de"), req.getParameter("ate"), LIMITE_LISTA)) + "}");
                case "boletim" -> {
                    String id = req.getParameter("id");
                    if (id == null || !id.matches("\\d+")) { erro(resp, 400, "Parâmetro id é obrigatório"); return; }
                    Map<String, Object> b = dao.buscar(Integer.parseInt(id), externo);
                    if (b == null) { erro(resp, 404, "Boletim não encontrado"); return; }
                    json(resp, "{\"ok\":true,\"data\":" + GSON.toJson(b) + "}");
                }
                default -> erro(resp, 404, "Rota desconhecida");
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro no módulo de manobra", e);
            erro(resp, 500, raiz(e));
        }
    }

    /** Tudo que a tela precisa para abrir, numa chamada só. */
    private void inicio(HttpServletRequest req, HttpServletResponse resp, Integer externo)
            throws SQLException, IOException {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("externo", externo != null);
        o.addProperty("nome", String.valueOf(atr(req, "nome")));
        o.addProperty("matricula", atr(req, "matricula") == null ? "" : String.valueOf(atr(req, "matricula")));
        o.add("operacoes", GSON.toJsonTree(listaOperacoes()));

        String hoje = LocalDate.now().toString();
        o.addProperty("hoje", hoje);

        if (externo != null) {
            o.addProperty("empresa", String.valueOf(atr(req, "razaoSocial")));
            o.add("equipamentos", GSON.toJsonTree(acesso.equipamentosDe(externo)));
            o.add("contratos",    GSON.toJsonTree(acesso.contratosDe(externo)));
            // A tela precisa saber, antes de desenhar, se o boletim do dia já
            // existe — é o que decide se ela pede justificativa de extra.
            o.addProperty("proximaSeq", dao.proximaSeq(externo, hoje));
        }

        // As fazendas vêm do Oracle e é a única parte que pode demorar ou
        // falhar. Falhar aqui não pode derrubar a tela inteira: sem a lista,
        // a pessoa ainda vê seus boletins anteriores.
        try {
            o.add("fazendas", GSON.toJsonTree(dao.fazendas()));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Não foi possível listar as fazendas", e);
            o.add("fazendas", new JsonArray());
            o.addProperty("erroFazendas", "Não foi possível carregar as fazendas agora.");
        }
        json(resp, GSON.toJson(o));
    }

    private static List<Map<String, Object>> listaOperacoes() {
        List<Map<String, Object>> l = new ArrayList<>();
        for (String[] op : ManobraDAO.OPERACOES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cod", op[0]);
            m.put("descricao", op[1]);
            l.add(m);
        }
        return l;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Integer externo = idExterno(req);
        // Lançar boletim é ato da empresa contratada. Gente de dentro consulta
        // e confere; se um dia precisar lançar, será por outro caminho, com o
        // registro de que foi a usina quem lançou.
        if (externo == null) { erro(resp, 403, "Somente a empresa contratada lança boletim"); return; }
        if (!"boletins".equals(rota(req))) { erro(resp, 404, "Rota desconhecida"); return; }

        try {
            JsonObject b = corpo(req);

            String data = texto(b, "data");
            if (data == null || !data.matches("\\d{4}-\\d{2}-\\d{2}")) {
                erro(resp, 400, "Data inválida"); return;
            }
            if (LocalDate.parse(data).isAfter(LocalDate.now())) {
                erro(resp, 400, "Não é possível lançar boletim de uma data futura"); return;
            }

            if (!b.has("trechos") || !b.get("trechos").isJsonArray()
                    || b.getAsJsonArray("trechos").isEmpty()) {
                erro(resp, 400, "Informe ao menos um trecho"); return;
            }
            JsonArray arr = b.getAsJsonArray("trechos");
            if (arr.size() > ManobraDAO.MAX_TRECHOS) {
                erro(resp, 400, "Boletim com trechos demais (" + arr.size() + ")"); return;
            }

            Integer kmIni = inteiro(b, "kmInicial"), kmFim = inteiro(b, "kmFinal");
            if (kmIni != null && kmFim != null && kmFim < kmIni) {
                // O papel deixava passar; aqui não. Foi assim que o boletim do
                // exemplo saiu com KM final menor que o inicial.
                erro(resp, 400, "KM final (" + kmFim + ") é menor que o KM inicial (" + kmIni + ")");
                return;
            }

            // Um boletim por dia. O segundo não é impedido — é marcado como
            // extra e exige que a pessoa conte o que houve.
            int seq = dao.proximaSeq(externo, data);
            String justificativa = texto(b, "justificativa");
            if (seq > 1 && (justificativa == null || justificativa.trim().length() < 10)) {
                erro(resp, 400, "Já existe boletim para " + data
                        + ". Para lançar outro, explique o motivo (ao menos 10 caracteres).");
                return;
            }

            List<Map<String, Object>> trechos = new ArrayList<>();
            int i = 0;
            for (JsonElement el : arr) {
                i++;
                if (!el.isJsonObject()) continue;
                JsonObject t = el.getAsJsonObject();
                String origem = texto(t, "codOrigem"), destino = texto(t, "codDestino");
                if (origem == null || origem.isBlank() || destino == null || destino.isBlank()) {
                    erro(resp, 400, "Trecho " + i + ": informe a fazenda de origem e a de destino");
                    return;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("codOperacao",  texto(t, "codOperacao"));
                m.put("descOperacao", texto(t, "descOperacao"));
                m.put("codOrigem",    origem);
                m.put("descOrigem",   texto(t, "descOrigem"));
                m.put("codDestino",   destino);
                m.put("descDestino",  texto(t, "descDestino"));
                m.put("quantidade",   inteiro(t, "quantidade"));
                trechos.add(m);
            }

            Map<String, Object> cab = new LinkedHashMap<>();
            cab.put("idUsuarioExterno", externo);
            cab.put("idEmpresa",        ((Number) atr(req, "idEmpresa")).intValue());
            cab.put("data",             data);
            cab.put("seq",              seq);
            cab.put("extra",            seq > 1 ? "S" : "N");
            cab.put("justificativa",    seq > 1 ? justificativa.trim() : null);
            cab.put("codEquipamento",   texto(b, "codEquipamento"));
            cab.put("descEquipamento",  texto(b, "descEquipamento"));
            cab.put("codContrato",      texto(b, "codContrato"));
            // Motorista e matrícula vêm de quem entrou, não do formulário:
            // é o que impede assinar o boletim em nome de outra pessoa.
            cab.put("nomeMotorista",    String.valueOf(atr(req, "nome")));
            cab.put("matricula",        atr(req, "matricula") == null ? null : String.valueOf(atr(req, "matricula")));
            cab.put("horaInicial",      texto(b, "horaInicial"));
            cab.put("horaFinal",        texto(b, "horaFinal"));
            cab.put("kmInicial",        kmIni);
            cab.put("kmFinal",          kmFim);
            cab.put("observacao",       texto(b, "observacao"));

            int id = dao.salvar(cab, trechos);
            json(resp, "{\"ok\":true,\"id\":" + id + ",\"seq\":" + seq
                     + ",\"extra\":" + (seq > 1) + "}");

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao gravar boletim de manobra", e);
            erro(resp, 500, raiz(e));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Corpo inválido no boletim de manobra", e);
            erro(resp, 400, "Requisição inválida: " + e.getMessage());
        }
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

    private static Integer inteiro(JsonObject o, String c) {
        try {
            if (!o.has(c) || o.get(c).isJsonNull()) return null;
            String v = o.get(c).getAsString();
            return v.isBlank() ? null : Integer.valueOf(v.trim());
        } catch (Exception e) { return null; }
    }

    /** A mensagem do Oracle é a única que diz o que de fato quebrou. */
    private static String raiz(Throwable e) {
        Throwable r = e;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        return r.getMessage() == null ? r.getClass().getSimpleName() : r.getMessage();
    }
}
