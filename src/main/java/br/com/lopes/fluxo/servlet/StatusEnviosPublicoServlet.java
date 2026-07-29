package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.RelatorioAgendadoDAO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Status dos envios agendados por WhatsApp — alimenta o painel
 * status-envios.html, que fica aberto num monitor.
 *
 * SEM AUTENTICAÇÃO, de propósito: o painel precisa ficar visível sem
 * ninguém logado. Por isso só devolve o que é preciso para saber se os
 * envios estão funcionando — nome do agendamento, horário e status — e
 * nunca conteúdo de mensagem, destinatários ou dado do ERP.
 *
 * O detalhe da execução é a única fonte de dado pessoal aqui: quando um
 * envio falha, ele traz o nome e o telefone de quem não recebeu. O telefone
 * é mascarado ({@link #mascararTelefones}) antes de sair — o suficiente para
 * identificar o cadastro a corrigir sem publicar o número.
 *
 * GET /api/publico/status-envios
 */
@WebServlet("/api/publico/status-envios")
public class StatusEnviosPublicoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(StatusEnviosPublicoServlet.class.getName());
    private static final Gson GSON = new Gson();

    /** Janela do resumo no topo do painel. */
    private static final int HORAS_RESUMO = 24;
    /** Linhas do histórico — o bastante para ver um padrão sem pesar a tela. */
    private static final int LIMITE_HISTORICO = 60;

    private final RelatorioAgendadoDAO dao = new RelatorioAgendadoDAO();

    /**
     * Telefone vira "(82) *****-6252": mantém DDD e os quatro últimos dígitos,
     * que bastam para achar a pessoa no cadastro, e esconde o resto.
     */
    private static final Pattern TELEFONE = Pattern.compile("(\\(?\\d{2}\\)?[\\s-]?)(\\d{4,5})([\\s-]?)(\\d{4})");

    static String mascararTelefones(String texto) {
        if (texto == null) return null;
        Matcher m = TELEFONE.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    m.group(1) + "*".repeat(m.group(2).length()) + m.group(3) + m.group(4)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        // O painel se atualiza sozinho: nada aqui pode vir de cache.
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        resp.setHeader("Pragma", "no-cache");

        try {
            JsonObject saida = new JsonObject();
            saida.addProperty("ok", true);
            saida.addProperty("geradoEm", java.time.LocalDateTime.now()
                    .withNano(0).toString().replace('T', ' '));

            JsonArray agendamentos = new JsonArray();
            for (Map<String, Object> a : dao.listar()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", (Integer) a.get("id"));
                o.addProperty("nome", str(a.get("nome")));
                o.addProperty("tipoRelatorio", str(a.get("tipoRelatorio")));
                o.addProperty("ativo", Boolean.TRUE.equals(a.get("ativo")));
                o.addProperty("qtdeDestinatarios", (Integer) a.get("qtdeDestinatarios"));
                o.addProperty("diaSemana", (Integer) a.get("diaSemana"));
                o.addProperty("horaEnvio", str(a.get("horaEnvio")));
                o.addProperty("intervaloMinutos", (Integer) a.get("intervaloMinutos"));
                o.addProperty("ultimaExecucao", str(a.get("ultimaExecucao")));
                o.addProperty("ultimoStatus", str(a.get("ultimoStatus")));
                o.addProperty("ultimoDetalhe", mascararTelefones(str(a.get("ultimoDetalhe"))));
                agendamentos.add(o);
            }
            saida.add("agendamentos", agendamentos);

            JsonArray historico = new JsonArray();
            List<Map<String, Object>> execucoes = dao.listarExecucoesRecentes(LIMITE_HISTORICO);
            for (Map<String, Object> e : execucoes) {
                JsonObject o = new JsonObject();
                o.addProperty("nome", str(e.get("nome")));
                o.addProperty("tipoRelatorio", str(e.get("tipoRelatorio")));
                o.addProperty("dataExecucao", str(e.get("dataExecucao")));
                o.addProperty("status", str(e.get("status")));
                o.addProperty("detalhe", mascararTelefones(str(e.get("detalhe"))));
                historico.add(o);
            }
            saida.add("historico", historico);

            Map<String, Integer> resumo = dao.resumoExecucoes(HORAS_RESUMO);
            JsonObject r = new JsonObject();
            r.addProperty("horas", HORAS_RESUMO);
            r.addProperty("sucesso", resumo.getOrDefault("sucesso", 0));
            r.addProperty("erro", resumo.getOrDefault("erro", 0));
            saida.add("resumo", r);

            resp.getWriter().print(GSON.toJson(saida));
            resp.getWriter().flush();

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao montar o painel de status de envios", e);
            resp.setStatus(500);
            // Sem detalhe do banco na resposta: a página é pública.
            resp.getWriter().print("{\"ok\":false,\"erro\":\"Não foi possível consultar o status dos envios\"}");
            resp.getWriter().flush();
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
