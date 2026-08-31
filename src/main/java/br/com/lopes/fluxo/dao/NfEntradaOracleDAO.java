package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Confere no ERP se uma nota fiscal (identificada por número + série, que é
 * o que dá pra extrair com confiança da chave de acesso decodificada do
 * DANFE — ver {@link br.com.lopes.fluxo.util.NfeChaveUtil}) já teve algum
 * item dando entrada no almoxarifado.
 *
 * O casamento é o mesmo já usado noutros pontos do projeto (ver
 * {@code OrdemCompraDAO.VW_PARCELAS_NOTAFISCAL} e
 * {@code DieselRecebimentoDAO}): a presença de uma linha em
 * material.itensentrada com o mesmo nrnf/serie é o próprio sinal de entrada
 * — não existe uma tabela separada de "recebimento".
 *
 * Número e série podem estar gravados como texto com zeros à esquerda
 * ("0001234") ou como número puro (1234), conforme o cadastro de cada
 * negócio no ERP — sem poder testar contra o Oracle a partir daqui, o filtro
 * aceita as duas formas: compara como texto exato E, quando a coluna for
 * numérica, como número (que já ignora zero à esquerda sozinho). Um dos dois
 * caminhos tende a bater; nenhum dos dois derruba a consulta se a coluna não
 * for numérica (o regexp_like barra o to_number antes de rodar).
 */
public class NfEntradaOracleDAO {

    private static final Logger LOG = Logger.getLogger(NfEntradaOracleDAO.class.getName());

    private static final String SQL = """
        select count(*) qt
        from   material.itensentrada it
        where (it.nrnf = ? or (regexp_like(it.nrnf, '^[0-9]+$') and to_number(it.nrnf) = ?))
        and   (it.serie = ? or (regexp_like(it.serie, '^[0-9]+$') and to_number(it.serie) = ?))
        """;

    /** True se existir pelo menos um item de entrada para esse número+série de nota. */
    public boolean existeEntrada(String nrnf, String serie) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {
            ps.setString(1, nrnf);
            ps.setLong(2, Long.parseLong(nrnf));
            ps.setString(3, serie);
            ps.setLong(4, Long.parseLong(serie));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("qt") > 0;
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao checar entrada da NF " + nrnf + "/" + serie, e);
            throw new RuntimeException(mensagem(e), e);
        }
    }

    private static String mensagem(SQLException e) {
        return e.getMessage() == null ? e.getClass().getName() : e.getMessage();
    }
}
