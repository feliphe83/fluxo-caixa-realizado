package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.NfEmailDAO;
import br.com.lopes.fluxo.util.ArmazenamentoNfEmailUtil;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dados da tela "Notas fiscais sem entrada" (painel de acompanhamento do
 * alerta gerado por {@link br.com.lopes.fluxo.agendamento.AlertaNfSemEntradaHandler}).
 *
 * GET /api/nf-sem-entrada          -> { ok, prazoDias, itens:[{id, nrnf, serie,
 *   cnpjEmitente, remetente, assunto, nomeAnexo, dataEmail, status,
 *   diasDesdeEmail, atrasada, temPdf}, ...] }
 * GET /api/nf-sem-entrada/download?id=123 -> baixa o PDF daquele anexo
 *
 * Geração e atualização dos registros é o agendamento (Administração →
 * Relatórios WhatsApp → "NF sem entrada"), inclusive manualmente pelo botão
 * "Executar agora" de lá — esta tela só lê e baixa.
 */
@WebServlet({"/api/nf-sem-entrada", "/api/nf-sem-entrada/*"})
public class NfSemEntradaServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(NfSemEntradaServlet.class.getName());
    private static final Gson GSON = new Gson();
    private static final int PRAZO_DIAS = 5;

    private final NfEmailDAO dao = new NfEmailDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.equals("/download")) {
            baixar(req, resp);
            return;
        }
        listar(resp);
    }

    private void listar(HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try {
            List<Map<String, Object>> linhas = dao.listarTudo();

            JsonObject r = new JsonObject();
            r.addProperty("ok", true);
            r.addProperty("prazoDias", PRAZO_DIAS);

            JsonArray itens = new JsonArray();
            for (Map<String, Object> l : linhas) {
                long dias = diasDesde(l.get("data_email"));
                String status = String.valueOf(l.get("status"));
                String caminhoPdf = texto(l.get("caminho_pdf"));

                JsonObject o = new JsonObject();
                o.addProperty("id", ((Number) l.get("id")).intValue());
                o.addProperty("nrnf", texto(l.get("nrnf")));
                o.addProperty("serie", texto(l.get("serie")));
                o.addProperty("cnpjEmitente", texto(l.get("cnpj_emitente")));
                o.addProperty("remetente", texto(l.get("remetente")));
                o.addProperty("assunto", texto(l.get("assunto")));
                o.addProperty("nomeAnexo", texto(l.get("nome_anexo")));
                o.addProperty("dataEmail", texto(l.get("data_email")));
                o.addProperty("status", status);
                o.addProperty("diasDesdeEmail", dias);
                o.addProperty("atrasada", "PENDENTE".equals(status) && dias >= PRAZO_DIAS);
                o.addProperty("temPdf", !caminhoPdf.isEmpty());
                itens.add(o);
            }
            r.add("itens", itens);

            resp.getWriter().print(GSON.toJson(r));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao consultar notas fiscais sem entrada", e);
            resp.setStatus(500);
            String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            resp.getWriter().print("{\"ok\":false,\"erro\":" + GSON.toJson(msg) + "}");
        }
    }

    private void baixar(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            int id = Integer.parseInt(req.getParameter("id"));
            String caminho = dao.caminhoPdf(id);
            if (caminho == null || caminho.isBlank()) {
                resp.sendError(404, "PDF não disponível para esta nota");
                return;
            }
            Path arquivo = ArmazenamentoNfEmailUtil.resolver(caminho);
            if (!ArmazenamentoNfEmailUtil.dentroDaBase(arquivo) || !Files.exists(arquivo)) {
                resp.sendError(404, "Arquivo não encontrado no disco");
                return;
            }
            // "attachment", não "inline": abre no leitor de PDF padrão do sistema em
            // vez do visualizador embutido do navegador — alguns PDFs de DANFE
            // renderizam em branco no visualizador embutido, mas abrem normal fora dele.
            resp.setContentType("application/pdf");
            resp.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                    + URLEncoder.encode("nota-fiscal-" + id + ".pdf", StandardCharsets.UTF_8).replace("+", "%20"));
            resp.setContentLengthLong(Files.size(arquivo));
            Files.copy(arquivo, resp.getOutputStream());
            resp.getOutputStream().flush();
        } catch (NumberFormatException e) {
            resp.sendError(400, "id inválido");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao baixar PDF de NF sem entrada", e);
            resp.sendError(500, "Erro ao baixar arquivo");
        }
    }

    private static long diasDesde(Object dataEmailIso) {
        try {
            LocalDateTime dt = LocalDateTime.parse(String.valueOf(dataEmailIso));
            return ChronoUnit.DAYS.between(dt.toLocalDate(), LocalDate.now());
        } catch (Exception e) {
            return -1;
        }
    }

    private static String texto(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
