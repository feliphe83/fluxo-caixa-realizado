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
        
    String sCodSafra = "74"; try { java.io.File _sf = new java.io.File(application.getRealPath("/safra.txt")); if (_sf.exists()) { String _sv = new String(java.nio.file.Files.readAllBytes(_sf.toPath())).trim(); Integer.parseInt(_sv); sCodSafra = _sv; } } catch (Exception _e) {}

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
        
        if (request.getParameter("tipo").equals("P") && request.getParameter("codigo").equals("1")){
            sSql = "select d.cod_tipoequipamento, e.descricaotipoequipamento, a.cod_equipamento, c.descricao, \n" +
                   "       sum(case when a.datamovimento = trunc(sysdate)   then b.pesoliquido else 0 end) hoje, \n" +
                   "       sum(case when a.datamovimento = trunc(sysdate)-1 then b.pesoliquido else 0 end) ontem, \n" +
                   "       sum(case when a.datamovimento between trunc(sysdate) -  \n" +
                   "                     decode(to_char(sysdate,'d'),1,7,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)  \n" +
                   "                then b.pesoliquido else 0 end) semana,  \n" +
                   "       sum(b.pesoliquido) safra \n" +
                   "from agricola.entradacana a,  \n" +
                   "     agricola.itensentradacana b, \n" +
                   "     automotivo.equipamento c,  \n" +
                   "     automotivo.historico_tipoequipamento d,  \n" +
                   "     automotivo.tipoequipamento e, \n" +
                   "     automotivo.histproprietarioequip f \n" +
                   "where a.cod_grupoempresa = 1 \n" +
                   "and   a.cod_empresa      = 1 \n" +
                   "and   a.cod_filial       = 1 \n" +
                   "and   a.cod_safra        = " + sCodSafra + " \n" +
                   "and   a.importado <> 'S' \n" +        
                   "and   a.cod_grupoempresa = b.cod_grupoempresa \n" +
                   "and   a.cod_empresa      = b.cod_empresa \n" +
                   "and   a.cod_filial       = b.cod_filial \n" +
                   "and   a.cod_safra        = b.cod_safra \n" +
                   "and   a.cod_entradacana  = b.cod_entradacana \n" +
                   "and   a.cod_equipamento  = c.cod_equipamento \n" +
                   "and   c.cod_equipamento  = d.cod_equipamento \n" +
                   "and   trunc(sysdate) between d.data_inicio and nvl(d.data_fim, trunc(sysdate)) \n" +
                   "and   d.cod_tipoequipamento = e.cod_tipoequipamento \n" +
                   "and   trunc(sysdate) between f.data_inicial and nvl(f.data_final, trunc(sysdate)) \n" +
                   "and   f.cod_equipamento  = c.cod_equipamento \n" +
                   "and   f.tipo_proprietario= 'P' \n" +
                   "and   f.cod_empresa      = 1 \n" +
                   "group by d.cod_tipoequipamento, e.descricaotipoequipamento, a.cod_equipamento, c.descricao \n" +
                   "order by a.cod_equipamento";
        }
        
        if (request.getParameter("tipo").equals("P") && request.getParameter("codigo").equals("3383")){
            sSql = "select d.cod_tipoequipamento, e.descricaotipoequipamento, a.cod_equipamento, c.descricao, \n" +
                   "       sum(case when a.datamovimento = trunc(sysdate)   then b.pesoliquido else 0 end) hoje, \n" +
                   "       sum(case when a.datamovimento = trunc(sysdate)-1 then b.pesoliquido else 0 end) ontem, \n" +
                   "       sum(case when a.datamovimento between trunc(sysdate) -  \n" +
                   "                     decode(to_char(sysdate,'d'),1,7,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)  \n" +
                   "                then b.pesoliquido else 0 end) semana,  \n" +
                   "       sum(b.pesoliquido) safra \n" +
                   "from agricola.entradacana a,  \n" +
                   "     agricola.itensentradacana b, \n" +
                   "     automotivo.equipamento c,  \n" +
                   "     automotivo.historico_tipoequipamento d,  \n" +
                   "     automotivo.tipoequipamento e, \n" +
                   "     automotivo.histproprietarioequip f \n" +
                   "where a.cod_grupoempresa = 1 \n" +
                   "and   a.cod_empresa      = 1 \n" +
                   "and   a.cod_filial       = 1 \n" +
                   "and   a.cod_safra        = " + sCodSafra + " \n" +
                   "and   a.importado <> 'S' \n" +        
                   "and   a.cod_grupoempresa = b.cod_grupoempresa \n" +
                   "and   a.cod_empresa      = b.cod_empresa \n" +
                   "and   a.cod_filial       = b.cod_filial \n" +
                   "and   a.cod_safra        = b.cod_safra \n" +
                   "and   a.cod_entradacana  = b.cod_entradacana \n" +
                   "and   a.cod_equipamento  = c.cod_equipamento \n" +
                   "and   c.cod_equipamento  = d.cod_equipamento \n" +
                   "and   trunc(sysdate) between d.data_inicio and nvl(d.data_fim, trunc(sysdate)) \n" +
                   "and   d.cod_tipoequipamento = e.cod_tipoequipamento \n" +
                   "and   trunc(sysdate) between f.data_inicial and nvl(f.data_final, trunc(sysdate)) \n" +
                   "and   f.cod_equipamento  = c.cod_equipamento \n" +
                   "and   f.tipo_proprietario= 'P' \n" +
                   "and   f.cod_empresa      = 1 \n" +
                   "group by d.cod_tipoequipamento, e.descricaotipoequipamento, a.cod_equipamento, c.descricao \n" +
                   "order by a.cod_equipamento";
        }
        
        if (request.getParameter("tipo").equals("P") && !request.getParameter("codigo").equals("1") && !request.getParameter("codigo").equals("3383")){
            sSql = "select d.cod_tipoequipamento, e.descricaotipoequipamento, a.cod_equipamento, c.descricao, \n" +
                   "       sum(case when a.datamovimento = trunc(sysdate)   then b.pesoliquido else 0 end) hoje, \n" +
                   "       sum(case when a.datamovimento = trunc(sysdate)-1 then b.pesoliquido else 0 end) ontem, \n" +
                   "       sum(case when a.datamovimento between trunc(sysdate) -  \n" +
                   "                     decode(to_char(sysdate,'d'),1,7,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)  \n" +
                   "                then b.pesoliquido else 0 end) semana,  \n" +
                   "       sum(b.pesoliquido) safra \n" +
                   "from agricola.entradacana a,  \n" +
                   "     agricola.itensentradacana b, \n" +
                   "     automotivo.equipamento c,  \n" +
                   "     automotivo.historico_tipoequipamento d,  \n" +
                   "     automotivo.tipoequipamento e, \n" +
                   "     automotivo.histproprietarioequip f, \n" +
                   "     material.fornecedor g \n" +
                   "where a.cod_grupoempresa = 1 \n" +
                   "and   a.cod_empresa      = 1 \n" +
                   "and   a.cod_filial       = 1 \n" +
                   "and   a.cod_safra        = " + sCodSafra + " \n" +
                   "and   a.importado <> 'S' \n" +        
                   "and   a.cod_grupoempresa = b.cod_grupoempresa \n" +
                   "and   a.cod_empresa      = b.cod_empresa \n" +
                   "and   a.cod_filial       = b.cod_filial \n" +
                   "and   a.cod_safra        = b.cod_safra \n" +
                   "and   a.cod_entradacana  = b.cod_entradacana \n" +
                   "and   a.cod_equipamento  = c.cod_equipamento \n" +
                   "and   c.cod_equipamento  = d.cod_equipamento \n" +
                   "and   trunc(sysdate) between d.data_inicio and nvl(d.data_fim, trunc(sysdate)) \n" +
                   "and   d.cod_tipoequipamento = e.cod_tipoequipamento \n" +
                   "and   trunc(sysdate) between f.data_inicial and nvl(f.data_final, trunc(sysdate)) \n" +
                   "and   f.cod_equipamento  = c.cod_equipamento \n" +
                   "and  (g.cpf = f.cpf or g.cgc = f.cgc) \n" +
                   "and   g.cod_fornecedor = " + request.getParameter("codigo") +
                   "and   f.tipo_proprietario <> 'P' \n" +
                   "group by d.cod_tipoequipamento, e.descricaotipoequipamento, a.cod_equipamento, c.descricao \n" +
                   "order by a.cod_equipamento";
        }
        
        if (request.getParameter("tipo").equals("E") ||
            request.getParameter("tipo").equals("T")){
            sSql = "select d.cod_tipoequipamento, e.descricaotipoequipamento, a.cod_equipamento, c.descricao, \n" +
                   "       sum(case when a.datamovimento = trunc(sysdate)   then b.pesoliquido else 0 end) hoje, \n" +
                   "       sum(case when a.datamovimento = trunc(sysdate)-1 then b.pesoliquido else 0 end) ontem, \n" +
                   "       sum(case when a.datamovimento between trunc(sysdate) -  \n" +
                   "                     decode(to_char(sysdate,'d'),1,7,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)  \n" +
                   "                then b.pesoliquido else 0 end) semana,  \n" +
                   "       sum(b.pesoliquido) safra \n" +
                   "from agricola.entradacana a,  \n" +
                   "     agricola.itensentradacana b, \n" +
                   "     automotivo.equipamento c,  \n" +
                   "     automotivo.historico_tipoequipamento d,  \n" +
                   "     automotivo.tipoequipamento e \n" +
                   "where a.cod_grupoempresa = 1 \n" +
                   "and   a.cod_empresa      = 1 \n" +
                   "and   a.cod_filial       = 1 \n" +
                   "and   a.cod_safra        = " + sCodSafra + " \n" +
                   "and   a.importado <> 'S' \n" +        
                   "and   a.cod_grupoempresa = b.cod_grupoempresa \n" +
                   "and   a.cod_empresa      = b.cod_empresa \n" +
                   "and   a.cod_filial       = b.cod_filial \n" +
                   "and   a.cod_safra        = b.cod_safra \n" +
                   "and   a.cod_entradacana  = b.cod_entradacana \n" +
                   "and   a.cod_equipamento  = c.cod_equipamento \n" +
                   "and   c.cod_equipamento  = d.cod_equipamento \n" +
                   "and   trunc(sysdate) between d.data_inicio and nvl(d.data_fim, trunc(sysdate)) \n" +
                   "and   d.cod_tipoequipamento = e.cod_tipoequipamento \n" +
                   (request.getParameter("tipo").equals("E")? "and d.cod_tipoequipamento = " + request.getParameter("codigo") : " ") + 
                   "group by d.cod_tipoequipamento, e.descricaotipoequipamento, a.cod_equipamento, c.descricao \n" +
                   "order by a.cod_equipamento";
        }
        
        if (request.getParameter("tipo").equals("C")){
            sSql = "select d.cod_tipoequipamento, e.descricaotipoequipamento, c.cod_equipamento, c.descricao, \n" +
                   "       sum(case when a.datamovimento = trunc(sysdate)   then b.pesoliquido else 0 end) hoje, \n" +
                   "       sum(case when a.datamovimento = trunc(sysdate)-1 then b.pesoliquido else 0 end) ontem, \n" +
                   "       sum(case when a.datamovimento between trunc(sysdate) -  \n" +
                   "                     decode(to_char(sysdate,'d'),1,7,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)  \n" +
                   "                then b.pesoliquido else 0 end) semana,  \n" +
                   "       sum(b.pesoliquido) safra \n" +
                   "from agricola.entradacana a,  \n" +
                   "     agricola.itensentradacana b, \n" +
                   "     agricola.itensentradacana_equip f, \n" +
                   "     automotivo.equipamento c,  \n" +
                   "     automotivo.historico_tipoequipamento d,  \n" +
                   "     automotivo.tipoequipamento e, \n" +
                   "     automotivo.histproprietarioequip g \n" +
                   "where a.cod_grupoempresa = 1 \n" +
                   "and   a.cod_empresa      = 1 \n" +
                   "and   a.cod_filial       = 1 \n" +
                   "and   a.cod_safra        = " + sCodSafra + " \n" +
                   "and   a.importado <> 'S' \n" +        
                   "and   a.cod_grupoempresa = b.cod_grupoempresa \n" +
                   "and   a.cod_empresa      = b.cod_empresa \n" +
                   "and   a.cod_filial       = b.cod_filial \n" +
                   "and   a.cod_safra        = b.cod_safra \n" +
                   "and   a.cod_entradacana  = b.cod_entradacana \n" +
                   "and   b.cod_grupoempresa = f.cod_grupoempresa \n" +
                   "and   b.cod_empresa      = f.cod_empresa \n" +
                   "and   b.cod_filial       = f.cod_filial \n" +
                   "and   b.cod_safra        = f.cod_safra \n" +
                   "and   b.cod_entradacana  = f.cod_entradacana \n" +
                   "and   b.seq_itensentradacana =  f.seq_itensentradacana \n" +
                   "and   f.cod_funcao_equip in (3, 5) \n" +
                   "and   f.cod_equipamento  = c.cod_equipamento \n" +
                   "and   c.cod_equipamento  = d.cod_equipamento \n" +
                   "and   trunc(sysdate) between d.data_inicio and nvl(d.data_fim, trunc(sysdate)) \n" +
                   "and   d.cod_tipoequipamento = e.cod_tipoequipamento \n" +
                   "and   trunc(sysdate) between g.data_inicial and nvl(g.data_final, trunc(sysdate)) \n" +
                   "and   g.cod_equipamento  = c.cod_equipamento \n" +
                 //"and   g.tipo_proprietario = 'P' \n" +
                   "and d.cod_tipoequipamento = " + request.getParameter("codigo") + " \n" + 
                   "group by d.cod_tipoequipamento, e.descricaotipoequipamento, c.cod_equipamento, c.descricao \n" +
                   "order by c.cod_equipamento";
        }
        
        rset = stmt.executeQuery(sSql);

        out.println("<form>");
        out.println("<table width='100%' bgcolor='#00CCCC' style='font-family: verdana; font-size: 12px' cellpadding='1' cellspacing='1' border='0'>");

        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td colspan=10 align='center' ><b>");
        out.println("Detalhamento do transporte de cana por Equipamento");
        out.println("</b></td></tr>");

        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td align='center'><b>");
        out.println("Codigo");
        out.println("</b></td>");
        out.println("<td align='center'><b>");
        out.println("Equipamento");
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
        out.println("Safra");
        out.println("</b></td>");
        out.println("</tr>");

        int i = 0;

        float nHoje   = 0;
        float nOntem  = 0;
        float nSemana = 0;
        float nSafra  = 0;

        while (rset.next()){
            nHoje   = nHoje   + rset.getFloat("hoje");
            nOntem  = nOntem  + rset.getFloat("ontem");
            nSemana = nSemana + rset.getFloat("semana");
            nSafra  = nSafra  + rset.getFloat("safra");
            if (i == 0){
                out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                i = 1;
            }else{
                out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                i = 0;
            }
            out.println("<td align='right'>");
            out.println(rset.getString("cod_equipamento"));
            out.println("</td>");
            out.println("<td align='left'>");
            out.println(rset.getString("descricao"));
            out.println("</td>");
            out.println("<td align='right'>");
            out.println(nf3.format(rset.getFloat("hoje")));
            out.println("</td>");
            out.println("<td align='right'>");
            out.println(nf3.format(rset.getFloat("ontem")));
            out.println("</td>");
            out.println("<td align='right'>");
            out.println(nf3.format(rset.getFloat("semana")));
            out.println("</td>");
            out.println("<td align='right'>");
            out.println(nf3.format(rset.getFloat("safra")));
            out.println("</td>");
            out.println("</tr>");
        }
        if (i == 0){
            out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black; font-weight:bold'>");
        }else{
            out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black; font-weight:bold'>");
        }
        out.println("<td align='left' colspan='2'>");
        out.println("Total Geral");
        out.println("</td>");
        out.println("<td align='right'>");
        out.println(nf3.format(nHoje));
        out.println("</td>");
        out.println("<td align='right'>");
        out.println(nf3.format(nOntem));
        out.println("</td>");
        out.println("<td align='right'>");
        out.println(nf3.format(nSemana));
        out.println("</td>");
        out.println("<td align='right'>");
        out.println(nf3.format(nSafra));
        out.println("</td>");
        out.println("</tr>");

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
