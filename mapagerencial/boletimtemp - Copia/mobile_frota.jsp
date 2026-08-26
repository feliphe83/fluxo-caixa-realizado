<%@include file="validausuario.jsp"%>

<%@page import="java.text.DecimalFormat"%>
<%@page import="java.util.Set"%>
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
        <script language="JavaScript">

            function acionatela(sCodigo, sTipo){
                window.open("mobile_frota_detalhe.jsp?codigo=" + sCodigo + "&tipo=" + sTipo,'frmTexto');
            }

            function redimensiona(frm){
               document.getElementById('frmTexto').height = document.body.scrollHeight+10;
               alert(frm);
            }

        </script>

    </head>
    <body>

    <%@ page language="java" import="java.sql.*, java.io.*"%>

    <%

    String sCodSafra = "71" ;
        
    Connection con = null;
    try{
 
        Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
        con = DriverManager.getConnection("jdbc:oracle:thin:@123.0.0.200:1521:o9i","consulta","consulta");
        Statement stmt = con.createStatement();
        ResultSet rset, rsetDatHor;
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        rsetDatHor = stmt.executeQuery("select to_char(sysdate,'dd/mm/rrrr hh24:mi') dathor from dual");
        rsetDatHor.next();
        String dathor = rsetDatHor.getString("dathor");
        rsetDatHor.close(); rsetDatHor = null;

        DecimalFormat nf2 = new DecimalFormat("#,##0.00"); 
        DecimalFormat nf3 = new DecimalFormat("#,##0.000"); 
        DecimalFormat nf4 = new DecimalFormat("#,##0.0000"); 
        
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        rset = stmt.executeQuery("select 1 tipo, d.cod_tipoequipamento, e.descricaotipoequipamento, \n" +
                                 "       sum(case when a.datamovimento = trunc(sysdate)   then b.pesoliquido else 0 end) hoje, \n" +
                                 "       sum(case when a.datamovimento = trunc(sysdate)-1 then b.pesoliquido else 0 end) ontem, \n" +
                                 "       sum(case when a.datamovimento between trunc(sysdate) -  \n" +
                                 "                     decode(to_char(sysdate,'d'),1,6,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)  \n" +
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
                                 "and   c.ativo            = 'S' \n" +
                                 "and   trunc(sysdate) between d.data_inicio and nvl(d.data_fim, trunc(sysdate)) \n" +
                                 "and   d.cod_tipoequipamento = e.cod_tipoequipamento \n" +
                                 "group by d.cod_tipoequipamento, e.descricaotipoequipamento \n" +
                                 "order by 1, 2, 3");

        out.println("<form>");
        out.println("<table width='100%' style='font-family: verdana; font-size: 12px' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

        out.println("<tr bgcolor='#009999' style='color: white; font-size: 25px'>");
        out.println("<td align='center' ><b>");
        out.println("Produtividade da Frota - Posição em " + dathor + "hs");
        out.println("</b></td></tr></table>");

        out.println("<table width='100%' border='1'><tr><td width='40%' align='left' valign='top'>");

        out.println("<table width='100%' style='font-family: verdana; font-size: 12px' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td colspan=7 align='center' ><b>");
        out.println("Transporte de Cana por Tipo de Equipamento");
        out.println("</b></td></tr>");
        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td colspan=5 align='center' ><b>");
        out.println("Frota Total");
        out.println("</b></td></tr>");
             
        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td align='center'><b>");
        out.println("Tipo Equipamento");
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
                out.println("<tr onclick=\"acionatela('" + rset.getString("cod_tipoequipamento") + "','E');\""+   
                        " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                i = 1;
            }else{
                out.println("<tr onclick=\"acionatela('" + rset.getString("cod_tipoequipamento") + "','E');\""+   
                        " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                i = 0;
            }
            out.println("<td align='left'>");
            out.println(rset.getString("descricaotipoequipamento"));
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
        out.println("<td align='left'>");
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
        
        //
        // Colhedora e Carregadeiras
        //
        rset = stmt.executeQuery("select d.cod_tipoequipamento, e.descricaotipoequipamento,  \n" +
                                 "       sum(case when a.datamovimento = trunc(sysdate)   then b.pesoliquido else 0 end) hoje, \n" +
                                 "       sum(case when a.datamovimento = trunc(sysdate)-1 then b.pesoliquido else 0 end) ontem, \n" +
                                 "       sum(case when a.datamovimento between trunc(sysdate) -  \n" +
                                 "                     decode(to_char(sysdate,'d'),1,6,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)  \n" +
                                 "                then b.pesoliquido else 0 end) semana,  \n" +
                                 "       sum(b.pesoliquido) safra \n" +
                                 "from agricola.entradacana a,  \n" +
                                 "     agricola.itensentradacana b, \n" +
                                 "     agricola.itensentradacana_equip f, \n" +
                                 "     automotivo.equipamento c,  \n" +
                                 "     automotivo.historico_tipoequipamento d,  \n" +
                                 "     automotivo.tipoequipamento e \n" +
                                 "where a.cod_grupoempresa = 1 \n" +
                                 "and   a.cod_empresa      = 1 \n" +
                                 "and   a.cod_filial       = 1 \n" +
                                 "and   a.cod_safra        = " + sCodSafra + " \n" +
                                 "and   a.cod_grupoempresa = b.cod_grupoempresa \n" +
                                 "and   a.cod_empresa      = b.cod_empresa \n" +
                                 "and   a.cod_filial       = b.cod_filial \n" +
                                 "and   a.cod_safra        = b.cod_safra \n" +
                                 "and   a.cod_entradacana  = b.cod_entradacana \n" +
                                 "and   a.importado <> 'S' \n" +        
                                 "and   b.cod_grupoempresa = f.cod_grupoempresa \n" +
                                 "and   b.cod_empresa      = f.cod_empresa \n" +
                                 "and   b.cod_filial       = f.cod_filial \n" +
                                 "and   b.cod_safra        = f.cod_safra \n" +
                                 "and   b.cod_entradacana  = f.cod_entradacana \n" +
                                 "and   b.seq_itensentradacana =  f.seq_itensentradacana \n" +
                                 "and   f.cod_funcao_equip in (3, 5, 7) \n" +
                                 "and   f.cod_equipamento  = c.cod_equipamento \n" +
                                 "and   c.cod_equipamento  = d.cod_equipamento \n" +
                                 "and   trunc(a.datamovimento) between d.data_inicio and nvl(d.data_fim, trunc(sysdate)) \n" +
                                 "and   d.cod_tipoequipamento = e.cod_tipoequipamento \n" +
                                 "group by d.cod_tipoequipamento, e.descricaotipoequipamento \n" +
                                 "order by 1, 2");
        //
        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td colspan=7 align='center' ><b>");
        out.println("Carregamento de Cana por Tipo de Equipamento");
        out.println("</b></td></tr>");
        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td colspan=5 align='center' ><b>");
        out.println("Frota Total");
        out.println("</b></td></tr>");
             
        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td align='center'><b>");
        out.println("Tipo Equipamento");
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

        i = 0;
        
        nHoje   = 0;
        nOntem  = 0;
        nSemana = 0;
        nSafra  = 0;

        while (rset.next()){
            nHoje   = nHoje   + rset.getFloat("hoje");
            nOntem  = nOntem  + rset.getFloat("ontem");
            nSemana = nSemana + rset.getFloat("semana");
            nSafra  = nSafra  + rset.getFloat("safra");
            if (i == 0){
                out.println("<tr onclick=\"acionatela('" + rset.getString("cod_tipoequipamento") + "','C');\""+   
                        " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                i = 1;
            }else{
                out.println("<tr onclick=\"acionatela('" + rset.getString("cod_tipoequipamento") + "','C');\""+   
                        " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                i = 0;
            }
            out.println("<td align='left'>");
            out.println(rset.getString("descricaotipoequipamento"));
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
        out.println("<td align='left'>");
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
        
        //
        // Por proprietário
        //
        rset = stmt.executeQuery("select g.cod_fornecedor, h.nome, \n" +
                                 "       sum(case when a.datamovimento = trunc(sysdate)   then b.pesoliquido else 0 end) hoje, \n" +
                                 "       sum(case when a.datamovimento = trunc(sysdate)-1 then b.pesoliquido else 0 end) ontem, \n" +
                                 "       sum(case when a.datamovimento between trunc(sysdate) -  \n" +
                                 "                     decode(to_char(sysdate,'d'),1,6,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)  \n" +
                                 "                then b.pesoliquido else 0 end) semana,  \n" +
                                 "       sum(b.pesoliquido) safra \n" +
                                 "from agricola.entradacana a,  \n" +
                                 "     agricola.itensentradacana b, \n" +
                                 "     automotivo.equipamento c,  \n" +
                                 "     automotivo.historico_tipoequipamento d,  \n" +
                                 "     automotivo.tipoequipamento e, \n" +
                                 "     automotivo.histproprietarioequip f, \n" +
                                 "     material.fornecedor g, \n" +
                                 "     rh.pessoa h \n" +
                                 "where a.cod_grupoempresa = 1 \n" +
                                 "and   a.cod_empresa      = 1 \n" +
                                 "and   a.cod_filial       = 1 \n" +
                                 "and   a.cod_safra        = " + sCodSafra + " \n" +
                                 "and   a.cod_grupoempresa = b.cod_grupoempresa \n" +
                                 "and   a.cod_empresa      = b.cod_empresa \n" +
                                 "and   a.cod_filial       = b.cod_filial \n" +
                                 "and   a.cod_safra        = b.cod_safra \n" +
                                 "and   a.cod_entradacana  = b.cod_entradacana \n" +
                                 "and   a.cod_equipamento  = c.cod_equipamento \n" +
                                 "and   a.importado <> 'S' \n" +        
                                 "and   c.cod_equipamento  = d.cod_equipamento \n" +
                                 "and   trunc(sysdate) between d.data_inicio and nvl(d.data_fim, trunc(sysdate)) \n" +
                                 "and   d.cod_tipoequipamento = e.cod_tipoequipamento \n" +
                                 "and   trunc(sysdate) between f.data_inicial and nvl(f.data_final, trunc(sysdate)) \n" +
                                 "and   f.cod_equipamento  = c.cod_equipamento \n" +
                                 "and  (g.cpf = f.cpf or g.cgc = f.cgc) \n" +
                                 "and   f.tipo_proprietario <> 'P' \n" +
                                 "and   h.cod_pessoa       = g.cod_pessoa \n" +
                                 "group by g.cod_fornecedor, h.nome \n" +
                                 " \n" +
                                 "union  \n" +
                                 " \n" +
                                 "select 1, ' USINA SANTA CLOTILDE' nome, \n" +
                                 "       sum(case when a.datamovimento = trunc(sysdate)   then b.pesoliquido else 0 end) hoje, \n" +
                                 "       sum(case when a.datamovimento = trunc(sysdate)-1 then b.pesoliquido else 0 end) ontem, \n" +
                                 "       sum(case when a.datamovimento between trunc(sysdate) -  \n" +
                                 "                     decode(to_char(sysdate,'d'),1,6,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)  \n" +
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
                                 "order by 2");
        //
        out.println("<tr bgcolor='#0099CC' style='color: white'>");

        out.println("<td colspan=5 align='center' ><b>");
        out.println("Transporte de Cana por Proprietário");
        out.println("</b></td></tr>");

        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td align='center'><b>");
        out.println("Proprietário");
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

        i = 0;

        nHoje   = 0;
        nOntem  = 0;
        nSemana = 0;
        nSafra  = 0;

        while (rset.next()){
            nHoje   = nHoje   + rset.getFloat("hoje");
            nOntem  = nOntem  + rset.getFloat("ontem");
            nSemana = nSemana + rset.getFloat("semana");
            nSafra  = nSafra  + rset.getFloat("safra");
            if (i == 0){
                out.println("<tr onclick=\"acionatela('" + rset.getString("cod_fornecedor") + "','P');\""+   
                        " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                i = 1;
            }else{
                out.println("<tr onclick=\"acionatela('" + rset.getString("cod_fornecedor") + "','P');\""+   
                        " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                i = 0;
            }
            out.println("<td align='left'>");
            out.println(rset.getString("nome"));
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
        out.println("<td align='left'>");
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
             

        out.println("</table>");

        out.println("</td><td valign='top' background='yellow'><iframe src='#' width='100%' marginwidth='0' marginheight='0' height='1000px' name='frmTexto' id='ifrmTexto'></iframe></td></tr>");
      //out.println("<tr><td align='left' valign='top'><iframe src='mobile_chuva.jsp' width='100%' marginwidth='0' marginheight='0' height='230px' name='frmChuva' id='frmChuva'></iframe></td></tr>");
      //out.println("<tr><td align='left' valign='top' colspan='2'><iframe src='mobile_chuva.jsp' width='100%' marginwidth='0' marginheight='0' height='230px' name='ifrmChuva' id='ifrmChuva'></iframe></td></tr>");

        out.println("</table>");
             
        out.println("<script>acionatela('0','T');</script>'");
        rset.close();
        stmt.close();
        con.close();
        rset = null;
        rsetDatHor = null;
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
