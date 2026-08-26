
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
        con = DriverManager.getConnection("jdbc:oracle:thin:@172.16.0.64:1521:PIRAMIDE","rocadinho","msrocadinho");
        Statement stmt = con.createStatement();
        ResultSet rset;
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        rset = stmt.executeQuery("select to_number(decode(b.codigo,'09','-1','10','00',b.codigo)) codigo, decode(b.nome,'','TOTAL GERAL',b.nome) nome, \n" +
                                 "       trim(to_char(trunc(sysdate),'dd/mm'))||'/'||  \n" +
                                 "       trim(to_char(to_number(trim(to_char(trunc(sysdate),'RRRR')))-1)) dt_ano_passado,  \n" +
                                 "       nk_formatanumero(sum(case when a.data = trunc(sysdate)   then a.precipitacao else 0 end),0) hoje,  \n" +
                                 "       nk_formatanumero(sum(case when a.data = trunc(sysdate)-1 then a.precipitacao else 0 end),0) ontem,  \n" +
                                 "       nk_formatanumero(sum(case when a.data between nk_primdiasem(to_char(trunc(sysdate),'dd/mm/rrrr')) and   \n" +
                                 "                               trunc(sysdate) then a.precipitacao else 0 end),0) semana,  \n" +
                                 "       nk_formatanumero(sum(case when a.data between to_date('01/'||trim(to_char(trunc(sysdate),'MM/RRRR')),'dd/mm/rrrr') and   \n" +
                                 "                               trunc(sysdate) then a.precipitacao else 0 end),0) mes,  \n" +
                                 "       nk_formatanumero(sum(case when a.data between to_date('01/09/2012','dd/mm/rrrr') and   \n" +
                                 "                               trunc(sysdate) then a.precipitacao else 0 end),0) safra,   \n" +
                                 "       nk_formatanumero(sum(case when a.data between to_date('01/01/'||trim(to_char(trunc(sysdate),'RRRR')),'dd/mm/rrrr') and   \n" +
                                 "                               trunc(sysdate) then a.precipitacao else 0 end),0) ano_atual,  \n" +
                                 "       nk_formatanumero(sum(case when a.data between to_date('01/01/'||trim(to_char(to_number(trim(to_char(trunc(sysdate),'RRRR')))-1)),'dd/mm/rrrr') and   \n" +
                                 "                               to_date(trim(to_char(trunc(sysdate),'dd/mm'))||'/'||  \n" +
                                 "                                       trim(to_char(to_number(trim(to_char(trunc(sysdate),'RRRR')))-1)),'dd/mm/rrrr') then a.precipitacao else 0 end),0) ano_passado_hoje,  \n" +
                                 "       nk_formatanumero(sum(case when a.data between to_date('01/01/'||trim(to_char(to_number(trim(to_char(trunc(sysdate),'RRRR')))-1)),'dd/mm/rrrr') and   \n" +
                                 "                               to_date('31/12/'||  \n" +
                                 "                                       trim(to_char(to_number(trim(to_char(trunc(sysdate),'RRRR')))-1)),'dd/mm/rrrr') then a.precipitacao else 0 end),0) ano_passado_total  \n" +
                                 "from sifrota.clima@sifprod a, sifrota.postomet@sifprod b  \n" +
                                 "where a.posto = b.codigo  \n" +
                                 "and   a.data between to_date('01/01/'||trim(to_char(to_number(trim(to_char(trunc(sysdate),'RRRR')))-1)),'dd/mm/rrrr') and trunc(sysdate)  \n" +
                                 "group by to_number(decode(b.codigo,'09','-1','10','00',b.codigo)), b.nome \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select to_number('99') codigo, 'TOTAL GERAL' nome, max(dt_ano_passado) dt_ano_passado, \n" +
                                 "       nk_formatanumero(avg(hoje),1) hoje, \n" +
                                 "       nk_formatanumero(avg(ontem),1) ontem, \n" +
                                 "       nk_formatanumero(avg(semana),1) semana, \n" +
                                 "       nk_formatanumero(avg(mes),1) mes, \n" +
                                 "       nk_formatanumero(avg(safra),0) safra, \n" +
                                 "       nk_formatanumero(avg(ano_atual),0) ano_atual, \n" +
                                 "       nk_formatanumero(avg(ano_passado_hoje),0) ano_passado_hoje, \n" +
                                 "       nk_formatanumero(avg(ano_passado_total),0) ano_passado_total \n" +
                                 "from (        \n" +
                                 "select to_number(decode(b.codigo,'09','-1','10','00',b.codigo)) codigo, decode(b.nome,'','TOTAL GERAL',b.nome) nome,  \n" +
                                 "       trim(to_char(trunc(sysdate),'dd/mm'))||'/'||  \n" +
                                 "       trim(to_char(to_number(trim(to_char(trunc(sysdate),'RRRR')))-1)) dt_ano_passado,  \n" +
                                 "       sum(case when a.data = trunc(sysdate)   then a.precipitacao else 0 end) hoje,  \n" +
                                 "       sum(case when a.data = trunc(sysdate)-1 then a.precipitacao else 0 end) ontem,  \n" +
                                 "       sum(case when a.data between nk_primdiasem(to_char(trunc(sysdate),'dd/mm/rrrr')) and   \n" +
                                 "                               trunc(sysdate) then a.precipitacao else 0 end) semana,  \n" +
                                 "       sum(case when a.data between to_date('01/'||trim(to_char(trunc(sysdate),'MM/RRRR')),'dd/mm/rrrr') and   \n" +
                                 "                               trunc(sysdate) then a.precipitacao else 0 end) mes,  \n" +
                                 "       sum(case when a.data between to_date('01/09/2012','dd/mm/rrrr') and   \n" +
                                 "                               trunc(sysdate) then a.precipitacao else 0 end) safra,   \n" +
                                 "       sum(case when a.data between to_date('01/01/'||trim(to_char(trunc(sysdate),'RRRR')),'dd/mm/rrrr') and   \n" +
                                 "                               trunc(sysdate) then a.precipitacao else 0 end) ano_atual,  \n" +
                                 "       sum(case when a.data between to_date('01/01/'||trim(to_char(to_number(trim(to_char(trunc(sysdate),'RRRR')))-1)),'dd/mm/rrrr') and   \n" +
                                 "                               to_date(trim(to_char(trunc(sysdate),'dd/mm'))||'/'||  \n" +
                                 "                                       trim(to_char(to_number(trim(to_char(trunc(sysdate),'RRRR')))-1)),'dd/mm/rrrr') then a.precipitacao else 0 end) ano_passado_hoje,  \n" +
                                 "       sum(case when a.data between to_date('01/01/'||trim(to_char(to_number(trim(to_char(trunc(sysdate),'RRRR')))-1)),'dd/mm/rrrr') and   \n" +
                                 "                               to_date('31/12/'||  \n" +
                                 "                                       trim(to_char(to_number(trim(to_char(trunc(sysdate),'RRRR')))-1)),'dd/mm/rrrr') then a.precipitacao else 0 end) ano_passado_total  \n" +
                                 "from sifrota.clima@sifprod a, sifrota.postomet@sifprod b  \n" +
                                 "where a.posto = b.codigo  \n" +
                                 "and   a.data between to_date('01/01/'||trim(to_char(to_number(trim(to_char(trunc(sysdate),'RRRR')))-1)),'dd/mm/rrrr') and trunc(sysdate)  \n" +
                                 "group by to_number(decode(b.codigo,'09','-1','10','00',b.codigo)), b.nome) \n" +
                                 " \n" +
                                 "order by 1");

             out.println("<form>");
             out.println("<table width='100%' bgcolor='#00CCCC' style='font-family: verdana; font-size: 12px' cellpadding='1' cellspacing='1' border='0'>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td colspan=9 align='center' ><b>");
             out.println("Mapa de Chuva - Pluviometria em milímetros");
             out.println("</b></td></tr>");

             int i = -1;

             while (rset.next()){

                 if (i == -1){
                     i = 0;
                     out.println("<tr bgcolor='#0099CC' style='color: white; font-family: verdana; font-size: 11px'>");
                     out.println("<td align='center'><b>");
                     out.println("Postos de Medição");
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Hoje");
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Ontem");
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Semana");
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Mês");
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Safra");
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Ano Atual");
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Ano 2011 até " + rset.getString("dt_ano_passado"));
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Ano 2011 Total");
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
                 out.println("<td align='left' width='300' style='font-family: verdana; font-size: 12px'>");
                 out.println(rset.getString("nome"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("hoje"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("ontem"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("semana"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("mes"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("safra"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("ano_atual"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("ano_passado_hoje"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("ano_passado_total"));
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
