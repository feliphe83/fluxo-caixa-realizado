package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.NfEmailDAO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * GET /api/nf-sem-entrada -> { ok, prazoDias, itens:[{id, nrnf, serie,
 *   cnpjEmitente, remetente, assunto, nomeAnexo, dataEmail, status,
 *   diasDesdeEmail, atrasada}, ...] }
 *
 * Só leitura: quem gera e atualiza os registros é o agendamento (Administração
 * → Relatórios WhatsApp → "NF sem entrada"), inclusive manualmente pelo botão
 * "Executar agora" de lá — esta tela não tem ação própria de disparar a
 * varredura.
 */
@WebServlet({"/api/nf-sem-entrada"})
public class NfSemEntradaServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(NfSemEntradaServlet.class.getName());
    private static final Gson GSON = new Gson();
    private static final int PRAZO_DIAS = 5;

    private final NfEmailDAO dao = new NfEmailDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
