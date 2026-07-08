package br.com.lopes.fluxo.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Utilitário de conexão com Oracle.
 * Ajuste as constantes DB_URL, DB_USER e DB_PASSWORD conforme o ambiente.
 *
 * Para Oracle 11g com autenticação antiga, habilite Thick Mode no cliente
 * ou configure oracle.jdbc.thinForceTNSNames e os parâmetros abaixo.
 */
public class OracleConnectionUtil {

    private static final Logger LOG = Logger.getLogger(OracleConnectionUtil.class.getName());

    // ─── CONFIGURAÇÃO ────────────────────────────────────────────────────
    // Formato thin: jdbc:oracle:thin:@HOST:PORT:SID
    //               jdbc:oracle:thin:@HOST:PORT/SERVICE_NAME
    private static final String DB_URL  = "jdbc:oracle:thin:@123.0.0.200:1521:o9i";
    private static final String DB_USER = "cpd";
    private static final String DB_PASS = "softcana";
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
