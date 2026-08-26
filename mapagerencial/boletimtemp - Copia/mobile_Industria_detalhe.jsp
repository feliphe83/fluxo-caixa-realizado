<%@include file="validausuario.jsp"%>

<%@page import="java.text.DecimalFormat"%>
<%--
    Document   : mobile_tela
    Created on : 17/02/2011, 16:00:01
    Author     : Nichael
--%>

<%@page contentType="text/html" pageEncoding="windows-1252"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=windows-1252">

    </head>
    <body>

    <%@ page language="java" import="java.sql.*, java.io.*"%>

    <%

    Connection con = null;
    try{

        Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
        con = DriverManager.getConnection("jdbc:oracle:thin:@123.0.0.200:1521:o9i","consulta","consulta");
        Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
        ResultSet rset;
        
        DecimalFormat nf2 = new DecimalFormat("#,##0.00"); 
        DecimalFormat nf3 = new DecimalFormat("#,##0.000"); 
        DecimalFormat nf4 = new DecimalFormat("#,##0.0000"); 
        
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        
        String sSql = "";
        
        sSql = "SELECT NVL(B.STATUS,'RODANDO') STATUS_A, B.MOTIVO_A, B.DATAHORA_A, B.HORAINI_A, B.HORAFIM_A, B.TEMPO_A, \n" +
               "       NVL(C.STATUS,'RODANDO') STATUS_B, C.MOTIVO_B, C.DATAHORA_B, C.HORAINI_B, C.HORAFIM_B, C.TEMPO_B \n" +
               "FROM (SELECT 1 CODIGO FROM DUAL) A, \n" +
               "     (SELECT 1 CODIGO, \n" +
               "             'PARADA' STATUS, \n" +
               "             TRIM(A.DESCRICAO) MOTIVO_A, \n" +
               "             TO_CHAR(A.DATAHORA,'DD/MM/RRRR') DATAHORA_A, \n" +
               "             SUBSTR(A.HORAINI,1,5) HORAINI_A, \n" +
               "             SUBSTR(A.HORAFIM,1,5) HORAFIM_A, \n" +
               "             CSCLIENTE.GOT_CALCULO_HORA(HORAINI, HORAFIM) TEMPO_A \n" +
               "      FROM LABORATORIO.OCORRENCIA A, LABORATORIO.CAUSAS B, LABORATORIO.OBJETO C \n" +
               "      WHERE A.COD_GRUPOEMPRESA = 1 \n" +
               "      AND   A.COD_EMPRESA      = 1 \n" +
               "      AND   A.COD_FILIAL       = 1 \n" +
               "      AND   A.CODIGO_OBJETO    = 3 \n" +
               "      AND   B.COD_GRUPOEMPRESA = A.COD_GRUPOEMPRESA \n" +
               "      AND   B.COD_EMPRESA      = A.COD_EMPRESA \n" +
               "      AND   B.COD_FILIAL       = A.COD_FILIAL \n" +
               "      AND   B.CODIGO           = A.COD_CAUSA \n" +
               "      AND   A.COD_GRUPOEMPRESA = C.COD_GRUPOEMPRESA \n" +
               "      AND   A.COD_EMPRESA      = C.COD_EMPRESA \n" +
               "      AND   A.COD_FILIAL       = C.COD_FILIAL \n" +
               "      AND   A.CODIGO_OBJETO    = C.CODIGO_OBJETO \n" +
               "      AND   TRUNC(A.DATAHORA) >= '01/10/2018' \n" +
               "      AND  (A.HORAINI = A.HORAFIM OR (HORAINI IS NOT NULL AND HORAFIM IS NULL)) \n" +
               "      AND   COD_CAUSA != 69) B, \n" +
               "     (SELECT 1 CODIGO,  \n" +
               "             'PARADA' STATUS, \n" +
               "             TRIM(A.DESCRICAO) MOTIVO_B, \n" +
               "             TO_CHAR(A.DATAHORA,'DD/MM/RRRR') DATAHORA_B, \n" +
               "             SUBSTR(A.HORAINI,1,5) HORAINI_B, \n" +
               "             SUBSTR(A.HORAFIM,1,5) HORAFIM_B, \n" +
               "             CSCLIENTE.GOT_CALCULO_HORA(HORAINI, HORAFIM) TEMPO_B \n" +
               "      FROM LABORATORIO.OCORRENCIA A, LABORATORIO.CAUSAS B, LABORATORIO.OBJETO C \n" +
               "      WHERE A.COD_GRUPOEMPRESA = 1 \n" +
               "      AND   A.COD_EMPRESA      = 1 \n" +
               "      AND   A.COD_FILIAL       = 1 \n" +
               "      AND   A.CODIGO_OBJETO    = 355 \n" +
               "      AND   B.COD_GRUPOEMPRESA = A.COD_GRUPOEMPRESA \n" +
               "      AND   B.COD_EMPRESA      = A.COD_EMPRESA \n" +
               "      AND   B.COD_FILIAL       = A.COD_FILIAL \n" +
               "      AND   B.CODIGO           = A.COD_CAUSA \n" +
               "      AND   A.COD_GRUPOEMPRESA = C.COD_GRUPOEMPRESA \n" +
               "      AND   A.COD_EMPRESA      = C.COD_EMPRESA \n" +
               "      AND   A.COD_FILIAL       = C.COD_FILIAL \n" +
               "      AND   A.CODIGO_OBJETO    = C.CODIGO_OBJETO \n" +
               //"      AND   TRUNC(A.DATAHORA) >= '01/10/2014' \n" +
               "      AND   TO_CHAR(A.DATAHORA,'DD/MM/RRRR')||SUBSTR(A.HORAINI,1,5) <> '--23/12/201304:25' \n" +
               "      AND  (A.HORAINI = A.HORAFIM OR (HORAINI IS NOT NULL AND HORAFIM IS NULL)) \n" +
               "      AND   COD_CAUSA != 69 \n" +
               "      AND   ROWNUM <= 1) C \n" +
               "WHERE A.CODIGO = B.CODIGO (+) \n" +
               "AND   A.CODIGO = C.CODIGO (+)";
        
        rset = stmt.executeQuery(sSql);
        rset.next();
        
        out.println("<form>");
        out.println("<table width='100%' bgcolor='#00CCCC' style='font-family: verdana; font-size: 12px' cellpadding='1' cellspacing='1' border='0'>");

        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td colspan=10 align='center' ><b>");
        out.println("Status da fábrica");
        out.println("</b></td></tr>");

        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td rowspan='4' align='center' style='font-size: 30px' bgcolor=" + (rset.getString("status_a").equals("RODANDO")? "'green'" : "'red'") + "><b>");
        out.println("MOENDA A");
        out.println("</b></td>");
        out.println("<td align='center'><b>");
        out.println(rset.getString("status_a"));
        out.println("</b></td>");
        out.println("<tr><td align='center'><b>");
        out.println(rset.getString("datahora_a"));
        out.println("</b></td></tr>");
        out.println("<tr><td align='center'><b>");
        out.println(rset.getString("tempo_a"));
        out.println("</b></td></tr>");
        out.println("<tr><td align='center'><b>");
        out.println(rset.getString("motivo_a"));
        out.println("</b></td></tr>");
        
        //
        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td rowspan='4' align='center' style='font-size: 30px' bgcolor=" + (rset.getString("status_b").equals("RODANDO")? "'green'" : "'red'") + "><b>");
        out.println("MOENDA B");
        out.println("</b></td>");
        out.println("<td align='center'><b>");
        out.println(rset.getString("status_b"));
        out.println("</b></td>");
        out.println("<tr><td align='center'><b>");
        out.println(rset.getString("datahora_b"));
        out.println("</b></td></tr>");
        out.println("<tr><td align='center'><b>");
        out.println(rset.getString("tempo_b"));
        out.println("</b></td></tr>");
        out.println("<tr><td align='center'><b>");
        out.println(rset.getString("motivo_b"));
        out.println("</b></td></tr>");
        
        out.println("</table></form>");

        rset.close();
        stmt.close();
        con.close();
        rset = null;
        stmt = null;
        con  = null;

    }catch (Exception e){
        out.println(e);
        con.close();
        con = null;
    }
    %>

</body>
</html>
