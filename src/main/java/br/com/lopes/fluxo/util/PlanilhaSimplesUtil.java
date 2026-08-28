package br.com.lopes.fluxo.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Gera uma planilha Excel bem simples, sem depender de nenhuma biblioteca
 * nova no projeto (o pom.xml não tem Apache POI, e evitar mexer nele é
 * proposital — ver notas do projeto).
 *
 * Usa o formato "XML Spreadsheet 2003" (SpreadsheetML): é puro texto, o
 * Excel abre nativamente com extensão .xls sem aviso de formato, e suporta
 * várias abas — suficiente pro anexo de relatório por e-mail/WhatsApp. Não é
 * o formato usado pelas telas (que exportam .xlsx no navegador via
 * js/xlsx.full.min.js); esta classe é só para geração no servidor, onde não
 * há navegador para gerar o .xlsx de verdade.
 */
public final class PlanilhaSimplesUtil {

    private PlanilhaSimplesUtil() {}

    public record Aba(String nome, List<String> cabecalho, List<List<Object>> linhas) {}

    public static byte[] gerar(List<Aba> abas) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<?mso-application progid=\"Excel.Sheet\"?>\n");
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" ")
          .append("xmlns:o=\"urn:schemas-microsoft-com:office:office\" ")
          .append("xmlns:x=\"urn:schemas-microsoft-com:office:excel\" ")
          .append("xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n");
        sb.append("<Styles>")
          .append("<Style ss:ID=\"cab\"><Font ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/><Interior ss:Color=\"#0F2460\" ss:Pattern=\"Solid\"/></Style>")
          .append("<Style ss:ID=\"tit\"><Font ss:Bold=\"1\" ss:Size=\"13\" ss:Color=\"#0F2460\"/></Style>")
          .append("</Styles>\n");

        for (Aba aba : abas) {
            sb.append("<Worksheet ss:Name=\"").append(atributo(aba.nome())).append("\">\n<Table>\n");
            sb.append("<Row>");
            for (String h : aba.cabecalho()) {
                sb.append("<Cell ss:StyleID=\"cab\"><Data ss:Type=\"String\">").append(texto(h)).append("</Data></Cell>");
            }
            sb.append("</Row>\n");
            for (List<Object> linha : aba.linhas()) {
                sb.append("<Row>");
                for (Object v : linha) sb.append(celula(v));
                sb.append("</Row>\n");
            }
            sb.append("</Table>\n</Worksheet>\n");
        }
        sb.append("</Workbook>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String celula(Object v) {
        if (v == null) return "<Cell><Data ss:Type=\"String\"></Data></Cell>";
        if (v instanceof Number n) return "<Cell><Data ss:Type=\"Number\">" + n + "</Data></Cell>";
        return "<Cell><Data ss:Type=\"String\">" + texto(v.toString()) + "</Data></Cell>";
    }

    private static String texto(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String atributo(String s) {
        // Nome de aba do Excel: no máximo 31 caracteres, sem alguns símbolos.
        String limpo = texto(s).replaceAll("[\\[\\]:\\\\/?*]", " ");
        return limpo.length() > 31 ? limpo.substring(0, 31) : limpo;
    }
}
