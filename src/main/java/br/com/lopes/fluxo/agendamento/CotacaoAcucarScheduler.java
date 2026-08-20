package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.CotacaoAcucarDAO;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mantém a cotação do açúcar fresca no MySQL sem depender de ninguém abrir a
 * tela.
 *
 * Coleta na subida do sistema e a cada {@link #INTERVALO_MINUTOS} minutos. O
 * intervalo é folgado de propósito: a fonte é atrasada em dez minutos, então
 * bater de dez em dez não traria número mais novo, só mais tráfego. Se a
 * coleta falhar (rede fora, bolsa fechada), o retrato anterior continua no
 * banco — o coletor não apaga o que estava lá quando volta de mãos vazias.
 */
@WebListener
public class CotacaoAcucarScheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(CotacaoAcucarScheduler.class.getName());
    private static final int INTERVALO_MINUTOS = 15;

    private final CotacaoAcucarColetor coletor = new CotacaoAcucarColetor();
    private ScheduledExecutorService executor;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            new CotacaoAcucarDAO().garantirEstrutura();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Não foi possível preparar as tabelas da cotação do açúcar", e);
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cotacao-acucar-scheduler");
            t.setDaemon(true);
            return t;
        });
        // Primeira coleta logo após subir (não em 0 para não brigar com o
        // resto da inicialização), depois de tempo em tempo.
        executor.scheduleAtFixedRate(this::coletar, 1, INTERVALO_MINUTOS, TimeUnit.MINUTES);
        LOG.info("CotacaoAcucarScheduler iniciado — coleta a cada " + INTERVALO_MINUTOS + " minutos.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (executor != null) executor.shutdownNow();
    }

    private void coletar() {
        try {
            coletor.coletar();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Coleta automática da cotação do açúcar falhou", e);
        }
    }
}
