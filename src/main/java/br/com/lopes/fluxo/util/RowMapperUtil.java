package br.com.lopes.fluxo.util;

import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilitário para mapear um ResultSet genérico (colunas variáveis, não
 * conhecidas em tempo de compilação) para uma lista de mapas nome-da-coluna
 * → valor. Usado pelas consultas agrícolas, que têm dezenas de colunas e não
 * compensam uma classe modelo dedicada por coluna.
 */
public class RowMapperUtil {

    private RowMapperUtil() {}

    public static List<Map<String, Object>> toList(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colunas = meta.getColumnCount();
        String[] nomes = new String[colunas];
        for (int i = 1; i <= colunas; i++) {
            nomes[i - 1] = meta.getColumnLabel(i).toLowerCase();
        }

        List<Map<String, Object>> lista = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> linha = new LinkedHashMap<>();
            for (int i = 1; i <= colunas; i++) {
                linha.put(nomes[i - 1], converter(rs.getObject(i)));
            }
            lista.add(linha);
        }
        return lista;
    }

    private static Object converter(Object valor) throws SQLException {
        if (valor instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (valor instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (valor instanceof Clob clob) {
            return clob.getSubString(1, (int) clob.length());
        }
        return valor;
    }
}
