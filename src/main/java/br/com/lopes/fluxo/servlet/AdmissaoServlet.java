package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AdmissaoCandidatoOracleDAO;
import br.com.lopes.fluxo.dao.AdmissaoDocumentoDAO;
import br.com.lopes.fluxo.util.ArmazenamentoAdmissaoUtil;
import br.com.lopes.fluxo.util.CpfUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API pública (sem login) da tela de admissão — admissao.html, o link que vai
 * pro site da usina.
 *
 * GET  /api/publico/admissao/status?cpf=NNNNNNNNNNN
 *   -> valida o CPF, cria/acha o candidato, tenta ligar ao ERP (nome + cargo
 *      pretendido) e devolve os tipos de documento, o que já foi enviado e o
 *      que falta.
 *
 * POST /api/publico/admissao/salvar  (multipart/form-data)
 *   campos: cpf, nome (opcional — só usado se ainda não há nome gravado),
 *           telefone (opcional, sempre editável)
 *   arquivos: um Part por tipo de documento enviado, nomeado "arquivo_{id}"
 *   -> grava os arquivos enviados e devolve o que ainda falta.
 *
 * Sem sessão/login de propósito — o pedido do usuário foi "validar apenas se
 * o CPF é válido". Já cai no prefixo /api/publico/ que o AuthFilter libera.
 */
@WebServlet("/api/publico/admissao/*")
@MultipartConfig(fileSizeThreshold = 1 << 20,
                 maxFileSize = 30L * 1024 * 1024,       // foto de celular em alta resolução passa fácil de 15MB
                 maxRequestSize = 250L * 1024 * 1024)   // vários documentos grandes no mesmo envio
public class AdmissaoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AdmissaoServlet.class.getName());
    private final Gson gson = new Gson();
    private final AdmissaoDocumentoDAO dao = new AdmissaoDocumentoDAO();
    private final AdmissaoCandidatoOracleDAO erpDao = new AdmissaoCandidatoOracleDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        String rota = req.getPathInfo();
        try {
            if (!"/status".equals(rota)) {
                erro(resp, 404, "rota desconhecida");
                return;
            }
            String cpf = CpfUtil.soDigitos(req.getParameter("cpf"));
            if (!CpfUtil.valido(cpf)) { erro(resp, 400, "CPF inválido"); return; }

            dao.garantirEstrutura();
            Map<String, Object> candidato = dao.buscarOuCriarCandidato(cpf);
            int idCandidato = (int) candidato.get("id");

            if (!Boolean.TRUE.equals(candidato.get("ligadoAoErp"))
                    && (candidato.get("nome") == null || String.valueOf(candidato.get("nome")).isBlank())) {
                Map<String, String> achado = erpDao.buscarPorCpf(cpf);
                if (achado != null && achado.get("nome") != null) {
                    dao.ligarAoErpSeVazio(idCandidato, achado.get("nome"), achado.get("cargoDesejado"));
                    candidato = dao.buscarOuCriarCandidato(cpf);
                }
            }

            resp.getWriter().print(gson.toJson(montarStatus(candidato)));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro em admissao/status", e);
            erro(resp, 500, mensagem(e));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try {
            if (!"/salvar".equals(req.getPathInfo())) { erro(resp, 404, "rota desconhecida"); return; }

            String cpf = CpfUtil.soDigitos(campo(req, "cpf"));
            if (!CpfUtil.valido(cpf)) { erro(resp, 400, "CPF inválido"); return; }

            dao.garantirEstrutura();
            Map<String, Object> candidato = dao.buscarOuCriarCandidato(cpf);
            int idCandidato = (int) candidato.get("id");

            String nome = campo(req, "nome");
            if (nome != null && !nome.isBlank()
                    && (candidato.get("nome") == null || String.valueOf(candidato.get("nome")).isBlank())) {
                dao.atualizarNome(idCandidato, nome.trim());
            }

            // Telefone é sempre opcional e sempre editável (não vem do ERP) —
            // atualiza sempre que vier no envio, mesmo que já tivesse um valor.
            String telefone = campo(req, "telefone");
            if (telefone != null) {
                dao.atualizarTelefone(idCandidato, telefone.trim().isBlank() ? null : telefone.trim());
            }

            for (Part parte : req.getParts()) {
                String nomeCampo = parte.getName();
                if (nomeCampo == null || !nomeCampo.startsWith("arquivo_") || parte.getSize() == 0) continue;
                int idTipo;
                try { idTipo = Integer.parseInt(nomeCampo.substring("arquivo_".length())); }
                catch (NumberFormatException e) { continue; }

                String anterior = dao.caminhoDocumento(idCandidato, idTipo);
                String nomeOriginal = nomeArquivo(parte);
                try (InputStream in = parte.getInputStream()) {
                    String caminho = ArmazenamentoAdmissaoUtil.salvar(cpf, idTipo, nomeOriginal, in, anterior);
                    dao.salvarDocumento(idCandidato, idTipo, nomeOriginal, caminho, parte.getSize());
                }
            }

            candidato = dao.buscarOuCriarCandidato(cpf);
            resp.getWriter().print(gson.toJson(montarStatus(candidato)));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro em admissao/salvar", e);
            erro(resp, 500, mensagem(e));
        }
    }

    /** Monta {ok, cpf, nome, cargoPretendido, ligadoAoErp, tipos:[...], faltando:[...]}. */
    private JsonObject montarStatus(Map<String, Object> candidato) throws Exception {
        int idCandidato = (int) candidato.get("id");
        List<Map<String, Object>> tipos = dao.tiposDocumento(true);
        Map<Integer, Map<String, Object>> enviados = dao.documentosDoCandidato(idCandidato);

        JsonArray tiposArr = new JsonArray();
        JsonArray faltandoArr = new JsonArray();
        for (Map<String, Object> t : tipos) {
            int idTipo = (int) t.get("id");
            Map<String, Object> doc = enviados.get(idTipo);

            JsonObject o = new JsonObject();
            o.addProperty("id", idTipo);
            o.addProperty("nome", (String) t.get("nome"));
            o.addProperty("obrigatorio", (Boolean) t.get("obrigatorio"));
            o.addProperty("enviado", doc != null);
            if (doc != null) {
                o.addProperty("nomeArquivoOriginal", (String) doc.get("nomeArquivoOriginal"));
                o.addProperty("enviadoEm", (String) doc.get("enviadoEm"));
            }
            tiposArr.add(o);

            if ((Boolean) t.get("obrigatorio") && doc == null) faltandoArr.add((String) t.get("nome"));
        }

        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("cpf", (String) candidato.get("cpf"));
        r.addProperty("nome", (String) candidato.get("nome"));
        r.addProperty("telefone", (String) candidato.get("telefone"));
        r.addProperty("cargoPretendido", (String) candidato.get("cargoPretendido"));
        r.addProperty("ligadoAoErp", Boolean.TRUE.equals(candidato.get("ligadoAoErp")));
        r.add("tipos", tiposArr);
        r.add("faltando", faltandoArr);
        r.addProperty("completo", faltandoArr.isEmpty());
        return r;
    }

    private static String campo(HttpServletRequest req, String nome) throws Exception {
        Part p = req.getPart(nome);
        if (p == null) return null;
        try (InputStream in = p.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static String nomeArquivo(Part parte) {
        String header = parte.getHeader("content-disposition");
        if (header == null) return "arquivo";
        for (String token : header.split(";")) {
            token = token.trim();
            if (token.startsWith("filename")) {
                String v = token.substring(token.indexOf('=') + 1).trim().replaceAll("^\"|\"$", "");
                return v.isBlank() ? "arquivo" : v;
            }
        }
        return "arquivo";
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        resp.getWriter().print("{\"ok\":false,\"erro\":" + gson.toJson(msg) + "}");
    }

    private static String mensagem(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        int q = m.indexOf('\n');
        return (q > 0 ? m.substring(0, q) : m).replace("\"", "'");
    }
}
