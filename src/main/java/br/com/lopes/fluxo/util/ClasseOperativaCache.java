package br.com.lopes.fluxo.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cache em memória do de-para de Classe Operativa (fc_depara_classeoperativa
 * no MySQL): cod_equipamento (automotivo.equipamento no Oracle — a unidade
 * física, não o modelo abstrato) → classificação de Classe Operativa (ex.:
 * "Trator", "Caminhão Apoio", "Ônibus"). Usado pelo Dashboard de Combustível
 * na seção "Consumo Semanal por Classe Operativa". A coluna no MySQL ainda
 * se chama cod_modelo por compatibilidade com o de-para já importado.
 *
 * A tabela é gerenciada na tela de administração (aba "De-Para Classe
 * Operativa"), por importação em lote (colar do Excel). Recarrega sozinho a
 * cada TTL_MS ou quando {@link #invalidar} é chamado após uma
 * importação/exclusão.
 */
public final class ClasseOperativaCache {

    private static final Logger LOG = Logger.getLogger(ClasseOperativaCache.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private static final long TTL_MS = 10 * 60 * 1000L;

    private static volatile Map<String, String> mapa = Map.of();
    private static volatile long carregadoEm = 0;

    private ClasseOperativaCache() {}

    /** Classe Operativa para o cod_modelo, ou null se não mapeado. */
    public static String buscar(String codModelo) {
        if (codModelo == null || codModelo.isBlank()) return null;
        carregarSeNecessario();
        return mapa.get(codModelo.trim());
    }

    public static void invalidar() {
        carregadoEm = 0;
    }

    private static void carregarSeNecessario() {
        if (System.currentTimeMillis() - carregadoEm < TTL_MS) return;
        synchronized (ClasseOperativaCache.class) {
            if (System.currentTimeMillis() - carregadoEm < TTL_MS) return;
            try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT cod_modelo, classe_operativa FROM fc_depara_classeoperativa");
                 ResultSet rs = ps.executeQuery()) {

                Map<String, String> novo = new HashMap<>();
                while (rs.next()) {
                    novo.put(String.valueOf(rs.getInt(1)), rs.getString(2));
                }
                mapa = novo;
            } catch (Exception e) {
                // Tabela ainda não criada ou MySQL fora: segue sem de-para
                // (dashboard cai no comportamento sem classe operativa).
                LOG.log(Level.WARNING, "De-para de classe operativa indisponível: " + e.getMessage());
            }
            carregadoEm = System.currentTimeMillis();
        }
    }
}
