package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Saldos contábeis do balancete, fonte do Demonstrativo Financeiro.
 *
 * A consulta é a do ERP, entregue pronta, e está em
 * src/main/resources/sql/dre-saldos.sql — 800 linhas de SQL gerado que
 * ninguém deve reescrever. Fora do arquivo ela só ganha três substituições:
 * o anomes e as duas datas do período.
 *
 * O MÊS. "Até onde o sistema está fechado" não é uma data que se escreve no
 * código: é o maior anomes que existe em ctb.saldoconta. Perguntando ao
 * banco, o painel anda sozinho quando a contabilidade fecha mais um mês, e
 * ninguém precisa lembrar de vir aqui mudar um número — que é exatamente o
 * tipo de manutenção que se esquece de fazer e ninguém percebe.
 */
public class DemonstrativoFinanceiroDAO {

    private static final Logger LOG = Logger.getLogger(DemonstrativoFinanceiroDAO.class.getName());

    private static final String RECURSO_SQL = "/sql/dre-saldos.sql";

    /** O último mês FECHADO na contabilidade — o que manda no painel. */
    private static final String SQL_ULTIMO_ANOMES = """
        select max(anomes) anomes
        from   ctb.saldoconta
        where  cod_grupoempresa = 1
        and    cod_empresa      = 1
        and    cod_filial       = 1
        and    cod_planocontas  = 1
        """;

    private static volatile String sqlBase;

    /** @return anomes no formato AAAAMM, ou null se a contabilidade não responder. */
    public Integer ultimoAnomesFechado() {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_ULTIMO_ANOMES);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int v = rs.getInt(1);
                return rs.wasNull() ? null : v;
            }
            return null;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao descobrir o último mês fechado", e);
            throw new RuntimeException(mensagem(e), e);
        }
    }

    /** Uma linha por conta contábil, do grau 1 ao 5, no anomes pedido. */
    public List<Map<String, Object>> saldos(int anomes) {
        String sql = montar(anomes);
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> linhas = RowMapperUtil.toList(rs);
            LOG.info("Balancete " + anomes + ": " + linhas.size() + " contas");
            return linhas;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar o balancete de " + anomes, e);
            throw new RuntimeException(mensagem(e), e);
        }
    }

    /**
     * O SQL com o mês aplicado.
     *
     * As datas saem do próprio anomes — primeiro e último dia do mês. No
     * original vinham escritas à mão ('01/05/2026' e '31/05/2026') junto com
     * o anomes 202605; derivá-las elimina a chance de o mês do anomes e o
     * mês das datas discordarem, que é o tipo de erro que passa despercebido
     * porque o relatório continua saindo.
     */
    String montar(int anomes) {
        YearMonth ym = YearMonth.of(anomes / 100, anomes % 100);
        String ini = String.format("'01/%02d/%d'", ym.getMonthValue(), ym.getYear());
        String fim = String.format("'%02d/%02d/%d'", ym.lengthOfMonth(), ym.getMonthValue(), ym.getYear());
        return base()
                .replace("%ANOMES%",   String.valueOf(anomes))
                .replace("%DATA_INI%", ini)
                .replace("%DATA_FIM%", fim);
    }

    private static String base() {
        String cache = sqlBase;
        if (cache != null) return cache;
        try (InputStream in = DemonstrativoFinanceiroDAO.class.getResourceAsStream(RECURSO_SQL)) {
            if (in == null) throw new IllegalStateException("Recurso não encontrado: " + RECURSO_SQL);
            cache = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            sqlBase = cache;
            return cache;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler " + RECURSO_SQL, e);
        }
    }

    /** O SQL literal já com o mês, para colar no PL/SQL Developer. */
    public String sql(int anomes) { return montar(anomes); }

    private static String mensagem(SQLException e) {
        String m = e.getMessage() == null ? e.getClass().getName() : e.getMessage().trim();
        int quebra = m.indexOf('\n');
        return quebra > 0 ? m.substring(0, quebra) : m;
    }
}
