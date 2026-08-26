
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
        con = DriverManager.getConnection("jdbc:oracle:thin:@172.16.0.64:1521:VETORH","sifrota","edisa95");
        Statement stmt = con.createStatement();
        ResultSet rset, rsetDatHor;
        rsetDatHor = stmt.executeQuery("select to_char(sysdate,'dd/mm/rrrr hh24:mi') dathor from dual");
        rsetDatHor.next();
        String dathor = rsetDatHor.getString("dathor");
        rsetDatHor.close(); rsetDatHor = null;
        
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        rset = stmt.executeQuery("select substr(a.hora,7,7) hora, rocadinho.nk_formatanumero@pirprod(sum(b.plf/1000),3) ton, " +
                                 "       to_char(trunc(avg(c.distancia)),'00')||'km ' distancia, " +
                                 "       to_char(count(distinct b.viagem),'00')||' ' viagens, " +
                                 "       trim(to_char(trunc(avg(horas_viaje)),'9000'))||':'|| " +
                                 "       trim(to_char(trunc((avg(horas_viaje) - trunc(avg(horas_viaje))) * 60),'00')) tempo " +
                                 "from (select hora from ( " +
                                 "select distinct  " +
                                 "       case when substr(a.hora_sai,1,2) between '00' and '01' then '00-01-00-02hs' else case when substr(a.hora_sai,1,2) between '02' and '03' then '02-03-02-04hs' else " +
                                 "       case when substr(a.hora_sai,1,2) between '04' and '05' then '04-05-04-06hs' else case when substr(a.hora_sai,1,2) between '06' and '07' then '06-07-06-08hs' else " +
                                 "       case when substr(a.hora_sai,1,2) between '08' and '09' then '08-09-08-10hs' else case when substr(a.hora_sai,1,2) between '10' and '11' then '10-11-10-12hs' else " +
                                 "       case when substr(a.hora_sai,1,2) between '12' and '13' then '12-13-12-14hs' else case when substr(a.hora_sai,1,2) between '14' and '15' then '14-15-14-16hs' else " +
                                 "       case when substr(a.hora_sai,1,2) between '16' and '17' then '16-17-16-18hs' else case when substr(a.hora_sai,1,2) between '18' and '19' then '18-19-18-20hs' else " +
                                 "       case when substr(a.hora_sai,1,2) between '20' and '21' then '20-21-20-22hs' else case when substr(a.hora_sai,1,2) between '22' and '23' then '22-03-22-00hs' end " +
                                 "       end end end end end end end end end end end hora " +
                                 "from sifrota.ent_saf a " +
                                 "where planta = '0' and dia = trunc(sysdate))) a, sifrota.ent_saf b, sifrota.fazendas c " +
                                 "where b.dia = trunc(sysdate) " +
                                 "and   b.fazenda = c.codigo " +
                                 "and   b.setor   = c.setor " +
                                 "and   b.planta = '0' " +
                                 "and   substr(b.hora_sai,1,2) between substr(a.hora,1,2) and substr(a.hora,4,2) " +
                                 "group by a.hora " +
                                 "order by a.hora");


             out.println("<form>");
             out.println("<table width='100%' style='font-family: verdana' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

             out.println("<tr bgcolor='#009999' style='color: white; font-size: 25px'>");
             out.println("<td align='center' ><b>");
             out.println("Boletim de Frota - Posição em " + dathor + "hs");
             out.println("</b></td></tr></table>");

             out.println("<table style='font-family: verdana' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td colspan=5 align='center' ><b>");
             out.println("Boletim de Frota");
             out.println("</b></td></tr>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td align='center'><b>");
             out.println("Horário");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Ton.Cana");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Dist");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Viagens");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Ida/Volta");
             out.println("</b></td>");
             out.println("</tr>");

             int i = 0;
             while (rset.next()){
                 if (i == 0){
                     out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                     i = 1;
                 }else{
                     out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                     i = 0;
                 }
                 out.println("<td align='center'>");
                 out.println(rset.getString("hora"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("ton"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("distancia"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("viagens"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("tempo") + " Hs");
                 out.println("</td>");

                 out.println("</tr>");
             }

             rset = stmt.executeQuery("select nvl((select count(distinct viagem) from sifrota.entradas),0) carros_fabrica, " +
                                      "       nvl((select count(distinct eq) qtdvei  " +
                                      "            from sifrota.os_sif a, sifrota.veiculos b " +
                                      "            where a.eq = b.codigo " +
                                      "            and   a.cancelada = 0 " +
                                      "            and   a.encerrada = 0 " +
                                      "            and   b.clasoper = '000002'),0) carros_oficina from dual");
             rset.next();
             out.println("<tr bgcolor='white'><td colspan=5 align='left'>&nbsp</td></tr>");
             out.println("<tr><td colspan=4 align='left'>");
             out.println("Qtd.Carros dentro da Fábrica");
             out.println("</td>");
             out.println("<td align='center'>");
             out.println(rset.getString("carros_fabrica"));
             out.println("</td></tr>");

             out.println("<tr><td colspan=4 align='left'>");
             out.println("Qtd.Carros na Oficina");
             out.println("</td>");
             out.println("<td align='center'>");
             out.println(rset.getString("carros_oficina"));
             out.println("</td></tr>");

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
