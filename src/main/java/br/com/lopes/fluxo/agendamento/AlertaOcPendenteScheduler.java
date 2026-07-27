package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.OrdemCompraPendenteDAO;
import br.com.lopes.fluxo.util.EvolutionApiUtil;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Avisa por WhatsApp quem tem ordem de compra esperando aprovação — o que
 * antes era feito por uma aplicação num agendador do Windows.
 *
 * A cada {@link #INTERVALO_MINUTOS} minutos, para cada destinatário
 * (ver {@link AlertaOcPendenteDAO#listarDestinatarios()}), consulta as
 * ordens pendentes daquele aprovador no ERP e manda uma mensagem por ordem
 * nova. "Nova" é o que ainda não foi avisado àquela pessoa: enquanto a
 * ordem seguir pendente ela continua voltando na consulta, mas não é
 * reenviada.
 *
 * É um agendador à parte do {@link RelatorioAgendadoScheduler} porque a
 * natureza é outra: lá é um relatório em dia/hora marcados, aqui é uma
 * varredura contínua.
 */
@WebListener
public class AlertaOcPendenteScheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(AlertaOcPendenteScheduler.class.getName());
    private static final int INTERVALO_MINUTOS = 10;

    /** Teto de mensagens por destinatário em uma passada, pra uma enxurrada de ordens novas não virar spam. */
    private static final int MAX_MENSAGENS_POR_CICLO = 15;

    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
    private static final NumberFormat QTDE = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));

    private final AlertaOcPendenteDAO controle = new AlertaOcPendenteDAO();
    private final OrdemCompraPendenteDAO erp = new OrdemCompraPendenteDAO();
    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            controle.garantirEstrutura();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Não foi possível preparar as tabelas do alerta de ordem de compra", e);
        }

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "alerta-oc-pendente");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::verificar, 2, INTERVALO_MINUTOS, TimeUnit.MINUTES);
        LOG.info("AlertaOcPendenteScheduler iniciado — verifica a cada " + INTERVALO_MINUTOS + " minutos.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (executor != null) executor.shutdownNow();
    }

    private void verificar() {
        List<Map<String, Object>> destinatarios;
        try {
            destinatarios = controle.listarDestinatarios();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao carregar destinatários do alerta de ordem de compra", e);
            return;
        }
        if (destinatarios.isEmpty()) return;

        for (Map<String, Object> destinatario : destinatarios) {
            try {
                avisar(destinatario);
            } catch (Exception e) {
                // Falha de um destinatário não pode travar os demais.
                LOG.log(Level.SEVERE, "Erro no alerta de ordem de compra para " + destinatario.get("nome"), e);
            }
        }

        try {
            controle.limparHistoricoAntigo();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Não foi possível limpar o histórico antigo do alerta de ordem de compra", e);
        }
    }

    private void avisar(Map<String, Object> destinatario) throws Exception {
        int idUsuario = ((Number) destinatario.get("id")).intValue();
        int idLogon = ((Number) destinatario.get("idLogonErp")).intValue();
        String nome = String.valueOf(destinatario.get("nome"));
        String telefone = String.valueOf(destinatario.get("telefone"));

        List<Map<String, Object>> itens = erp.buscarPendentes(idLogon);
        if (itens.isEmpty()) return;

        Set<String> jaEnviados = controle.jaEnviados(idUsuario);

        int enviadas = 0;
        for (Map.Entry<String, List<Map<String, Object>>> ordem : agruparPorOrdem(itens).entrySet()) {
            List<Map<String, Object>> itensDaOrdem = ordem.getValue();
            String tipo = str(itensDaOrdem.get(0).get("tipo"));
            String nrSolicitacao = str(itensDaOrdem.get(0).get("nr_solicitacao"));

            if (jaEnviados.contains(AlertaOcPendenteDAO.chave(tipo, nrSolicitacao))) continue;

            if (enviadas >= MAX_MENSAGENS_POR_CICLO) {
                LOG.info("Alerta de ordem de compra para " + nome + ": limite de "
                        + MAX_MENSAGENS_POR_CICLO + " mensagens por ciclo atingido, o resto vai no próximo.");
                break;
            }

            EvolutionApiUtil.enviarTexto(telefone, montarMensagem(itensDaOrdem));
            // Só marca depois do envio dar certo: se a Evolution API falhar, a
            // ordem continua "não avisada" e entra de novo no próximo ciclo.
            controle.registrarEnviado(idUsuario, tipo, nrSolicitacao);
            enviadas++;
        }

        if (enviadas > 0) {
            LOG.info("Alerta de ordem de compra: " + enviadas + " ordem(ns) avisada(s) para " + nome);
        }
    }

    /** Uma ordem pode ter vários itens (uma linha por material) — vira uma mensagem só. */
    private static Map<String, List<Map<String, Object>>> agruparPorOrdem(List<Map<String, Object>> itens) {
        Map<String, List<Map<String, Object>>> porOrdem = new LinkedHashMap<>();
        for (Map<String, Object> item : itens) {
            String chave = AlertaOcPendenteDAO.chave(str(item.get("tipo")), str(item.get("nr_solicitacao")));
            porOrdem.computeIfAbsent(chave, k -> new ArrayList<>()).add(item);
        }
        return porOrdem;
    }

    private static String montarMensagem(List<Map<String, Object>> itens) {
        Map<String, Object> primeiro = itens.get(0);
        StringBuilder msg = new StringBuilder();

        msg.append("*").append(str(primeiro.get("tipo"))).append(" aguardando sua aprovação*\n\n");
        msg.append("*Solicitação:* ").append(str(primeiro.get("nr_solicitacao"))).append("\n");

        String fornecedor = str(primeiro.get("nome"));
        if (!fornecedor.isBlank()) msg.append("*Fornecedor:* ").append(fornecedor).append("\n");

        String objetoCusto = str(primeiro.get("desc_objetocusto"));
        if (!objetoCusto.isBlank()) msg.append("*Objeto de Custo:* ").append(objetoCusto).append("\n");

        msg.append("\n*").append(itens.size() > 1 ? "Itens:" : "Item:").append("*\n");
        double total = 0;
        for (Map<String, Object> item : itens) {
            double precoTotal = num(item.get("precototal"));
            total += precoTotal;
            msg.append("• ").append(str(item.get("cod_material")))
               .append(" — ").append(QTDE.format(num(item.get("quantidade"))))
               .append(" ").append(str(item.get("cod_unidade")))
               .append(" × ").append(MOEDA.format(num(item.get("preco_unitario"))))
               .append(" = ").append(MOEDA.format(precoTotal))
               .append("\n");
        }

        msg.append("\n*Total:* ").append(MOEDA.format(total));

        String observacao = str(primeiro.get("observacao"));
        if (!observacao.isBlank()) msg.append("\n\n_").append(observacao).append("_");

        return msg.toString();
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}
