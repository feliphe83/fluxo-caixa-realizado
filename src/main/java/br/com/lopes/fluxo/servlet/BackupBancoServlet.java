package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.BackupBancoDAO;
import br.com.lopes.fluxo.util.ArmazenamentoBackupUtil;
import br.com.lopes.fluxo.util.BackupBancoUtil;
import com.google.gson.Gson;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administração do backup do banco MySQL "intranet" (Administração → Backup
 * do Banco). Só administradores acessam. O agendamento automático é lido e
 * executado por {@link br.com.lopes.fluxo.agendamento.BackupBancoScheduler};
 * este servlet só configura e permite gerar/baixar sob demanda.
 *
 * GET  /api/admin/backup-banco           -> {config, historico}
 * PUT  /api/admin/backup-banco           -> salva a configuração do agendamento
 *        Body: {diasSemana:[1..7], horaExecucao:"HH:mm", manterDias, ativo}
 * POST /api/admin/backup-banco/gerar     -> gera um backup agora (síncrono — o
 *        admin está esperando na tela) e devolve o id pra baixar
 * GET  /api/admin/backup-banco/download?id=N -> baixa o .zip daquele backup
 */
@WebServlet("/api/admin/backup-banco/*")
public class BackupBancoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(BackupBancoServlet.class.getName());
    private static final Gson GSON = new Gson();
    private final BackupBancoDAO dao = new BackupBancoDAO();

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
        json(resp, "{\"ok\":false,\"erro\":" + GSON.toJson(msg) + "}");
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
        String path = req.getPathInfo();
        if ("/download".equals(path)) {
            // Autenticado (AuthFilter já garante sessão) + admin, checado aqui
            // porque o link de download é um GET simples, sem passar pelo
            // isAdmin genérico de baixo.
            if (!isAdmin(req)) { resp.sendError(403, "Acesso negado"); return; }
            baixar(req, resp);
            return;
        }
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        try {
            Map<String, Object> config = dao.buscarConfig();
            List<Map<String, Object>> historico = dao.listarHistorico(30);
            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.add("config", GSON.toJsonTree(config));
            r.add("historico", GSON.toJsonTree(historico));
            json(resp, GSON.toJson(r));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao consultar configuração de backup", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        try {
            JsonObject b = lerBody(req);
            List<Integer> dias = new ArrayList<>();
            if (b.has("diasSemana") && b.get("diasSemana").isJsonArray()) {
                for (JsonElement e : b.get("diasSemana").getAsJsonArray()) dias.add(e.getAsInt());
            }
            boolean ativo = b.has("ativo") && b.get("ativo").getAsBoolean();
            String hora = b.has("horaExecucao") && !b.get("horaExecucao").isJsonNull() ? b.get("horaExecucao").getAsString() : null;
            if (ativo) {
                if (dias.isEmpty()) { erro(resp, 400, "Escolha ao menos um dia da semana"); return; }
                if (hora == null || hora.isBlank()) { erro(resp, 400, "Informe o horário"); return; }
            }
            int manterDias = b.has("manterDias") ? b.get("manterDias").getAsInt() : 30;
            if (manterDias < 1) { erro(resp, 400, "\"Manter últimos\" precisa ser pelo menos 1 dia"); return; }

            dao.salvarConfig(dias, hora, manterDias, ativo);
            json(resp, "{\"ok\":true}");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao salvar configuração de backup", e);
            erro(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Acesso negado"); return; }
        String path = req.getPathInfo();
        if (!"/gerar".equals(path)) { erro(resp, 404, "Rota não encontrada"); return; }

        Integer idUsuario = null;
        Object idAttr = req.getSession(false).getAttribute("idUsuario");
        if (idAttr instanceof Number n) idUsuario = n.intValue();

        try {
            BackupBancoUtil.Resultado resultado = BackupBancoUtil.gerarBackup();
            String caminho = ArmazenamentoBackupUtil.salvar(resultado.conteudoZip());
            int id = dao.registrarExecucao("manual", "sucesso", null, caminho, resultado.nomeArquivo(),
                    (long) resultado.conteudoZip().length, idUsuario);
            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.addProperty("id", id);
            r.addProperty("nomeArquivo", resultado.nomeArquivo());
            r.addProperty("tamanhoBytes", resultado.conteudoZip().length);
            json(resp, GSON.toJson(r));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao gerar backup do banco", e);
            try {
                dao.registrarExecucao("manual", "erro", e.getMessage(), null, null, null, idUsuario);
            } catch (SQLException ignorado) {
                LOG.log(Level.SEVERE, "Não foi possível registrar a falha do backup manual", ignorado);
            }
            erro(resp, 500, "Falha ao gerar o backup: " + e.getMessage());
        }
    }

    private void baixar(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            int id = Integer.parseInt(req.getParameter("id"));
            Map<String, String> arquivo = dao.buscarArquivo(id);
            if (arquivo == null || arquivo.get("caminho") == null) {
                resp.sendError(404, "Backup não encontrado");
                return;
            }
            Path caminho = ArmazenamentoBackupUtil.resolver(arquivo.get("caminho"));
            if (!ArmazenamentoBackupUtil.dentroDaBase(caminho) || !Files.exists(caminho)) {
                resp.sendError(404, "Arquivo não encontrado no disco");
                return;
            }
            String nome = arquivo.get("nome") != null ? arquivo.get("nome") : ("backup-intranet-" + id + ".zip");
            resp.setContentType("application/zip");
            resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                    + URLEncoder.encode(nome, StandardCharsets.UTF_8).replace("+", "%20"));
            resp.setContentLengthLong(Files.size(caminho));
            Files.copy(caminho, resp.getOutputStream());
            resp.getOutputStream().flush();
        } catch (NumberFormatException e) {
            resp.sendError(400, "id inválido");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao baixar backup do banco", e);
            resp.sendError(500, "Erro ao baixar arquivo");
        }
    }
}
