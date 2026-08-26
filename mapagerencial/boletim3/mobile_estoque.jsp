
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
    Integer nCodConsulta = 0;
    try{
        if (request.getParameter("cod") != null) {
            nCodConsulta = Integer.parseInt(request.getParameter("cod").toString());
        }

        Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
        con = DriverManager.getConnection("jdbc:oracle:thin:@172.16.0.64:1521:VETORH","sifrota","edisa95");
        Statement stmt = con.createStatement();
        ResultSet rset, rsetDatHor;
        rsetDatHor = stmt.executeQuery("select to_char(sysdate,'dd/mm/rrrr hh24:mi') dathor from dual");
        rsetDatHor.next();
        String dathor = rsetDatHor.getString("dathor");
        rsetDatHor.close(); rsetDatHor = null;
        
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        rset = stmt.executeQuery("select 1 ordem_item, 'Estoque de Álcool Anidro' descricao, 'LTS' unidade, '' data, nk_formatanumero(to_number(b.valor_saf),0) valor from sigind.vars a, sigind.db_acum b " +
                                 "where a.codigo in ('ESALA') " +
                                 "and   a.codigo = b.varia " +
                                 "and   b.data = trunc(sysdate) - 1 " +
                                 " " +
                                 "union all " +
                                 " " +
                                 "select 2 ordem_item, 'Estoque de Álcool Hidratado' descricao, 'LTS' unidade, '' data, nk_formatanumero(to_number(b.valor_saf),0) valor from sigind.vars a, sigind.db_acum b " +
                                 "where a.codigo in ('ESALH') " +
                                 "and   a.codigo = b.varia " +
                                 "and   b.data = trunc(sysdate) - 1 " +
                                 " " +
                                 "union all " +
                                 "select 4 ordem_item, 'Estoque de Açúcar VHP' descricao, 'TON' unidade, '' data, nk_formatanumero(to_number(valor_saf)*50/1000,3) valor " +
                                 "from sigind.db_acum " +
                                 "where varia = 'ESAVHP' " +
                                 "and   data = trunc(sysdate) - 1 " +
                                 " " +
                                 "union all " +
                                 " " +
                                 "select 5 ordem_item, 'Estoque de Açúcar VHP' descricao, 'TON' unidade, (select to_char(max(datreg),'dd/mm/rrrr') from rocadinho.estacuempat@pirprod " +
                                 "                                                             where datreg <= sysdate) data, nk_formatanumero(qtdatu,3) valor " +
                                 "from rocadinho.estacuempat@pirprod " +
                                 "where datreg = (select max(datreg) from rocadinho.estacuempat@pirprod " +
                                 "                where datreg <= sysdate) " +
                                 "and   datreg >= '01/09/2012' " +
                                 "order by ordem_item");

             out.println("<form>");
             out.println("<table width='100%' style='font-family: verdana' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

             out.println("<tr bgcolor='#009999' style='color: white; font-size: 25px'>");
             out.println("<td align='center' ><b>");
             out.println("Estoques - Posição em " + dathor + "hs");
             out.println("</b></td></tr></table>");

             out.println("<table style='font-family: verdana' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td colspan=3 align='center' ><b>");
             out.println("Estoques na Usina");
             out.println("</b></td></tr>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td align='center'><b>");
             out.println("Item");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Unid");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Estoque");
             out.println("</b></td>");
             out.println("</tr>");

             int i = 0;
             while (rset.next()){
                 if (rset.getString("ordem_item").equals("5")){
                     out.println("<tr bgcolor='#0099CC' style='color: white'>");
                     out.println("<td colspan=3 align='center' ><b>");
                     out.println("Estoques na Empat em " + rset.getString("data"));
                     out.println("</b></td></tr>");
                     out.println("<tr bgcolor='#0099CC' style='color: white'>");
                     out.println("<td align='center'><b>");
                     out.println("Item");
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Unid");
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Estoque");
                     out.println("</b></td>");
                     out.println("</tr>");
                 }
                 if (i == 0){
                     out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                     i = 1;
                 }else{
                     out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                     i = 0;
                 }
                 out.println("<td align='left'>");
                 out.println(rset.getString("descricao"));
                 out.println("</td>");
                 out.println("<td align='center'>");
                 out.println(rset.getString("unidade"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("valor"));
                 out.println("</td>");
                 out.println("</tr>");
             }

             out.println("</table>");

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
