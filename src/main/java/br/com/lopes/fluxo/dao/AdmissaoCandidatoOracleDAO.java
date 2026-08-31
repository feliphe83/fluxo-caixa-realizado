package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Liga o CPF de quem está enviando documentos de admissão ao cadastro que já
 * existe no ERP — quando a pessoa se candidatou pela vaga, isso já criou uma
 * ficha em RH.FICHACANDIDATO. Mesma fonte que {@code FichaCandidatoServlet}
 * usa (RH.FICHACANDIDATO + RH.CANDIDATOCARGO para o cargo desejado nível 1),
 * só que aqui é uma consulta por CPF, bem mais enxuta.
 *
 * É "tente achar, se não achar segue sem": alguém pode chegar no link de
 * admissão sem ter uma ficha de candidato (contratação direta, indicação
 * etc.) — nesse caso a tela pede o nome na mão, e a pessoa da administração
 * completa manualmente o cargo se quiser.
 */
public class AdmissaoCandidatoOracleDAO {

    private static final Logger LOG = Logger.getLogger(AdmissaoCandidatoOracleDAO.class.getName());

    private static final String SQL = """
        SELECT pessoa.nome
             , (SELECT c.descricao
                FROM   RH.CANDIDATOCARGO candidatocargo, RH.CARGO c
                WHERE  candidatocargo.cod_grupoempresa = fichacandidato.cod_grupoempresa
                AND    candidatocargo.cpf              = fichacandidato.cpf
                AND    candidatocargo.cod_cargo         = c.cod_cargo
                AND    candidatocargo.nivel             = c.nivel
                AND    candidatocargo.nivel             = '1'
                AND    rownum = 1) cargo_desejado
        FROM   RH.vw_PESSOA pessoa, RH.vw_FISICA fisica, RH.FICHACANDIDATO fichacandidato
        WHERE  fichacandidato.cpf   = ?
        AND    fichacandidato.cpf   = fisica.cpf
        AND    fisica.cod_pessoa    = pessoa.cod_pessoa
        AND    fichacandidato.cod_grupoempresa = 1
        AND    rownum = 1
        """;

    /** @return {nome, cargoDesejado}, ou null se não achou ficha de candidato para este CPF. */
    public Map<String, String> buscarPorCpf(String cpfSoDigitos) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {
            ps.setString(1, cpfSoDigitos);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, String> m = new LinkedHashMap<>();
                m.put("nome", rs.getString("nome"));
                m.put("cargoDesejado", rs.getString("cargo_desejado"));
                return m;
            }
        } catch (SQLException e) {
            // Não acha o candidato no ERP não é motivo para travar a tela de
            // admissão — a pessoa preenche o nome na mão. Só registra o log.
            LOG.log(Level.WARNING, "Falha ao ligar CPF ao cadastro do ERP (segue sem): " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * A partir de quando o link público de admissão libera o envio de
     * documentos: só quem tem ficha de candidato no ERP preenchida NESTA
     * data ou depois. Quem não tem ficha nenhuma (contratação direta,
     * indicação) também fica de fora — decisão de negócio, não é o "segue
     * sem" de {@link #buscarPorCpf}.
     */
    private static final String DATA_MINIMA_FICHA = "01/07/2026";

    private static final String SQL_FICHA_LIBERADA = """
        SELECT COUNT(*) qt
        FROM   RH.FICHACANDIDATO
        WHERE  CPF              = ?
        AND    COD_GRUPOEMPRESA = 1
        AND    DATA             >= TO_DATE(?, 'DD/MM/YYYY')
        """;

    /**
     * True só se existir ficha de candidato para este CPF preenchida em ou
     * após {@link #DATA_MINIMA_FICHA}. Uma falha na consulta NÃO libera por
     * padrão — diferente de {@link #buscarPorCpf} (que é só enriquecimento de
     * dado), aqui é controle de acesso: sem conseguir confirmar, bloqueia.
     */
    public boolean fichaLiberada(String cpfSoDigitos) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FICHA_LIBERADA)) {
            ps.setString(1, cpfSoDigitos);
            ps.setString(2, DATA_MINIMA_FICHA);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("qt") > 0;
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao checar a data da ficha de candidato (bloqueando por segurança): "
                    + e.getMessage(), e);
            return false;
        }
    }
}
