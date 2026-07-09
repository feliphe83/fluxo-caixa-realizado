package br.com.lopes.fluxo.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Utilitário de conexão com Oracle exclusivo para as consultas do chatbot
 * agrícola (agricola.vw_apontamento, agricola.talhao etc.), usando um usuário
 * de banco próprio, separado do usado pelo fluxo de caixa
 * ({@link OracleConnectionUtil}).
 */
public class AgroOracleConnectionUtil {

    private static final Logger LOG = Logger.getLogger(AgroOracleConnectionUtil.class.getName());

    // ─── CONFIGURAÇÃO ────────────────────────────────────────────────────
    private static final String DB_URL  = "jdbc:oracle:thin:@123.0.0.200:1521:o9i";
    private static final String DB_USER = "consulta";
    private static final String DB_PASS = "consulta";
    // ─────────────────────────────────────────────────────────────────────

    static {
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            LOG.severe("Driver Oracle não encontrado: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }
}
