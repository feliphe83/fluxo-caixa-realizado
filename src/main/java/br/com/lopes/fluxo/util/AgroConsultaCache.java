package br.com.lopes.fluxo.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda em memória os resultados brutos das últimas consultas ao banco
 * feitas pelo agente de IA (via n8n), por sessão de chat, para que o
 * front-end do assistente agrícola possa exportar os dados completos em
 * Excel/PDF.
 *
 * O agente só recebe uma amostra truncada (limite de contexto do modelo);
 * aqui fica o resultado inteiro. Guarda as últimas MAX_POR_SESSAO consultas
 * porque uma única pergunta pode gerar várias consultas (ex.: comparativo
 * entre duas safras = duas chamadas da ferramenta), e a exportação precisa
 * de todas.
 *
 * Chave: sessionId enviado pelo n8n na chamada da ferramenta. Se o n8n não
 * enviar sessionId, os resultados ficam apenas na fila global (fallback para
 * instalações com o workflow antigo).
 */
public final class AgroConsultaCache {

    private static final int MAX_POR_SESSAO = 5;
    /** Descarta entradas mais antigas que isto (evita crescer para sempre). */
    private static final long VALIDADE_MS = 2 * 60 * 60 * 1000L; // 2 horas

    public static final class Entrada {
        public final long timestamp;
        public final String titulo;
        public final List<Map<String, Object>> dados;

        Entrada(String titulo, List<Map<String, Object>> dados) {
            this.timestamp = System.currentTimeMillis();
            this.titulo = titulo;
            this.dados = dados;
        }
    }

    private static final ConcurrentHashMap<String, Deque<Entrada>> POR_SESSAO = new ConcurrentHashMap<>();
    private static final Deque<Entrada> GLOBAL = new ArrayDeque<>();

    private AgroConsultaCache() {}

    public static void guardar(String sessionId, String titulo, List<Map<String, Object>> dados) {
        Entrada entrada = new Entrada(titulo, dados);

        synchronized (GLOBAL) {
            GLOBAL.addLast(entrada);
            while (GLOBAL.size() > MAX_POR_SESSAO) GLOBAL.removeFirst();
        }

        if (sessionId != null && !sessionId.isBlank()) {
            Deque<Entrada> fila = POR_SESSAO.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
            synchronized (fila) {
                fila.addLast(entrada);
                while (fila.size() > MAX_POR_SESSAO) fila.removeFirst();
            }
        }

        limparExpiradas();
    }

    /**
     * Últimas consultas da sessão (ou globais como fallback) mais recentes
     * que {@code desde} (0 = todas as válidas), da mais antiga para a mais
     * recente.
     */
    public static List<Entrada> obter(String sessionId, long desde) {
        Deque<Entrada> fila = (sessionId != null && !sessionId.isBlank()) ? POR_SESSAO.get(sessionId) : null;
        if (fila == null || fila.isEmpty()) fila = GLOBAL;

        long agora = System.currentTimeMillis();
        List<Entrada> resultado = new ArrayList<>();
        synchronized (fila) {
            for (Entrada e : fila) {
                if (agora - e.timestamp > VALIDADE_MS) continue;
                if (e.timestamp > desde) resultado.add(e);
            }
        }
        return resultado;
    }

    private static void limparExpiradas() {
        long agora = System.currentTimeMillis();
        POR_SESSAO.entrySet().removeIf(en -> {
            Deque<Entrada> fila = en.getValue();
            synchronized (fila) {
                fila.removeIf(e -> agora - e.timestamp > VALIDADE_MS);
                return fila.isEmpty();
            }
        });
        synchronized (GLOBAL) {
            GLOBAL.removeIf(e -> agora - e.timestamp > VALIDADE_MS);
        }
    }
}
