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
 * Cache em memória do de-para de tipo de serviço (fc_depara_tiposervico no
 * MySQL): cod_tiposervico (rh.tiposervico no Oracle) → classificação de
 * Processo/Subprocesso/Objeto de Custo. Usado pelo Controle de Serviços
 * Agrícola para classificar os lançamentos de mão de obra na seção
 * "Objeto/Serviço" do resumo, quando o Oracle não expõe essa classificação
 * diretamente (só existe hoje para o bloco AUTOMOTIVO).
 *
 * A tabela é gerenciada na tela de administração (aba "De-Para Serviços"),
 * por importação em lote (colar do Excel). Recarrega sozinho a cada TTL_MS
 * ou quando {@link #invalidar} é chamado após uma importação/exclusão.
 */
public final class DeParaTipoServicoCache {

    private static final Logger LOG = Logger.getLogger(DeParaTipoServicoCache.class.getName());

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    private static final long TTL_MS = 10 * 60 * 1000L;

    public static final class Registro {
        public final String processo, subprocesso, objetoCusto, descricao;

        public Registro(String processo, String subprocesso, String objetoCusto, String descricao) {
            this.processo = processo;
            this.subprocesso = subprocesso;
            this.objetoCusto = objetoCusto;
            this.descricao = descricao;
        }
    }

    private static volatile Map<String, Registro> mapa = Map.of();
    private static volatile long carregadoEm = 0;

    private DeParaTipoServicoCache() {}

    /** Registro do de-para para o cod_tiposervico, ou null se não mapeado. */
    public static Registro buscar(String codTipoServico) {
        if (codTipoServico == null || codTipoServico.isBlank()) return null;
        carregarSeNecessario();
        return mapa.get(codTipoServico.trim());
    }

    public static void invalidar() {
        carregadoEm = 0;
    }

    private static void carregarSeNecessario() {
        if (System.currentTimeMillis() - carregadoEm < TTL_MS) return;
        synchronized (DeParaTipoServicoCache.class) {
            if (System.currentTimeMillis() - carregadoEm < TTL_MS) return;
            try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT cod_tiposervico, processo, subprocesso, objetocusto, descricao FROM fc_depara_tiposervico");
                 ResultSet rs = ps.executeQuery()) {

                Map<String, Registro> novo = new HashMap<>();
                while (rs.next()) {
                    novo.put(String.valueOf(rs.getInt(1)),
                            new Registro(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
                }
                mapa = novo;
            } catch (Exception e) {
                // Tabela ainda não criada ou MySQL fora: segue sem de-para
                // (resumo cai no comportamento antigo, sem classificação).
                LOG.log(Level.WARNING, "De-para de tipo de serviço indisponível: " + e.getMessage());
            }
            carregadoEm = System.currentTimeMillis();
        }
    }
}
