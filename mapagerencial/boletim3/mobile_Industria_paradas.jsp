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
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        
        DecimalFormat nf2 = new DecimalFormat("#,##0.00"); 
        DecimalFormat nf3 = new DecimalFormat("#,##0.000"); 
        DecimalFormat nf4 = new DecimalFormat("#,##0.0000"); 
        
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        rset = stmt.executeQuery("SELECT C.CODIGO_OBJETO, \n" +
                                 "       TO_CHAR(A.DATAHORA,'DD/MM/RRRR') DATA, \n" +
                                 "       SUBSTR(A.HORAINI,1,5) HORINI, \n" +
                                 "       SUBSTR(A.HORAFIM,1,5) HORFIN, \n" +
                                 "       CSCLIENTE.GOT_CALCULO_HORA(HORAINI, HORAFIM) TEMPO, \n" +
                                 "       TRIM(A.DESCRICAO) MOTIVO \n" +
                                 "FROM LABORATORIO.OCORRENCIA A, LABORATORIO.CAUSAS B, LABORATORIO.OBJETO C \n" +
                                 "WHERE A.COD_GRUPOEMPRESA = 1 \n" +
                                 "AND   A.COD_EMPRESA      = 1 \n" +
                                 "AND   A.COD_FILIAL       = 1 \n" +
                                 "AND   A.CODIGO_OBJETO    = " + (request.getParameter("moenda").equals("A")? " 3 " : " 355 ") + " \n" +
                                 "AND   B.COD_GRUPOEMPRESA = A.COD_GRUPOEMPRESA \n" +
                                 "AND   B.COD_EMPRESA      = A.COD_EMPRESA \n" +
                                 "AND   B.COD_FILIAL       = A.COD_FILIAL \n" +
                                 "AND   B.CODIGO           = A.COD_CAUSA \n" +
                                 "AND   A.COD_GRUPOEMPRESA = C.COD_GRUPOEMPRESA \n" +
                                 "AND   A.COD_EMPRESA      = C.COD_EMPRESA \n" +
                                 "AND   A.COD_FILIAL       = C.COD_FILIAL \n" +
                                 "AND   A.CODIGO_OBJETO    = C.CODIGO_OBJETO \n" +
                                 "AND   TRUNC(A.DATAHORA) >= '13/09/2021' \n" +
                                 "ORDER BY A.DATAHORA DESC, A.HORAINI DESC");

             out.println("<form>");
             out.println("<table width='100%' bgcolor='#00CCCC' style='font-family: verdana; font-size: 12px' cellpadding='1' cellspacing='1' border='0'>");

             out.println("<tr bgcolor='#0099CC' style='font-size:20px; color: white'>");
             out.println("<td colspan=10 align='center' ><b>");
             out.println("Detalhamento de Paradas MOENDA " + request.getParameter("moenda"));
             out.println("</b></td></tr>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td align='center'><b>");
             out.println("Data");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Hora Inicial");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Hora Retorno");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Tempo");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Motivo");
             out.println("</b></td></tr>");

             int i = 0;
             
             while (rset.next()){
                 if (i == 0){
                     out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                     i = 1;
                 }else{
                     out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                     i = 0;
                 }
                 out.println("<td align='left'>");
                 out.println(rset.getString("data"));
                 out.println("</td>");
                 out.println("<td align='center'>");
                 out.println(rset.getString("horini"));
                 out.println("</td>");
                 out.println("<td align='center'>");
                 out.println(rset.getString("horfin"));
                 out.println("</td>");
                 out.println("<td align='center'>");
                 out.println(rset.getString("tempo"));
                 out.println("</td>");
                 out.println("<td align='left'>");
                 out.println(rset.getString("motivo"));
                 out.println("</td>");
                 out.println("</tr>");
             }

             out.println("</table></form>");
                     
             rset.close();
             stmt.close();
             con.close();
             rset = null;
             stmt = null;
             con  = null;
             
             }
     catch (Exception e)
            {
                out.println(e);
                con.close();
                con = null;
            }
        %>

</body>
</html>
