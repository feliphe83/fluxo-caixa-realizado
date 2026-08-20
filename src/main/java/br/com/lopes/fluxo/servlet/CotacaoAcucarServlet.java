package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.agendamento.CotacaoAcucarColetor;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dispara a coleta da cotação do açúcar sob demanda e grava no MySQL.
 *
 * Fica sob /api/interno/ — liberado sem sessão pelo AuthFilter — para que o
 * agendador do sistema e um eventual cron externo possam chamá-lo. É um GET
 * idempotente que só atualiza cotação pública; o pior que uma chamada extra
 * faz é buscar os mesmos números de novo. A coleta periódica automática é do
 * {@link br.com.lopes.fluxo.agendamento.CotacaoAcucarScheduler}; este
 * endpoint é o gatilho manual.
 */
@WebServlet("/api/interno/coletar-acucar")
public class CotacaoAcucarServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(CotacaoAcucarServlet.class.getName());
    private final CotacaoAcucarColetor coletor = new CotacaoAcucarColetor();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        JsonObject r = new JsonObject();
        try {
            int n = coletor.coletar();
            r.addProperty("ok", true);
            r.addProperty("vencimentos", n);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Falha ao coletar a cotação do açúcar", e);
            r.addProperty("ok", false);
            r.addProperty("erro", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            resp.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
        }
        resp.getWriter().print(r.toString());
        resp.getWriter().flush();
    }
}
