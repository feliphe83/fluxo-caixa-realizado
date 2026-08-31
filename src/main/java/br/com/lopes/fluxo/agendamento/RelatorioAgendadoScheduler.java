package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.RelatorioAgendadoDAO;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dispara os envios agendados por WhatsApp (Administração → Relatórios
 * WhatsApp). Verifica a cada {@link #INTERVALO_MINUTOS} minuto(s) o que
 * está na hora de rodar — ver {@link RelatorioAgendadoDAO#listarPendentes},
 * que cobre os dois tipos de recorrência:
 *
 * - semanal (ex.: relatório de combustível toda segunda às 08:00): dispara
 *   na primeira verificação após a hora marcada;
 * - por intervalo (ex.: alerta de ordem de compra a cada 10 minutos):
 *   dispara quando passou o intervalo desde a última execução.
 *
 * O tique é de 1 minuto justamente por causa do modo por intervalo — assim
 * um alerta configurado para 10 minutos não vira 15.
 *
 * Novos tipos: implemente {@link RelatorioAgendadoHandler} e registre em
 * {@link #HANDLERS}.
 */
@WebListener
public class RelatorioAgendadoScheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(RelatorioAgendadoScheduler.class.getName());
    private static final int INTERVALO_MINUTOS = 1;
    private static final ZoneId FUSO = ZoneId.of("America/Maceio");

    private static final Map<String, RelatorioAgendadoHandler> HANDLERS = Map.ofEntries(
            Map.entry("combustivel", new CombustivelRelatorioAgendadoHandler()),
            Map.entry("oc_pendente", new AlertaOcPendenteHandler()),
            Map.entry("variacao_preco", new AlertaVariacaoPrecoHandler()),
            Map.entry("divergencia_nf", new AlertaDivergenciaNfHandler()),
            Map.entry("contrato_limite", new AlertaContratoLimiteHandler()),
            Map.entry("contrato_vencendo", new AlertaContratoArrendamentoHandler()),
            Map.entry("oc_aprovada_alto_valor", new AlertaOrdemCompraAprovadaHandler()),
            Map.entry("contrato_aprovacao", new AlertaContratoAprovacaoHandler()),
            Map.entry("parcela_contrato_aprovacao", new AlertaParcelaContratoHandler()),
            Map.entry("orcamento_estourado", new AlertaOrcamentoEstouradoHandler()),
            Map.entry("solicitacao_estourada", new AlertaSolicitacaoEstouradaHandler()),
            Map.entry("estoque_parado", new EstoqueParadoHandler()),
            Map.entry("nf_sem_entrada", new AlertaNfSemEntradaHandler())
    );

    /** Execuções mais antigas que isso são descartadas — os recorrentes geram um registro por ciclo. */
    private static final int DIAS_HISTORICO_EXECUCAO = 90;

    private static final RelatorioAgendadoDAO DAO = new RelatorioAgendadoDAO();

    /**
     * Fila das execuções manuais (botão "Executar agora" da administração),
     * separada do tick automático: um envio manual demorado — a geração do
     * PDF leva minutos — não pode atrasar a verificação periódica, e uma fila
     * de tamanho 1 evita que vários cliques gerem envios concorrentes.
     */
    private static final ExecutorService MANUAL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "relatorio-agendado-manual");
        t.setDaemon(true);
        return t;
    });

    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            // Cria as tabelas/colunas usadas pelos agendamentos antes de
            // qualquer tela abrir (a de Usuários já lê fc_usuario.id_logon_erp).
            DAO.garantirEstrutura();
            new AlertaOcPendenteDAO().garantirEstrutura();
            // Parâmetros gerais: a tabela precisa existir e estar semeada
            // antes de qualquer tela perguntar qual é a safra padrão.
            new br.com.lopes.fluxo.dao.ParametroDAO().garantirEstrutura();
            new br.com.lopes.fluxo.dao.AcessoExternoDAO().garantirEstrutura();
            new br.com.lopes.fluxo.dao.ManobraDAO().garantirEstrutura();
            new br.com.lopes.fluxo.dao.ParadaMoagemDAO().garantirEstrutura();
            // Preço da cana: cria a tabela e semeia os meses conhecidos antes
            // de o painel de indicadores pedir a tabela pela primeira vez.
            new br.com.lopes.fluxo.dao.PrecoCanaDAO().garantirEstrutura();
            // Alerta de estoque parado: tabelas do histórico semanal (snapshot/comparação).
            new br.com.lopes.fluxo.dao.EstoqueParadoSnapshotDAO().garantirEstrutura();
            // Admissão de funcionários: tabelas de tipos de documento, candidato e envios.
            new br.com.lopes.fluxo.dao.AdmissaoDocumentoDAO().garantirEstrutura();
            // NF sem entrada: tabela de controle das notas detectadas no e-mail de Compras.
            new br.com.lopes.fluxo.dao.NfEmailDAO().garantirEstrutura();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Não foi possível preparar as tabelas dos agendamentos", e);
        }

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "relatorio-agendado-scheduler");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::verificarEExecutar, 1, INTERVALO_MINUTOS, TimeUnit.MINUTES);
        LOG.info("RelatorioAgendadoScheduler iniciado — verifica a cada " + INTERVALO_MINUTOS + " minutos.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (executor != null) executor.shutdownNow();
    }

    private void verificarEExecutar() {
        try {
            ZonedDateTime agora = ZonedDateTime.now(FUSO);
            List<Map<String, Object>> pendentes = DAO.listarPendentes(agora.getDayOfWeek(), agora.toLocalTime().withNano(0).withSecond(0));
            for (Map<String, Object> agendamento : pendentes) {
                executarUm(agendamento);
            }
            if (agora.getHour() == 3 && agora.getMinute() < INTERVALO_MINUTOS) {
                DAO.limparExecucoesAntigas(DIAS_HISTORICO_EXECUCAO);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao verificar relatórios agendados pendentes", e);
        }
    }

    /**
     * Dispara um agendamento na hora, em background — usado pelo botão
     * "Executar agora". Devolve o controle na mesma hora (a geração leva
     * minutos); o resultado sai em fc_relatorio_agendado_execucao, igual ao
     * disparo automático.
     *
     * @param agendamento no formato de {@link RelatorioAgendadoDAO#buscarPorId}
     */
    public static void dispararAgora(Map<String, Object> agendamento) {
        MANUAL.submit(() -> executarUm(agendamento));
    }

    private static void executarUm(Map<String, Object> agendamento) {
        int id = (int) agendamento.get("id");
        String tipo = String.valueOf(agendamento.get("tipoRelatorio"));
        String nome = String.valueOf(agendamento.get("nome"));

        RelatorioAgendadoHandler handler = HANDLERS.get(tipo);
        if (handler == null) {
            registrarSemLancar(id, "erro", "Tipo de relatório \"" + tipo + "\" sem handler registrado.");
            return;
        }

        try {
            long idUsuarioCriacao = ((Number) agendamento.get("idUsuarioCriacao")).longValue();
            List<Map<String, Object>> destinatarios = DAO.listarDestinatarios(id);
            if (destinatarios.isEmpty()) {
                registrarSemLancar(id, "erro", "Sem destinatários cadastrados.");
                return;
            }
            JsonObject parametros = parseParametros(String.valueOf(agendamento.get("parametros")));

            LOG.info("Executando relatório agendado #" + id + " (" + nome + ", tipo=" + tipo + ") para "
                    + destinatarios.size() + " destinatário(s)");
            String resumo = handler.executar(parametros, destinatarios, idUsuarioCriacao);
            registrarSemLancar(id, "sucesso",
                    resumo != null ? resumo : "Enviado para " + destinatarios.size() + " destinatário(s).");

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Falha ao executar relatório agendado #" + id + " (" + nome + ")", e);
            registrarSemLancar(id, "erro", e.getMessage());
        }
    }

    private static JsonObject parseParametros(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) return new JsonObject();
        try {
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static void registrarSemLancar(int idAgendamento, String status, String detalhe) {
        try {
            DAO.registrarExecucao(idAgendamento, status, detalhe);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Não foi possível registrar a execução do agendamento #" + idAgendamento, e);
        }
    }
}
