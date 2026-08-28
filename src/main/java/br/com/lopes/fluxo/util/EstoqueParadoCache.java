package br.com.lopes.fluxo.util;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Ponte em memória entre {@link br.com.lopes.fluxo.agendamento.EstoqueParadoHandler}
 * e {@link br.com.lopes.fluxo.servlet.EstoqueParadoServlet}: a extração do
 * Oracle é pesada (roda MATERIAL.PR_POPULAR_MOVIMENTOMATERIAL numa janela de
 * 2 anos, para todos os materiais), e o handler precisa dela tanto para o
 * Excel quanto para o PDF — mas o PDF é gerado abrindo a página do relatório
 * no Chromium, que por sua vez busca os dados chamando o próprio servlet.
 *
 * Sem isso, cada execução do alerta consultaria o Oracle duas vezes (uma no
 * handler, outra pelo Chromium) — o dobro do tempo e da carga no ERP de
 * produção pra nada. O handler calcula uma vez, guarda aqui, e o servlet usa
 * o que estiver fresco (mesmo dia, poucos minutos) em vez de recalcular.
 *
 * Deliberadamente simples (uma variável estática, sem TTL sofisticado): só
 * existe uma execução deste alerta por vez (fila única de agendamentos
 * manuais, e o automático roda uma vez por semana), então não há disputa
 * real por esse cache.
 */
public final class EstoqueParadoCache {

    private EstoqueParadoCache() {}

    public record Entrada(long timestampMs, LocalDate dataExecucao,
                           List<Map<String, Object>> itens, Map<String, Object> comparacao) {}

    private static volatile Entrada atual;

    public static void preencher(LocalDate dataExecucao, List<Map<String, Object>> itens, Map<String, Object> comparacao) {
        atual = new Entrada(System.currentTimeMillis(), dataExecucao, itens, comparacao);
    }

    /** @return o cache, se for de hoje e mais novo que {@code validadeMs}; senão null. */
    public static Entrada valida(LocalDate hoje, long validadeMs) {
        Entrada e = atual;
        if (e == null) return null;
        if (!e.dataExecucao().equals(hoje)) return null;
        if (System.currentTimeMillis() - e.timestampMs() > validadeMs) return null;
        return e;
    }
}
