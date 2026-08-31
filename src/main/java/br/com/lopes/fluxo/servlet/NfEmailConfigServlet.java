package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.NfEmailConfigDAO;
import br.com.lopes.fluxo.util.ImapComprasUtil;
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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tela Administração → NF sem Entrada: configura a caixa de e-mail de
 * Compras (IMAP), o prazo em dias e a janela de varredura usados por
 * {@link br.com.lopes.fluxo.agendamento.AlertaNfSemEntradaHandler}.
 *
 * Tanto leitura quanto gravação exigem administrador — diferente de
 * {@code /api/parametros}, que qualquer usuário logado pode ler: aqui
 * entraria a senha do IMAP se a leitura fosse liberada geral, e por isso
 * esta configuração fica numa tabela própria ({@link NfEmailConfigDAO}), não
 * junto dos parâmetros gerais.
 *
 * GET  /api/admin/nf-sem-entrada-config          -> { ok, host, pasta, usuario,
 *        senhaConfigurada, prazoDias, diasVarredura, atualizadoEm, atualizadoPor }
 *        (nunca devolve a senha em texto puro)
 * POST /api/admin/nf-sem-entrada-config          -> grava; { usuario? } senha
 *        vazia/ausente MANTÉM a senha já gravada
 * POST /api/admin/nf-sem-entrada-config/testar   -> conecta de verdade na
 *        caixa (com a config já gravada) e devolve quantos PDFs achou no
 *        último dia, ou o erro de conexão
 */
@WebServlet({"/api/admin/nf-sem-entrada-config", "/api/admin/nf-sem-entrada-config/*"})
public class NfEmailConfigServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(NfEmailConfigServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final NfEmailConfigDAO dao = new NfEmailConfigDAO();

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
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        json(resp, "{\"ok\":false,\"erro\":" + GSON.toJson(msg) + "}");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!admin(req)) { erro(resp, 403, "Apenas administradores"); return; }
        try {
            NfEmailConfigDAO.Config c = dao.obter();
            JsonObject o = new JsonObject();
            o.addProperty("ok", true);
            o.addProperty("host", c.host);
            o.addProperty("pasta", c.pasta);
            o.addProperty("usuario", c.usuario);
            o.addProperty("senhaConfigurada", c.senhaConfigurada);
            o.addProperty("prazoDias", c.prazoDias);
            o.addProperty("diasVarredura", c.diasVarredura);
            o.addProperty("atualizadoEm", c.atualizadoEm);
            o.addProperty("atualizadoPor", c.atualizadoPor);
            json(resp, GSON.toJson(o));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao ler configuração de NF sem entrada", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!admin(req)) { erro(resp, 403, "Apenas administradores"); return; }

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.equals("/testar")) {
            testar(resp);
            return;
        }
        salvar(req, resp);
    }

    private void salvar(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = req.getReader()) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            }
            JsonObject b = JsonParser.parseString(sb.toString()).getAsJsonObject();

            String host = texto(b, "host", "email-ssl.com.br");
            String pasta = texto(b, "pasta", "INBOX");
            String usuario = texto(b, "usuario", "");
            String senha = b.has("senha") && !b.get("senha").isJsonNull() ? b.get("senha").getAsString() : null;
            int prazoDias = inteiro(b, "prazoDias", 5);
            int diasVarredura = inteiro(b, "diasVarredura", 20);

            if (usuario.isBlank()) { erro(resp, 400, "Informe o usuário (caixa de e-mail) de Compras"); return; }
            if (host.isBlank()) { erro(resp, 400, "Informe o host do IMAP"); return; }
            if (pasta.isBlank()) { erro(resp, 400, "Informe a pasta (ex.: INBOX)"); return; }
            if (prazoDias < 1 || prazoDias > 60) { erro(resp, 400, "Prazo em dias deve ficar entre 1 e 60"); return; }
            if (diasVarredura < 1 || diasVarredura > 120) { erro(resp, 400, "Janela de varredura deve ficar entre 1 e 120 dias"); return; }

            dao.salvar(host, pasta, usuario, senha, prazoDias, diasVarredura, quem(req));
            json(resp, "{\"ok\":true}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao gravar configuração de NF sem entrada", e);
            erro(resp, 500, e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Corpo inválido ao gravar configuração de NF sem entrada", e);
            erro(resp, 400, "Requisição inválida: " + e.getMessage());
        }
    }

    /** Conecta de verdade na caixa configurada e conta os PDFs do último dia — não grava nada. */
    private void testar(HttpServletResponse resp) throws IOException {
        try {
            List<ImapComprasUtil.AnexoPdf> anexos = ImapComprasUtil.buscarAnexosPdf(1);
            long comChave = anexos.stream().filter(a -> a.texto != null && !a.texto.isBlank()).count();
            JsonObject o = new JsonObject();
            o.addProperty("ok", true);
            o.addProperty("mensagem", "Conexão OK. " + anexos.size() + " PDF(s) encontrado(s) no último dia"
                    + (anexos.isEmpty() ? "." : " (" + comChave + " com texto legível)."));
            json(resp, GSON.toJson(o));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Falha no teste de conexão IMAP de Compras", e);
            // Status 200: a chamada ao servlet funcionou, o teste de conexão
            // é que deu errado — a tela decide o que mostrar pelo "ok".
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            json(resp, "{\"ok\":false,\"erro\":" + GSON.toJson("Falha na conexão: " + msg) + "}");
        }
    }

    private static String texto(JsonObject b, String chave, String padrao) {
        return b.has(chave) && !b.get(chave).isJsonNull() ? b.get(chave).getAsString().trim() : padrao;
    }

    private static int inteiro(JsonObject b, String chave, int padrao) {
        try {
            return b.has(chave) && !b.get(chave).isJsonNull() ? b.get(chave).getAsInt() : padrao;
        } catch (Exception e) {
            return padrao;
        }
    }
}
