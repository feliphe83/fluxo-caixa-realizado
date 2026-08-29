package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AdmissaoDocumentoDAO;
import br.com.lopes.fluxo.util.ArmazenamentoAdmissaoUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tela de controle (RH) dos documentos de admissão — quem já mandou o quê, e
 * o download de cada arquivo. Não é admin-only: qualquer usuário logado com
 * o módulo liberado no Hub acessa, como as demais telas de trabalho
 * (permissão decidida em Administração → Módulos, não aqui).
 *
 * GET /api/admissao-controle/candidatos          -> lista (cpf, nome, cargo, quantos já mandou)
 * GET /api/admissao-controle/candidatos/{id}      -> detalhe (todos os tipos + o que foi enviado)
 * GET /api/admissao-controle/download?idCandidato=&idTipo=  -> baixa o arquivo
 */
@WebServlet("/api/admissao-controle/*")
public class AdmissaoControleServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AdmissaoControleServlet.class.getName());
    private final Gson gson = new Gson();
    private final AdmissaoDocumentoDAO dao = new AdmissaoDocumentoDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        try {
            dao.garantirEstrutura();
            if ("/candidatos".equals(path)) {
                listar(resp);
            } else if (path != null && path.startsWith("/candidatos/")) {
                detalhe(resp, Integer.parseInt(path.substring("/candidatos/".length())));
            } else if ("/download".equals(path)) {
                baixar(req, resp);
            } else {
                erro(resp, 404, "rota desconhecida");
            }
        } catch (NumberFormatException e) {
            erro(resp, 400, "id inválido");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro na tela de controle de admissão", e);
            erro(resp, 500, e.getMessage());
        }
    }

    private void listar(HttpServletResponse resp) throws Exception {
        json(resp);
        List<Map<String, Object>> lista = dao.listarCandidatos();
        resp.getWriter().print("{\"ok\":true,\"data\":" + gson.toJson(lista) + "}");
    }

    private void detalhe(HttpServletResponse resp, int id) throws Exception {
        json(resp);
        Map<String, Object> candidato = dao.buscarCandidatoPorId(id);
        if (candidato == null) { erro(resp, 404, "Candidato não encontrado"); return; }

        List<Map<String, Object>> tipos = dao.tiposDocumento(false);
        Map<Integer, Map<String, Object>> enviados = dao.documentosDoCandidato(id);

        JsonArray arr = new JsonArray();
        for (Map<String, Object> t : tipos) {
            int idTipo = (int) t.get("id");
            Map<String, Object> doc = enviados.get(idTipo);
            JsonObject o = new JsonObject();
            o.addProperty("id", idTipo);
            o.addProperty("nome", (String) t.get("nome"));
            o.addProperty("obrigatorio", (Boolean) t.get("obrigatorio"));
            o.addProperty("ativo", (Boolean) t.get("ativo"));
            o.addProperty("enviado", doc != null);
            if (doc != null) {
                o.addProperty("nomeArquivoOriginal", (String) doc.get("nomeArquivoOriginal"));
                o.addProperty("enviadoEm", (String) doc.get("enviadoEm"));
                o.addProperty("tamanhoBytes", (Long) doc.get("tamanhoBytes"));
            }
            arr.add(o);
        }

        JsonObject r = gson.toJsonTree(candidato).getAsJsonObject();
        r.addProperty("ok", true);
        r.add("documentos", arr);
        resp.getWriter().print(gson.toJson(r));
    }

    private void baixar(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        int idCandidato = Integer.parseInt(req.getParameter("idCandidato"));
        int idTipo = Integer.parseInt(req.getParameter("idTipo"));

        String caminho = dao.caminhoDocumento(idCandidato, idTipo);
        if (caminho == null) { erro(resp, 404, "Documento não encontrado"); return; }

        Path arquivo = ArmazenamentoAdmissaoUtil.resolver(caminho);
        if (!ArmazenamentoAdmissaoUtil.dentroDaBase(arquivo) || !Files.exists(arquivo)) {
            erro(resp, 404, "Arquivo não encontrado no disco");
            return;
        }

        Map<Integer, Map<String, Object>> enviados = dao.documentosDoCandidato(idCandidato);
        Map<String, Object> doc = enviados.get(idTipo);
        String nomeOriginal = doc != null && doc.get("nomeArquivoOriginal") != null
                ? (String) doc.get("nomeArquivoOriginal") : arquivo.getFileName().toString();

        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(nomeOriginal, StandardCharsets.UTF_8).replace("+", "%20"));
        resp.setContentLengthLong(Files.size(arquivo));
        Files.copy(arquivo, resp.getOutputStream());
        resp.getOutputStream().flush();
    }

    private void json(HttpServletResponse resp) {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().print("{\"ok\":false,\"erro\":" + gson.toJson(msg) + "}");
    }
}
