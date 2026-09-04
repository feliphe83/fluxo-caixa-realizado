package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.BackupBancoDAO;
import br.com.lopes.fluxo.util.ArmazenamentoBackupUtil;
import br.com.lopes.fluxo.util.BackupBancoUtil;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dispara o backup automático do banco MySQL "intranet" (Administração →
 * Backup do Banco). Verifica a cada minuto se algum dia marcado é hoje e já
 * passou do horário configurado — mesma ideia de tique de
 * {@link RelatorioAgendadoScheduler}, só que independente dele: backup não
 * tem "tipo" nem "destinatário", é uma configuração única
 * (ver {@link BackupBancoDAO}), então não faz sentido forçar esse encaixe no
 * agendador de relatórios (que exige ao menos um destinatário pra disparar).
 *
 * Roda numa thread própria, separada do tique — o mysqldump de um banco maior
 * pode levar minutos, e isso não pode atrasar a verificação do minuto
 * seguinte (mesmo raciocínio da fila MANUAL de RelatorioAgendadoScheduler).
 */
@WebListener
public class BackupBancoScheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(BackupBancoScheduler.class.getName());
    private static final int INTERVALO_MINUTOS = 1;
    private static final ZoneId FUSO = ZoneId.of("America/Maceio");

    private static final BackupBancoDAO DAO = new BackupBancoDAO();

    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            DAO.garantirEstrutura();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Não foi possível preparar as tabelas de backup do banco", e);
        }

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backup-banco-scheduler");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::verificarEExecutar, 1, INTERVALO_MINUTOS, TimeUnit.MINUTES);
        LOG.info("BackupBancoScheduler iniciado — verifica a cada " + INTERVALO_MINUTOS + " minuto(s).");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (executor != null) executor.shutdownNow();
    }

    private void verificarEExecutar() {
        try {
            ZonedDateTime agora = ZonedDateTime.now(FUSO);
            boolean naHora = DAO.estaNaHoraDeRodarAutomatico(agora.getDayOfWeek(), agora.toLocalTime());
            if (naHora) executarBackupAutomatico();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao verificar se é hora do backup automático", e);
        }
    }

    private void executarBackupAutomatico() {
        LOG.info("Iniciando backup automático do banco intranet…");
        try {
            BackupBancoUtil.Resultado resultado = BackupBancoUtil.gerarBackup();
            String caminho = ArmazenamentoBackupUtil.salvar(resultado.conteudoZip());
            DAO.registrarExecucao("automatico", "sucesso", null, caminho, resultado.nomeArquivo(),
                    (long) resultado.conteudoZip().length, null);
            LOG.info("Backup automático concluído: " + resultado.nomeArquivo());

            Map<String, Object> config = DAO.buscarConfig();
            int manterDias = config.get("manterDias") instanceof Number n ? n.intValue() : 30;
            DAO.limparAntigos(manterDias);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Falha ao executar o backup automático do banco", e);
            try {
                DAO.registrarExecucao("automatico", "erro", e.getMessage(), null, null, null, null);
            } catch (Exception ignorado) {
                LOG.log(Level.SEVERE, "Não foi possível registrar a falha do backup automático", ignorado);
            }
        }
    }
}
