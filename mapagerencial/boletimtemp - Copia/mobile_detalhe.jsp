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

        String sCodSafra = "71" ;
        
        Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
        con = DriverManager.getConnection("jdbc:oracle:thin:@123.0.0.200:1521:o9i","consulta","consulta");
        Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY);
        ResultSet rset;
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        
        DecimalFormat nf2 = new DecimalFormat(" #,##0.00"); 
        DecimalFormat nf3 = new DecimalFormat(" #,##0.000"); 
        DecimalFormat nf4 = new DecimalFormat(" #,##0.0000"); 
        
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        rset = stmt.executeQuery("SELECT '1' tipo, P.Nome fornecedor, fz.descricao fazenda, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.BRIX_EXTRATO * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) BRIX, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
                                 "     agricola.fazenda fz, \n" +
                                 "     agricola.tipofazenda tpfz, \n" +
                                 "     agricola.historico_fazenda htfz, \n" +
                                 "     rh.pessoa p, \n" +
                                 "     material.fornecedor f \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
								 "AND tpfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_filial = 1 \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                      
                                 "AND   iec.cod_grupoempresa = ec.cod_grupoempresa \n" +
                                 "AND   iec.cod_empresa = ec.cod_empresa \n" +
                                 "AND   iec.cod_filial = ec.cod_filial \n" +
                                 "AND   iec.cod_safra = ec.cod_safra \n" +
                                 "AND   iec.cod_entradacana = ec.cod_entradacana \n" +
                                 "AND   iec.pesoliquido > 0 \n" +
                                 "AND   a_pcts.cod_grupoempresa (+) = iec.cod_grupoempresa \n" +
                                 "AND   a_pcts.cod_empresa (+)= iec.cod_empresa \n" +
                                 "AND   a_pcts.cod_filial (+)= iec.cod_filial \n" +
                                 "AND   a_pcts.cod_safra (+)= iec.cod_safra \n" +
                                 "AND   a_pcts.cod_entradacana (+)= iec.cod_entradacana \n" +
                                 "AND   a_pcts.seq_itensentradacana (+)= iec.seq_itensentradacana \n" +
                                 "AND   oc.cod_grupoempresa  = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa       = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial        = iec.cod_filial \n" +
                                 "AND   oc.cod_safra         = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem      = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana      = oc.cod_tipocana \n" + 
                                 (request.getParameter("tipcol").equals("M")? " and tc.cod_tipocana in (3,4) \n" : " ") +
                                 (request.getParameter("tipcol").equals("MAN")? " and tc.cod_tipocana not in (3,4) \n" : " ") +
                                 (request.getParameter("tipo").equals("1") ||
                                  request.getParameter("tipo").equals("3") ||
                                  request.getParameter("tipo").equals("4") ||
                                  request.getParameter("tipo").equals("5") ? " and htfz.cod_tipofazenda = " + request.getParameter("tipo") + " \n" : " ") +
                                 "AND   fz.cod_fazenda       = iec.cod_fazenda          \n" +
                                 "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
                                 "AND   htfz.cod_tipofazenda = tpfz.cod_tipofazenda \n" +
                                 "and   f.cod_fornecedor     = htfz.cod_fornecedor \n" +
                                 "and   p.cod_pessoa         = f.cod_pessoa \n" +
                                 "and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate)) \n" +
                                 "and   ec.datamovimento between '" + request.getParameter("datini") + "' and '" + request.getParameter("datfin") + "' " +
                                 "and   to_number(substr(ec.horasaida,1,2)) between " + request.getParameter("horini") + " and " + request.getParameter("horfin") + " " +
                                 "group by f.cod_fornecedor, p.nome, fz.cod_fazenda, fz.descricao \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "SELECT '2' tipo, 'TOTAL GERAL' fornecedor, ' ' fazenda, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.BRIX_EXTRATO * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) BRIX, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
                                 "     agricola.fazenda fz, \n" +
                                 "     agricola.tipofazenda tpfz, \n" +
                                 "     agricola.historico_fazenda htfz, \n" +
                                 "     rh.pessoa p, \n" +
                                 "     material.fornecedor f \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
								 "AND tpfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_filial = 1 \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                      
                                 "AND   iec.cod_grupoempresa = ec.cod_grupoempresa \n" +
                                 "AND   iec.cod_empresa = ec.cod_empresa \n" +
                                 "AND   iec.cod_filial = ec.cod_filial \n" +
                                 "AND   iec.cod_safra = ec.cod_safra \n" +
                                 "AND   iec.cod_entradacana = ec.cod_entradacana \n" +
                                 "AND   iec.pesoliquido > 0 \n" +
                                 "AND   a_pcts.cod_grupoempresa (+) = iec.cod_grupoempresa \n" +
                                 "AND   a_pcts.cod_empresa (+)= iec.cod_empresa \n" +
                                 "AND   a_pcts.cod_filial (+)= iec.cod_filial \n" +
                                 "AND   a_pcts.cod_safra (+)= iec.cod_safra \n" +
                                 "AND   a_pcts.cod_entradacana (+)= iec.cod_entradacana \n" +
                                 "AND   a_pcts.seq_itensentradacana (+)= iec.seq_itensentradacana \n" +
                                 "AND   oc.cod_grupoempresa  = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa       = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial        = iec.cod_filial \n" +
                                 "AND   oc.cod_safra         = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem      = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana      = oc.cod_tipocana \n" +
                                 (request.getParameter("tipcol").equals("M")? " and tc.cod_tipocana in (3,4) \n" : " ") +
                                 (request.getParameter("tipcol").equals("MAN")? " and tc.cod_tipocana not in (3,4) \n" : " ") +
                                 (request.getParameter("tipo").equals("1") ||
                                  request.getParameter("tipo").equals("3") ||
                                  request.getParameter("tipo").equals("4") ||
                                  request.getParameter("tipo").equals("5") ? " and htfz.cod_tipofazenda = " + request.getParameter("tipo") + " \n" : " ") +
                                 "AND   fz.cod_fazenda       = iec.cod_fazenda          \n" +
                                 "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
                                 "AND   htfz.cod_tipofazenda = tpfz.cod_tipofazenda \n" +
                                 "and   f.cod_fornecedor     = htfz.cod_fornecedor \n" +
                                 "and   p.cod_pessoa         = f.cod_pessoa \n" +
                                 "and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate)) \n" +
                                 "and   ec.datamovimento between '" + request.getParameter("datini") + "' and '" + request.getParameter("datfin") + "' " +
                                 "and   to_number(substr(ec.horasaida,1,2)) between " + request.getParameter("horini") + " and " + request.getParameter("horfin") + " " +
                                 "order by 1, 2");

             out.println("<form>");
             out.println("<table width='100%' bgcolor='#00CCCC' style='font-family: verdana; font-size: 12px' cellpadding='1' cellspacing='1' border='0'>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td colspan=10 align='center' ><b>");
             out.println("Detalhamento da Entrada de Cana no Período de " + request.getParameter("datini") + " a " +
                                                                            request.getParameter("datfin") + "  -  " +
                                                                            "Horário de " + request.getParameter("horini") + " a " + (Integer.parseInt(request.getParameter("horfin")) + 1) + " Hs");
             out.println("</b></td></tr>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td align='center'><b>");
             out.println("Fornecedor");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Fazenda");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Toneladas");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Brix");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println(" PCC ");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println(" ATR ");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println(" AR ");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Fibra");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println(" PZA ");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println(" TQ ");
             out.println("</b></td>");
             out.println("</tr>");

             int i = 0;
             
             while (rset.next()){
                 if (i == 0){
                     if (rset.getString("tipo").equals("1")){
                         out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                     }else{
                         out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black; font-weight:bold'>");
                     }
                     i = 1;
                 }else{
                     if (rset.getString("tipo").equals("1")){
                         out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                     }else{
                         out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black; font-weight:bold'>");
                     }
                     i = 0;
                 }
                 out.println("<td align='left'>");
                 out.println(rset.getString("fornecedor"));
                 out.println("</td>");
                 out.println("<td align='left'>");
                 out.println(rset.getString("fazenda"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("pesliq") == null?" ":nf3.format(rset.getFloat("pesliq"))));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("brix") == null?" ":nf2.format(rset.getFloat("brix"))));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("pcc") == null?" ":nf4.format(rset.getFloat("pcc"))));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("atr") == null?" ":nf4.format(rset.getFloat("atr"))));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("ar") == null?" ":nf2.format(rset.getFloat("ar"))));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("fibra") == null?" ":nf2.format(rset.getFloat("fibra"))));
                 out.println("</td>");                 
                 out.println("<td align='right'>");
                 out.println((rset.getString("pureza") == null?" ":nf2.format(rset.getFloat("pureza"))));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("tq") == null?" ":rset.getString("tq")));
                 out.println("</td>");
                 out.println("</tr>");
             }

             out.println("</table>");
             
        // Resumo por variedade
             
        rset = stmt.executeQuery("SELECT '1' tipo, var.descricao variedade, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.BRIX_EXTRATO * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) BRIX, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
                                 "     agricola.fazenda fz, \n" +
                                 "     agricola.tipofazenda tpfz, \n" +
                                 "     agricola.historico_fazenda htfz, \n" +
                                 "     rh.pessoa p, \n" +
                                 "     material.fornecedor f, \n" +
                                 "     agricola.talhao tal, \n" +
                                 "     agricola.variedade var \n" +     
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								 "AND tpfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                      
                                 "AND   iec.cod_grupoempresa = ec.cod_grupoempresa \n" +
                                 "AND   iec.cod_empresa = ec.cod_empresa \n" +
                                 "AND   iec.cod_filial = ec.cod_filial \n" +
                                 "AND   iec.cod_safra = ec.cod_safra \n" +
                                 "AND   iec.cod_entradacana = ec.cod_entradacana \n" +
                                 "AND   iec.pesoliquido > 0 \n" +
                                 "AND   a_pcts.cod_grupoempresa (+) = iec.cod_grupoempresa \n" +
                                 "AND   a_pcts.cod_empresa (+)= iec.cod_empresa \n" +
                                 "AND   a_pcts.cod_filial (+)= iec.cod_filial \n" +
                                 "AND   a_pcts.cod_safra (+)= iec.cod_safra \n" +
                                 "AND   a_pcts.cod_entradacana (+)= iec.cod_entradacana \n" +
                                 "AND   a_pcts.seq_itensentradacana (+)= iec.seq_itensentradacana \n" +
                                 "AND   oc.cod_grupoempresa  = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa       = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial        = iec.cod_filial \n" +
                                 "AND   oc.cod_safra         = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem      = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana      = oc.cod_tipocana \n" +
                                 (request.getParameter("tipcol").equals("M")? " and tc.cod_tipocana in (3,4) \n" : " ") +
                                 (request.getParameter("tipcol").equals("MAN")? " and tc.cod_tipocana not in (3,4) \n" : " ") +
                                 (request.getParameter("tipo").equals("1") ||
                                  request.getParameter("tipo").equals("3") ||
                                  request.getParameter("tipo").equals("4") ||
                                  request.getParameter("tipo").equals("5") ? " and htfz.cod_tipofazenda = " + request.getParameter("tipo") + " \n" : " ") +
                                 "AND   fz.cod_fazenda       = iec.cod_fazenda          \n" +
                                 "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
                                 "AND   htfz.cod_tipofazenda = tpfz.cod_tipofazenda \n" +
                                 "and   f.cod_fornecedor     = htfz.cod_fornecedor \n" +
                                 "and   p.cod_pessoa         = f.cod_pessoa \n" +
                                 "and   tal.cod_safra        = iec.cod_safra \n" +
                                 "and   tal.cod_fazenda      = iec.cod_fazenda \n" +
                                 "and   tal.zona             = iec.zona \n" +
                                 "and   tal.cod_talhao       = iec.cod_talhao \n" +
                                 "and   var.cod_variedade    = tal.cod_variedade \n" +
                                 "and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate)) \n" +
                                 "and   ec.datamovimento between '" + request.getParameter("datini") + "' and '" + request.getParameter("datfin") + "' " +
                                 "and   to_number(substr(ec.horasaida,1,2)) between " + request.getParameter("horini") + " and " + request.getParameter("horfin") + " " +
                                 "group by var.descricao \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "SELECT '2' tipo, 'TOTAL GERAL' variedade,  \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.BRIX_EXTRATO * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) BRIX, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
                                 "     agricola.fazenda fz, \n" +
                                 "     agricola.tipofazenda tpfz, \n" +
                                 "     agricola.historico_fazenda htfz, \n" +
                                 "     rh.pessoa p, \n" +
                                 "     material.fornecedor f, \n" +
                                 "     agricola.talhao tal, \n" +
                                 "     agricola.variedade var \n" +     
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
								 "AND tpfz.cod_tipofazenda not in (8) \n" +
                                      
                                 "AND   iec.cod_grupoempresa = ec.cod_grupoempresa \n" +
                                 "AND   iec.cod_empresa = ec.cod_empresa \n" +
                                 "AND   iec.cod_filial = ec.cod_filial \n" +
                                 "AND   iec.cod_safra = ec.cod_safra \n" +
                                 "AND   iec.cod_entradacana = ec.cod_entradacana \n" +
                                 "AND   iec.pesoliquido > 0 \n" +
                                 "AND   a_pcts.cod_grupoempresa (+) = iec.cod_grupoempresa \n" +
                                 "AND   a_pcts.cod_empresa (+)= iec.cod_empresa \n" +
                                 "AND   a_pcts.cod_filial (+)= iec.cod_filial \n" +
                                 "AND   a_pcts.cod_safra (+)= iec.cod_safra \n" +
                                 "AND   a_pcts.cod_entradacana (+)= iec.cod_entradacana \n" +
                                 "AND   a_pcts.seq_itensentradacana (+)= iec.seq_itensentradacana \n" +
                                 "AND   oc.cod_grupoempresa  = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa       = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial        = iec.cod_filial \n" +
                                 "AND   oc.cod_safra         = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem      = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana      = oc.cod_tipocana \n" +
                                 (request.getParameter("tipcol").equals("M")? " and tc.cod_tipocana in (3,4) \n" : " ") +
                                 (request.getParameter("tipcol").equals("MAN")? " and tc.cod_tipocana not in (3,4) \n" : " ") +
                                 (request.getParameter("tipo").equals("1") ||
                                  request.getParameter("tipo").equals("3") ||
                                  request.getParameter("tipo").equals("4") ||
                                  request.getParameter("tipo").equals("5") ? " and htfz.cod_tipofazenda = " + request.getParameter("tipo") + " \n" : " ") +
                                 "AND   fz.cod_fazenda       = iec.cod_fazenda          \n" +
                                 "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
                                 "AND   htfz.cod_tipofazenda = tpfz.cod_tipofazenda \n" +
                                 "and   f.cod_fornecedor     = htfz.cod_fornecedor \n" +
                                 "and   p.cod_pessoa         = f.cod_pessoa \n" +
                                 "and   tal.cod_safra        = iec.cod_safra \n" +
                                 "and   tal.cod_fazenda      = iec.cod_fazenda \n" +
                                 "and   tal.zona             = iec.zona \n" +
                                 "and   tal.cod_talhao       = iec.cod_talhao \n" +
                                 "and   var.cod_variedade    = tal.cod_variedade \n" +
                                 "and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate)) \n" +
                                 "and   ec.datamovimento between '" + request.getParameter("datini") + "' and '" + request.getParameter("datfin") + "' " +
                                 "and   to_number(substr(ec.horasaida,1,2)) between " + request.getParameter("horini") + " and " + request.getParameter("horfin") + " " +
                                 "order by 1, 2");

             out.println("<table width='100%' bgcolor='#00CCCC' style='font-family: verdana; font-size: 12px' cellpadding='1' cellspacing='1' border='0'>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td colspan=11 align='center' ><b>");
             out.println("Entrada de Cana por Variedades no Período de " + request.getParameter("datini") + " a " +
                                                                           request.getParameter("datfin") + "  -  " +
                                                           "Horário de " + request.getParameter("horini") + " a " + (Integer.parseInt(request.getParameter("horfin")) + 1) + " Hs");
             out.println("</b></td></tr>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td align='center'><b>");
             out.println("Variedade");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("% do Total");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Toneladas");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Brix");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println(" PCC ");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println(" ATR ");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println(" AR ");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Fibra");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println(" PZA ");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println(" TQ ");
             out.println("</b></td>");
             out.println("</tr>");

             i = 0;

             float nPesliqtot = 0;
             if (rset.last()){
                 nPesliqtot = rset.getFloat("pesliq");
             }
             
             rset.beforeFirst();

             while (rset.next()){
                 if (i == 0){
                     if (rset.getString("tipo").equals("1")){
                         out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                     }else{
                         out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black; font-weight:bold''>");
                     }
                     i = 1;
                 }else{
                     if (rset.getString("tipo").equals("1")){
                         out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                     }else{
                         out.println("<tr onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black; font-weight:bold''>");
                     }
                     i = 0;
                 }
                 out.println("<td align='left'>");
                 out.println(rset.getString("variedade"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(nf2.format((rset.getFloat("pesliq") / nPesliqtot) * 100));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("pesliq") == null?" ":nf3.format(rset.getFloat("pesliq"))));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("brix") == null?" ":nf2.format(rset.getFloat("brix"))));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("pcc") == null?" ":nf4.format(rset.getFloat("pcc"))));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("atr") == null?" ":nf4.format(rset.getFloat("atr"))));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("ar") == null?" ":nf2.format(rset.getFloat("ar"))));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("fibra") == null?" ":nf2.format(rset.getFloat("fibra"))));
                 out.println("</td>");                 
                 out.println("<td align='right'>");
                 out.println((rset.getString("pureza") == null?" ":nf2.format(rset.getFloat("pureza"))));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("tq") == null?" ":rset.getString("tq")));
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
