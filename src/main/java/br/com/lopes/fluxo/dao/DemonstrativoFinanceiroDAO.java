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
import java.time.LocalDate;
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
 * O MÊS. Vem de geral.filial.inicioperiodocontabil menos um dia — o dia
 * anterior ao início do período aberto é o último dia fechado. É o próprio
 * ERP dizendo até onde fechou, então o painel anda sozinho a cada
 * fechamento e ninguém precisa vir aqui mudar número.
 *
 * A primeira tentativa foi max(anomes) de ctb.saldoconta, e ela deu
 * "fechada até julho de 4201": a tabela de saldos guarda lançamento com
 * competência absurda, e o máximo pega justamente esse. Máximo de uma
 * coluna de dados não é o mesmo que estado do sistema — o estado tem que
 * ser perguntado a quem o guarda.
 */
public class DemonstrativoFinanceiroDAO {

    private static final Logger LOG = Logger.getLogger(DemonstrativoFinanceiroDAO.class.getName());

    private static final String RECURSO_SQL = "/sql/dre-saldos.sql";

    /**
     * O último dia FECHADO na contabilidade: o dia anterior ao início do
     * período contábil aberto. Consulta indicada pela controladoria.
     */
    private static final String SQL_FECHAMENTO = """
        select f.inicioperiodocontabil - 1 fechamento
        from   geral.filial f
        where  f.cod_empresa = 1
        and    f.cod_filial  = 1
        """;

    private static volatile String sqlBase;

    /** @return o último dia fechado, ou null se a filial não responder. */
    public LocalDate fechamentoContabil() {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FECHAMENTO);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            java.sql.Date d = rs.getDate(1);
            LocalDate fechamento = d == null ? null : d.toLocalDate();
            // Mais de uma filial casando com o filtro significaria escolher
            // um fechamento entre vários sem critério. Não é o caso hoje, e
            // se passar a ser é melhor aparecer no log do que ser sorteado.
            if (rs.next()) {
                LOG.warning("geral.filial devolveu mais de uma linha para empresa 1 / filial 1; "
                          + "usando o primeiro fechamento: " + fechamento);
            }
            return fechamento;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao ler o fechamento contábil da filial", e);
            throw new RuntimeException(mensagem(e), e);
        }
    }

    /** AAAAMM do dia fechado — é assim que ctb.saldoconta guarda a competência. */
    public static int anomesDe(LocalDate dia) {
        return dia.getYear() * 100 + dia.getMonthValue();
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
