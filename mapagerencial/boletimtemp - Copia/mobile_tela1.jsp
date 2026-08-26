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

            function acionatela(sTipo, dDatini, dDatfin, nHorini, nHorfin, sTipcol){
                window.open("mobile_detalhe.jsp?tipo=" + sTipo + "&datini=" + dDatini + "&datfin=" + dDatfin + "&horini=" + nHorini + "&horfin=" + nHorfin + "&tipcol=" + sTipcol,'frmTexto');
              //window.open("mobile_variedade.jsp?datini=" + dDatini + "&datfin=" + dDatfin + "&horini=" + nHorini + "&horfin=" + nHorfin ,'frmChuva');
            }

            function acionatelachuva(dDatini, dDatfin, nHorini, nHorfin){
                window.open("mobile_chuva.jsp",'frmChuva');
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

    String sCodSafra = "71";
    
    Connection con = null;
    try{
 
        Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
        con = DriverManager.getConnection("jdbc:oracle:thin:@123.0.0.200:1521:o9i","consulta","consulta");
        Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
        ResultSet rset, rsetDatHor;
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        rsetDatHor = stmt.executeQuery("select to_char(sysdate,'dd/mm/rrrr hh24:mi') dathor from dual");
        rsetDatHor.next();
        String dathor = rsetDatHor.getString("dathor");
        rsetDatHor.close(); rsetDatHor = null;

        DecimalFormat nf0 = new DecimalFormat(" #,##0"); 
        DecimalFormat nf2 = new DecimalFormat(" #,##0.00"); 
        DecimalFormat nf3 = new DecimalFormat(" #,##0.000"); 
        DecimalFormat nf4 = new DecimalFormat(" #,##0.0000"); 
        
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        rset = stmt.executeQuery("SELECT '0' tipo, '1' tipo2, ' Hoje' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								 "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
								  "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_filial = 1 \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                      " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))  \n" +
                                                                      
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "SELECT '0' tipo, '2' tipo2, ' Ontem' periodo, to_char(trunc(sysdate)-1,'dd/mm/rrrr') datini, to_char(trunc(sysdate)-1,'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								  "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)-1  \n" +
                                      " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))  \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "SELECT '0' tipo, '3' tipo2, ' Semana' periodo, to_char(trunc(sysdate) - decode(to_char(sysdate,'d'),1,6,2,0,3,1,4,2,5,3,6,4,7,5),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                  "     agricola.tipocana tc, \n" +
								  "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento between trunc(sysdate) - decode(to_char(sysdate,'d'),1,6,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)  \n" +
                                      " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))  \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "SELECT '0' tipo, '3' tipo2, ' Sem.Ant' periodo,  \n" +
                                 "           to_char(trunc(sysdate) - decode(to_char(sysdate,'d'),1,13,2,7,3,8,4,9,5,10,6,11,7,12),'dd/mm/rrrr') datini, \n" +
                                 "           to_char(trunc(sysdate) - decode(to_char(sysdate,'d'),1,7,2,1,3,2,4,3,5,4,6,5,7,6),'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                  "     agricola.tipocana tc, \n" +
								  "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento between to_date(trunc(sysdate) - decode(to_char(sysdate,'d'),1,13,2,7,3,8,4,9,5,10,6,11,7,12),'dd/mm/rrrr') and \n" +
                                 "                               to_date(trunc(sysdate) - decode(to_char(sysdate,'d'),1,7,2,1,3,2,4,3,5,4,6,5,7,6),'dd/mm/rrrr')  \n" +
                                      " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select '0' tipo, '4' tipo2, ' Safra' periodo, to_char(min(s.data_inicio),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                   "     agricola.tipocana tc, \n" +
								  "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                      " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate)) \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select '0' tipo, '5' tipo2, ' Média/Dia' periodo, min(datini) datini, min(datfin) datfin, 0 horini, 23 horfin, \n" +
                                 "       avg(pesliq) pesliq, 0 pcc, 0 atr, 0 ar, 0 pureza, 0 fibra, '' tq \n" +
                                 "from (select ec.datamovimento, to_char(min(s.data_inicio),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, \n" +
                                 "             sum(iec.pesoliquido) pesliq \n" +
                                 "      FROM agricola.entradacana ec, \n" +
                                 "           agricola.itensentradacana iec, \n" +
                                 "           agricola.analise_pcts a_pcts, \n" +
                                 "           agricola.safra s, \n" +
                                 "           agricola.ordem_corte_unica oc, \n" +
                                   "     agricola.tipocana tc, \n" +
								  "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "      AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "      AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "      AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "      AND   ec.cod_filial = s.cod_Filial \n" +
                                 "      AND   ec.cod_safra = s.cod_Safra \n" +
                                   " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate)) \n" +
                                 "      AND   iec.cod_grupoempresa = ec.cod_grupoempresa \n" +
                                 "      AND   iec.cod_empresa = ec.cod_empresa \n" +
                                 "      AND   iec.cod_filial = ec.cod_filial \n" +
                                 "      AND   iec.cod_safra = ec.cod_safra \n" +
                                 "      AND   iec.cod_entradacana = ec.cod_entradacana \n" +
                                 "      AND   iec.pesoliquido > 0 \n" +
                                 "      AND   a_pcts.cod_grupoempresa (+) = iec.cod_grupoempresa \n" +
                                 "      AND   a_pcts.cod_empresa (+)= iec.cod_empresa \n" +
                                 "      AND   a_pcts.cod_filial (+)= iec.cod_filial \n" +
                                 "      AND   a_pcts.cod_safra (+)= iec.cod_safra \n" +
                                 "      AND   a_pcts.cod_entradacana (+)= iec.cod_entradacana \n" +
                                 "      AND   a_pcts.seq_itensentradacana (+)= iec.seq_itensentradacana \n" +
                                 "      AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "      AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "      AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "      AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "      AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "      and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "      group by ec.datamovimento) \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "SELECT to_char(hf.cod_tipofazenda) tipo, '1 - ' || tf.descricao tipo2, ' Hoje' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 23 horfin,  \n" +
                                 "       sum(iec.pesoliquido) pesliq,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0,  \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq  \n" +
                                 "FROM agricola.entradacana ec,  \n" +
                                 "     agricola.itensentradacana iec,  \n" +
                                 "     agricola.analise_pcts a_pcts,  \n" +
                                 "     agricola.safra s,  \n" +
                                 "     agricola.ordem_corte_unica oc,  \n" +
                                 "     agricola.tipocana tc, \n" +
                                 "     agricola.historico_fazenda hf, \n" +
                                 "     agricola.tipofazenda tf \n" +
                                 "WHERE s.cod_grupoempresa = 1  \n" +
                                 "AND   s.cod_empresa = 1  \n" +
                                 "AND   s.cod_filial = 1  \n" +
								 "AND hf.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa  \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa  \n" +
                                 "AND   ec.cod_filial = s.cod_Filial  \n" +
                                 "AND   ec.cod_safra = s.cod_Safra  \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)   \n" +
                                      " and   ec.datamovimento between hf.data_inicio and nvl(hf.data_fim, trunc(sysdate)) \n" +
                                 "AND   iec.cod_grupoempresa = ec.cod_grupoempresa  \n" +
                                 "AND   iec.cod_empresa = ec.cod_empresa  \n" +
                                 "AND   iec.cod_filial = ec.cod_filial  \n" +
                                 "AND   iec.cod_safra = ec.cod_safra  \n" +
                                 "AND   iec.cod_entradacana = ec.cod_entradacana  \n" +
                                 "AND   iec.pesoliquido > 0  \n" +
                                 "AND   a_pcts.cod_grupoempresa (+) = iec.cod_grupoempresa  \n" +
                                 "AND   a_pcts.cod_empresa (+)= iec.cod_empresa  \n" +
                                 "AND   a_pcts.cod_filial (+)= iec.cod_filial  \n" +
                                 "AND   a_pcts.cod_safra (+)= iec.cod_safra  \n" +
                                 "AND   a_pcts.cod_entradacana (+)= iec.cod_entradacana  \n" +
                                 "AND   a_pcts.seq_itensentradacana (+)= iec.seq_itensentradacana  \n" +
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa     \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa     \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial  \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra     \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte  \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana  \n" +
                                 "and   hf.cod_grupoempresa = iec.cod_grupoempresa \n" +
                                 "and   hf.cod_empresa      = iec.cod_empresa \n" +
                                 "and   hf.cod_filial       = iec.cod_filial \n" +
                                 "and   hf.cod_fazenda      = iec.cod_fazenda \n" +
                                 "and   ec.datamovimento between hf.data_inicio and nvl(hf.data_fim, trunc(sysdate)) \n" +
                                 "and   hf.cod_tipofazenda  = tf.cod_tipofazenda \n" +
                                 "group by hf.cod_tipofazenda, tf.descricao \n" +
                                 " \n" +
                                 "union all  \n" +
                                 "  \n" +
                                 "SELECT to_char(hf.cod_tipofazenda), '2 - ' || tf.descricao tipo2, ' Ontem' periodo, to_char(trunc(sysdate)-1,'dd/mm/rrrr') datini, to_char(trunc(sysdate)-1,'dd/mm/rrrr') datfin, 0 horini, 23 horfin,  \n" +
                                 "       sum(iec.pesoliquido) pesliq,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0,  \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq  \n" +
                                 "FROM agricola.entradacana ec,  \n" +
                                 "     agricola.itensentradacana iec,  \n" +
                                 "     agricola.analise_pcts a_pcts,  \n" +
                                 "     agricola.safra s,  \n" +
                                 "     agricola.ordem_corte_unica oc,  \n" +
                                 "     agricola.tipocana tc, \n" +
                                 "     agricola.historico_fazenda hf, \n" +
                                 "     agricola.tipofazenda tf \n" +
                                 "WHERE s.cod_grupoempresa = 1  \n" +
                                 "AND   s.cod_empresa = 1  \n" +
                                 "AND   s.cod_filial = 1  \n" +
								 "AND hf.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa  \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa  \n" +
                                 "AND   ec.cod_filial = s.cod_Filial  \n" +
                                 "AND   ec.cod_safra = s.cod_Safra  \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)-1   \n" +
                                      " and   ec.datamovimento between hf.data_inicio and nvl(hf.data_fim, trunc(sysdate)) \n" +
                                 "AND   iec.cod_grupoempresa = ec.cod_grupoempresa  \n" +
                                 "AND   iec.cod_empresa = ec.cod_empresa  \n" +
                                 "AND   iec.cod_filial = ec.cod_filial  \n" +
                                 "AND   iec.cod_safra = ec.cod_safra  \n" +
                                 "AND   iec.cod_entradacana = ec.cod_entradacana  \n" +
                                 "AND   iec.pesoliquido > 0  \n" +
                                 "AND   a_pcts.cod_grupoempresa (+) = iec.cod_grupoempresa  \n" +
                                 "AND   a_pcts.cod_empresa (+)= iec.cod_empresa  \n" +
                                 "AND   a_pcts.cod_filial (+)= iec.cod_filial  \n" +
                                 "AND   a_pcts.cod_safra (+)= iec.cod_safra  \n" +
                                 "AND   a_pcts.cod_entradacana (+)= iec.cod_entradacana  \n" +
                                 "AND   a_pcts.seq_itensentradacana (+)= iec.seq_itensentradacana  \n" +
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa     \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa     \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial  \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra     \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte  \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana  \n" +
                                 "and   hf.cod_grupoempresa = iec.cod_grupoempresa \n" +
                                 "and   hf.cod_empresa      = iec.cod_empresa \n" +
                                 "and   hf.cod_filial       = iec.cod_filial \n" +
                                 "and   hf.cod_fazenda      = iec.cod_fazenda \n" +
                                 "and   ec.datamovimento between hf.data_inicio and nvl(hf.data_fim, trunc(sysdate)) \n" +
                                 "and   hf.cod_tipofazenda  = tf.cod_tipofazenda \n" +
                                 "group by hf.cod_tipofazenda, tf.descricao \n" +
                                 "  \n" +
                                 "union all  \n" +
                                 "  \n" +
                                 "SELECT to_char(hf.cod_tipofazenda) tipo, '3 - ' || tf.descricao tipo2, ' Semana' periodo, to_char(trunc(sysdate) - decode(to_char(sysdate,'d'),1,6,2,0,3,1,4,2,5,3,6,4,7,5),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 23 horfin,  \n" +
                                 "       sum(iec.pesoliquido) pesliq,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0,  \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq  \n" +
                                 "FROM agricola.entradacana ec,  \n" +
                                 "     agricola.itensentradacana iec,  \n" +
                                 "     agricola.analise_pcts a_pcts,  \n" +
                                 "     agricola.safra s,  \n" +
                                 "     agricola.ordem_corte_unica oc,  \n" +
                                 "     agricola.tipocana tc, \n" +
                                 "     agricola.historico_fazenda hf, \n" +
                                 "     agricola.tipofazenda tf \n" +
                                 "WHERE s.cod_grupoempresa = 1  \n" +
                                 "AND   s.cod_empresa = 1  \n" +
                                 "AND   s.cod_filial = 1  \n" +
								 "AND hf.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa  \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa  \n" +
                                 "AND   ec.cod_filial = s.cod_Filial  \n" +
                                 "AND   ec.cod_safra = s.cod_Safra  \n" +
                                 "AND   ec.datamovimento between  trunc(sysdate) - decode(to_char(sysdate,'d'),1,6,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)   \n" +
                                      " and   ec.datamovimento between hf.data_inicio and nvl(hf.data_fim, trunc(sysdate))   \n" +
                                 "AND   iec.cod_grupoempresa = ec.cod_grupoempresa  \n" +
                                 "AND   iec.cod_empresa = ec.cod_empresa  \n" +
                                 "AND   iec.cod_filial = ec.cod_filial  \n" +
                                 "AND   iec.cod_safra = ec.cod_safra  \n" +
                                 "AND   iec.cod_entradacana = ec.cod_entradacana  \n" +
                                 "AND   iec.pesoliquido > 0  \n" +
                                 "AND   a_pcts.cod_grupoempresa (+) = iec.cod_grupoempresa  \n" +
                                 "AND   a_pcts.cod_empresa (+)= iec.cod_empresa  \n" +
                                 "AND   a_pcts.cod_filial (+)= iec.cod_filial  \n" +
                                 "AND   a_pcts.cod_safra (+)= iec.cod_safra  \n" +
                                 "AND   a_pcts.cod_entradacana (+)= iec.cod_entradacana  \n" +
                                 "AND   a_pcts.seq_itensentradacana (+)= iec.seq_itensentradacana  \n" +
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa     \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa     \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial  \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra     \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte  \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana  \n" +
                                 "and   hf.cod_grupoempresa = iec.cod_grupoempresa \n" +
                                 "and   hf.cod_empresa      = iec.cod_empresa \n" +
                                 "and   hf.cod_filial       = iec.cod_filial \n" +
                                 "and   hf.cod_fazenda      = iec.cod_fazenda \n" +
                                 "and   ec.datamovimento between hf.data_inicio and nvl(hf.data_fim, trunc(sysdate)) \n" +
                                 "and   hf.cod_tipofazenda  = tf.cod_tipofazenda \n" +
                                 "group by hf.cod_tipofazenda, tf.descricao \n" +
                                 "  \n" +
                                 "union all  \n" +
                                 "  \n" +
                                 "SELECT to_char(hf.cod_tipofazenda) tipo, '3 - ' || tf.descricao tipo2, ' Sem.Ant' periodo,   \n" +
                                 "           to_char(trunc(sysdate) - decode(to_char(sysdate,'d'),1,13,2,7,3,8,4,9,5,10,6,11,7,12),'dd/mm/rrrr') datini,  \n" +
                                 "           to_char(trunc(sysdate) - decode(to_char(sysdate,'d'),1,7,2,1,3,2,4,3,5,4,6,5,7,6),'dd/mm/rrrr') datfin, 0 horini, 23 horfin,  \n" +
                                 "       sum(iec.pesoliquido) pesliq,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0,  \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq  \n" +
                                 "FROM agricola.entradacana ec,  \n" +
                                 "     agricola.itensentradacana iec,  \n" +
                                 "     agricola.analise_pcts a_pcts,  \n" +
                                 "     agricola.safra s,  \n" +
                                 "     agricola.ordem_corte_unica oc,  \n" +
                                 "     agricola.tipocana tc, \n" +
                                 "     agricola.historico_fazenda hf, \n" +
                                 "     agricola.tipofazenda tf \n" +
                                 "WHERE s.cod_grupoempresa = 1  \n" +
                                 "AND   s.cod_empresa = 1  \n" +
                                 "AND   s.cod_filial = 1  \n" +
								 "AND hf.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa  \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa  \n" +
                                 "AND   ec.cod_filial = s.cod_Filial  \n" +
                                 "AND   ec.cod_safra = s.cod_Safra  \n" +
                                 "AND   ec.datamovimento between to_date(trunc(sysdate) - decode(to_char(sysdate,'d'),1,13,2,7,3,8,4,9,5,10,6,11,7,12),'dd/mm/rrrr') and  \n" +
                                 "                               to_date(trunc(sysdate) - decode(to_char(sysdate,'d'),1,7,2,1,3,2,4,3,5,4,6,5,7,6),'dd/mm/rrrr')   \n" +
                                  " and   ec.datamovimento between hf.data_inicio and nvl(hf.data_fim, trunc(sysdate))   \n" +
                                 "AND   iec.cod_grupoempresa = ec.cod_grupoempresa  \n" +
                                 "AND   iec.cod_empresa = ec.cod_empresa  \n" +
                                 "AND   iec.cod_filial = ec.cod_filial  \n" +
                                 "AND   iec.cod_safra = ec.cod_safra  \n" +
                                 "AND   iec.cod_entradacana = ec.cod_entradacana  \n" +
                                 "AND   iec.pesoliquido > 0  \n" +
                                 "AND   a_pcts.cod_grupoempresa (+) = iec.cod_grupoempresa  \n" +
                                 "AND   a_pcts.cod_empresa (+)= iec.cod_empresa  \n" +
                                 "AND   a_pcts.cod_filial (+)= iec.cod_filial  \n" +
                                 "AND   a_pcts.cod_safra (+)= iec.cod_safra  \n" +
                                 "AND   a_pcts.cod_entradacana (+)= iec.cod_entradacana  \n" +
                                 "AND   a_pcts.seq_itensentradacana (+)= iec.seq_itensentradacana  \n" +
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa     \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa     \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial  \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra     \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte  \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana  \n" +
                                 "and   hf.cod_grupoempresa = iec.cod_grupoempresa \n" +
                                 "and   hf.cod_empresa      = iec.cod_empresa \n" +
                                 "and   hf.cod_filial       = iec.cod_filial \n" +
                                 "and   hf.cod_fazenda      = iec.cod_fazenda \n" +
                                 "and   ec.datamovimento between hf.data_inicio and nvl(hf.data_fim, trunc(sysdate)) \n" +
                                 "and   hf.cod_tipofazenda  = tf.cod_tipofazenda \n" +
                                 "group by hf.cod_tipofazenda, tf.descricao \n" +
                                 "  \n" +
                                 "union all  \n" +
                                 "  \n" +
                                 "select to_char(hf.cod_tipofazenda) tipo, '4 - ' || tf.descricao tipo2, ' Safra' periodo, to_char(min(s.data_inicio),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 23 horfin,  \n" +
                                 "       sum(iec.pesoliquido) pesliq,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza,  \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0,  \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq  \n" +
                                 "FROM agricola.entradacana ec,  \n" +
                                 "     agricola.itensentradacana iec,  \n" +
                                 "     agricola.analise_pcts a_pcts,  \n" +
                                 "     agricola.safra s,  \n" +
                                 "     agricola.ordem_corte_unica oc,  \n" +
                                 "     agricola.tipocana tc, \n" +
                                 "     agricola.historico_fazenda hf, \n" +
                                 "     agricola.tipofazenda tf \n" +
                                 "WHERE s.cod_grupoempresa = 1  \n" +
                                 "AND   s.cod_empresa = 1  \n" +
                                 "AND   s.cod_filial = 1  \n" +
								 "AND hf.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa  \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa  \n" +
                                 "AND   ec.cod_filial = s.cod_Filial  \n" +
                                 "AND   ec.cod_safra = s.cod_Safra  \n" +
                                     " and   ec.datamovimento between hf.data_inicio and nvl(hf.data_fim, trunc(sysdate))   \n" +
                                 "AND   iec.cod_grupoempresa = ec.cod_grupoempresa  \n" +
                                 "AND   iec.cod_empresa = ec.cod_empresa  \n" +
                                 "AND   iec.cod_filial = ec.cod_filial  \n" +
                                 "AND   iec.cod_safra = ec.cod_safra  \n" +
                                 "AND   iec.cod_entradacana = ec.cod_entradacana  \n" +
                                 "AND   iec.pesoliquido > 0  \n" +
                                 "AND   a_pcts.cod_grupoempresa (+) = iec.cod_grupoempresa  \n" +
                                 "AND   a_pcts.cod_empresa (+)= iec.cod_empresa  \n" +
                                 "AND   a_pcts.cod_filial (+)= iec.cod_filial  \n" +
                                 "AND   a_pcts.cod_safra (+)= iec.cod_safra  \n" +
                                 "AND   a_pcts.cod_entradacana (+)= iec.cod_entradacana  \n" +
                                 "AND   a_pcts.seq_itensentradacana (+)= iec.seq_itensentradacana  \n" +
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa     \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa     \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial  \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra     \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte  \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana  \n" +
                                 "and   hf.cod_grupoempresa = iec.cod_grupoempresa \n" +
                                 "and   hf.cod_empresa      = iec.cod_empresa \n" +
                                 "and   hf.cod_filial       = iec.cod_filial \n" +
                                 "and   hf.cod_fazenda      = iec.cod_fazenda \n" +
                                 "and   ec.datamovimento between hf.data_inicio and nvl(hf.data_fim, trunc(sysdate)) \n" +
                                 "and   hf.cod_tipofazenda  = tf.cod_tipofazenda \n" +
                                 "group by hf.cod_tipofazenda, tf.descricao \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select '6' tipo, '6' tipo2, '00-06h' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 5 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                      " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "and   to_number(substr(ec.horasaida,1,2)) between 0 and 5   \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select '6' tipo, '7' tipo2, '06-12h' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 6 horini, 11 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                      " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "and   to_number(substr(ec.horasaida,1,2)) between 6 and 11   \n" +
                                 " \n" +
                                 "union all  \n" +
                                 "  \n" +
                                 "select '6' tipo, '8' tipo2, '12-18h' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 12 horini, 17 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_fornecedor in (11,867) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                     " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "and   to_number(substr(ec.horasaida,1,2)) between 12 and 17 \n" +
                                 "  \n" +
                                 "union all  \n" +
                                 "  \n" +
                                 "select '6' tipo, '9' tipo2, '18-24h' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 18 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                      
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "and   to_number(substr(ec.horasaida,1,2)) between 18 and 23 \n" +
                                 "  \n" +
                                 "union all  \n" +
                                 "  \n" +
                                 "select '9' tipo, '1' tipo2, to_char(to_number(substr(ec.horasaida,1,2)),'00')||' a '||  \n" +
                                 "                            to_char(to_number(substr(ec.horasaida,1,2)) + 1,'00') periodo,  \n" +
                                 "       to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin,  \n" +
                                 "       to_number(substr(ec.horasaida,1,2)) horini, to_number(substr(ec.horasaida,1,2)) horfin,  \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) FIBRA, \n" +                
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                 " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "group by to_number(substr(ec.horasaida,1,2))  \n" +
                                 "order by 1, 2, 3");

             out.println("<form>");
             out.println("<table width='100%' style='font-family: verdana' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

             out.println("<tr bgcolor='#009999' style='color: white; font-size: 25px'>");
             out.println("<td align='center' ><b>");
             out.println("Mapa Agrícola - Posição em " + dathor + "hs");
             out.println("</b></td></tr></table>");

             out.println("<table width='100%' border='1'><tr><td rowspan='2' width='10' align='left' valign='top'>");

             out.println("<table bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

             int i = 0;
             boolean lResumoHora = true;
             String dDatini = null;
             String dDatfin = null;
             int nHorini = 0;
             int nHorfin = 23;
             
             float[] nCanaTotal = new float[10];
             int x = 0;
             boolean lPrevious = false;

             String sTipo = "";
             while (rset.next()){
                 if (rset.getString("tipo").equals("0") ||
                     rset.getString("tipo").equals("6")){
                     x++;
                     if (x <= 10)
                         nCanaTotal[x-1] = (rset.getString("pesliq") == null? 0 :rset.getFloat("pesliq"));
                 }
                 
                 if (dDatini == null){
                     dDatini = rset.getString("datini");
                     dDatfin = rset.getString("datfin");
                     nHorini = rset.getInt("horini");
                     nHorfin = rset.getInt("horfin");
                 }
                 if (!sTipo.equals(rset.getString("tipo"))){
                     sTipo = rset.getString("tipo");
                     out.println("<tr bgcolor='#0099CC' style='color: white'>");
                     out.println("<td colspan=8 align='center' ><b>");
                     if (sTipo.equals("0")){
                        out.println("Resumo da Entrada de Cana TOTAL");
                     }
                     if (sTipo.equals("1")){
                        out.println("Resumo da Entrada de Cana PRÓPRIA");
                     }
                     if (sTipo.equals("3")){
                        out.println("Resumo da Ent.Cana TROCA DE CANA");
                     }
                     if (sTipo.equals("4")){
                        out.println("Resumo da Entrada de Cana ACIONISTA");
                     }
                     if (sTipo.equals("5")){
                        out.println("Resumo da Entrada de Cana FORNECEDOR");
                     }
                     if (rset.getString("tipo").equals("6") && lResumoHora){
                        out.println("Entrada de Cana por Hora");
                        lResumoHora = false;
                     }
                     out.println("</b></td></tr>");
                     out.println("<tr bgcolor='#0099CC' style='color: white'>");
                     out.println("<td align='center'><b>");
                     out.println("Período");
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Ton.Cana");
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
                 }
                 if (i == 0){
                     out.println("<tr onclick=\"acionatela('" + rset.getString("tipo") + "','" +
                                                                rset.getString("datini") + "','" +
                                                                rset.getString("datfin") + "', " +
                                                                rset.getString("horini") + ",  " +
                                                                rset.getString("horfin") + ",'T');\""+   
                             " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                     i = 1;
                 }else{
                     out.println("<tr onclick=\"acionatela('" + rset.getString("tipo") + "','" +
                                                                rset.getString("datini") + "','" +
                                                                rset.getString("datfin") + "', " +
                                                                rset.getString("horini") + ",  " +
                                                                rset.getString("horfin") + ",'T');\""+
                             " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                     i = 0;
                 }
                 out.println("<td align='left'>");
                 out.println((rset.getString("periodo") == null?" ":rset.getString("periodo")));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("pesliq") == null?" ":nf3.format(rset.getFloat("pesliq"))));
                 out.println("</td>");
                 if (rset.getString("tipo2").equals("5")){
                     rset.previous();
                     lPrevious = true;
                 }
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
                 if (lPrevious){
                     rset.next();
                     lPrevious = false;
                 }
             }
        //
        // Colhedora
        //
        rset = stmt.executeQuery("SELECT '1' tipo, '1' tipo2, ' Hoje' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                     " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                               //"and   tc.cod_tipocana in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 2 \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "SELECT '1' tipo, '2' tipo2, ' Ontem' periodo, to_char(trunc(sysdate)-1,'dd/mm/rrrr') datini, to_char(trunc(sysdate)-1,'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)-1  \n" +
                                      " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                               //"and   tc.cod_tipocana in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 2 \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "SELECT '1' tipo, '3' tipo2, ' Semana' periodo, to_char(trunc(sysdate) - decode(to_char(sysdate,'d'),1,6,2,0,3,1,4,2,5,3,6,4,7,5),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento between trunc(sysdate) - decode(to_char(sysdate,'d'),1,6,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)  \n" +
                                     " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                               //"and   tc.cod_tipocana in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 2 \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "SELECT '1' tipo, '3' tipo2, ' Sem.Ant' periodo,  \n" +
                                 "           to_char(trunc(sysdate) - decode(to_char(sysdate,'d'),1,13,2,7,3,8,4,9,5,10,6,11,7,12),'dd/mm/rrrr') datini, \n" +
                                 "           to_char(trunc(sysdate) - decode(to_char(sysdate,'d'),1,7,2,1,3,2,4,3,5,4,6,5,7,6),'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento between to_date(trunc(sysdate) - decode(to_char(sysdate,'d'),1,13,2,7,3,8,4,9,5,10,6,11,7,12),'dd/mm/rrrr') and \n" +
                                 "                               to_date(trunc(sysdate) - decode(to_char(sysdate,'d'),1,7,2,1,3,2,4,3,5,4,6,5,7,6),'dd/mm/rrrr')  \n" +
                                    " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                               //"and   tc.cod_tipocana in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 2 \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select '1' tipo, '4' tipo2, ' Safra' periodo, to_char(min(s.data_inicio),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                    " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                               //"and   tc.cod_tipocana in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 2 \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select '1' tipo, '5' tipo2, ' Média/Dia' periodo, min(datini) datini, min(datfin) datfin, 0 horini, 23 horfin, \n" +
                                 "       avg(pesliq) pesliq, 0 pcc, 0 atr, 0 ar, 0 pureza, 0 fibra, '' tq \n" +
                                 "from (select ec.datamovimento, to_char(min(s.data_inicio),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, \n" +
                                 "             sum(iec.pesoliquido) pesliq \n" +
                                 "      FROM agricola.entradacana ec, \n" +
                                 "           agricola.itensentradacana iec, \n" +
                                 "           agricola.analise_pcts a_pcts, \n" +
                                 "           agricola.safra s, \n" +
                                 "           agricola.ordem_corte_unica oc, \n" +
                                "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "      AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "      AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "      AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "      AND   ec.cod_filial = s.cod_Filial \n" +
                                 "      AND   ec.cod_safra = s.cod_Safra \n" +
                                  " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
                                 "      AND   iec.cod_grupoempresa = ec.cod_grupoempresa \n" +
                                 "      AND   iec.cod_empresa = ec.cod_empresa \n" +
                                 "      AND   iec.cod_filial = ec.cod_filial \n" +
                                 "      AND   iec.cod_safra = ec.cod_safra \n" +
                                 "      AND   iec.cod_entradacana = ec.cod_entradacana \n" +
                                 "      AND   iec.pesoliquido > 0 \n" +
                                 "      AND   a_pcts.cod_grupoempresa (+) = iec.cod_grupoempresa \n" +
                                 "      AND   a_pcts.cod_empresa (+)= iec.cod_empresa \n" +
                                 "      AND   a_pcts.cod_filial (+)= iec.cod_filial \n" +
                                 "      AND   a_pcts.cod_safra (+)= iec.cod_safra \n" +
                                 "      AND   a_pcts.cod_entradacana (+)= iec.cod_entradacana \n" +
                                 "      AND   a_pcts.seq_itensentradacana (+)= iec.seq_itensentradacana \n" +
                                 "      AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "      AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "      AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "      AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "      AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "      and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "      and   oc.cod_tipocorte    = 2 \n" +
                                 "      group by ec.datamovimento) \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select '1' tipo, '6' tipo2, '00-06h' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 5 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                   " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "and   to_number(substr(ec.horasaida,1,2)) between 0 and 5   \n" +
                               //"and   tc.cod_tipocana in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 2 \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select '1' tipo, '7' tipo2, '06-12h' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 6 horini, 11 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                  " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "and   to_number(substr(ec.horasaida,1,2)) between 6 and 11   \n" +
                               //"and   tc.cod_tipocana in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 2 \n" +
                                 " \n" +
                                 "union all  \n" +
                                 "  \n" +
                                 "select '1' tipo, '8' tipo2, '12-18h' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 12 horini, 17 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                    " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "and   to_number(substr(ec.horasaida,1,2)) between 12 and 17 \n" +
                               //"and   tc.cod_tipocana in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 2 \n" +
                                 "  \n" +
                                 "union all  \n" +
                                 "  \n" +
                                 "select '1' tipo, '9' tipo2, '18-24h' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 18 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								  "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                 " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "and   to_number(substr(ec.horasaida,1,2)) between 18 and 23 \n" +
                               //"and   tc.cod_tipocana in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 2 \n" +
                                 "order by 1, 2, 3");
        //
             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             
             out.println("<td colspan=8 align='center' ><b>");
             out.println("Resumo da Entrada de Cana da Colhedora");
             out.println("</b></td></tr>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td align='center'><b>");
             out.println("Período");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Ton.Cana");
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
             out.println(" %/Tot ");
             out.println("</b></td>");
             out.println("</tr>");

             i = 0;
             lResumoHora = true;
             dDatini = null;
             dDatfin = null;
             nHorini = 0;
             nHorfin = 23;

             x = 0;
             while (rset.next()){
                 x++;
                 if (dDatini == null){
                     dDatini = rset.getString("datini");
                     dDatfin = rset.getString("datfin");
                     nHorini = rset.getInt("horini");
                     nHorfin = rset.getInt("horfin");
                 }
                 if (rset.getString("tipo").equals("2") && lResumoHora){
                     lResumoHora = false;
                     out.println("<tr bgcolor='#0099CC' style='color: white'>");
                     out.println("<td colspan=8 align='center' ><b>");
                     out.println("Entrada de Cana por Hora");
                     out.println("</b></td></tr>");

                     out.println("<tr bgcolor='#0099CC' style='color: white'>");
                     out.println("<td align='center'><b>");
                     out.println("Período");
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Ton.Cana");
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
                 }
                 if (i == 0){
                     out.println("<tr onclick=\"acionatela('" + rset.getString("tipo") + "','" +
                                                                rset.getString("datini") + "','" +
                                                                rset.getString("datfin") + "', " +
                                                                rset.getString("horini") + ",  " +
                                                                rset.getString("horfin") + ",'M');\""+   
                             " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                     i = 1;
                 }else{
                     out.println("<tr onclick=\"acionatela('" + rset.getString("tipo") + "','" +
                                                                rset.getString("datini") + "','" +
                                                                rset.getString("datfin") + "', " +
                                                                rset.getString("horini") + ",  " +
                                                                rset.getString("horfin") + ",'M');\""+
                             " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                     i = 0;
                 }
                 out.println("<td align='center'>");
                 out.println((rset.getString("periodo") == null?" ":rset.getString("periodo")));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("pesliq") == null?" ":nf3.format(rset.getFloat("pesliq"))));
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
                 
                 if (nCanaTotal[x-1] > 0){
                     out.println(nf0.format(rset.getFloat("pesliq") / nCanaTotal[x-1] * 100) + "%");
                 }else{
                     out.println(" ");
                 }
                 
                 //out.println((rset.getString("tq") == null?" ":rset.getString("tq")));
                 out.println("</td>");
                 out.println("</tr>");
             }
        
        //
        // Corte Manual
        //
        rset = stmt.executeQuery("SELECT '1' tipo, '1' tipo2, ' Hoje' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                 " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                               //"and   tc.cod_tipocana not in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 1 \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "SELECT '1' tipo, '2' tipo2, ' Ontem' periodo, to_char(trunc(sysdate)-1,'dd/mm/rrrr') datini, to_char(trunc(sysdate)-1,'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)-1  \n" +
                                 " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                               //"and   tc.cod_tipocana not in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 1 \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "SELECT '1' tipo, '3' tipo2, ' Semana' periodo, to_char(trunc(sysdate) - decode(to_char(sysdate,'d'),1,6,2,0,3,1,4,2,5,3,6,4,7,5),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento between trunc(sysdate) - decode(to_char(sysdate,'d'),1,6,2,0,3,1,4,2,5,3,6,4,7,5) and trunc(sysdate)  \n" +
                                 " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                               //"and   tc.cod_tipocana not in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 1 \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "SELECT '1' tipo, '3' tipo2, ' Sem.Ant' periodo, \n" +
                                 "           to_char(trunc(sysdate) - decode(to_char(sysdate,'d'),1,13,2,7,3,8,4,9,5,10,6,11,7,12),'dd/mm/rrrr') datini, \n" +
                                 "           to_char(trunc(sysdate) - decode(to_char(sysdate,'d'),1,7,2,1,3,2,4,3,5,4,6,5,7,6),'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento between to_date(trunc(sysdate) - decode(to_char(sysdate,'d'),1,13,2,7,3,8,4,9,5,10,6,11,7,12),'dd/mm/rrrr') and \n" +
                                 "                               to_date(trunc(sysdate) - decode(to_char(sysdate,'d'),1,7,2,1,3,2,4,3,5,4,6,5,7,6),'dd/mm/rrrr')  \n" +
                                 " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                               //"and   tc.cod_tipocana not in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 1 \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select '1' tipo, '4' tipo2, ' Safra' periodo, to_char(min(s.data_inicio),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                               //"and   tc.cod_tipocana not in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 1 \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select '1' tipo, '5' tipo2, ' Média/Dia' periodo, min(datini) datini, min(datfin) datfin, 0 horini, 23 horfin, \n" +
                                 "       avg(pesliq) pesliq, 0 pcc, 0 atr, 0 ar, 0 pureza, 0 fibra, '' tq \n" +
                                 "from (select ec.datamovimento, to_char(min(s.data_inicio),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, \n" +
                                 "             sum(iec.pesoliquido) pesliq \n" +
                                 "      FROM agricola.entradacana ec, \n" +
                                 "           agricola.itensentradacana iec, \n" +
                                 "           agricola.analise_pcts a_pcts, \n" +
                                 "           agricola.safra s, \n" +
                                 "           agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "      AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "      AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "      AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "      AND   ec.cod_filial = s.cod_Filial \n" +
                                 "      AND   ec.cod_safra = s.cod_Safra \n" +
                                  " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
                                 "      AND   iec.cod_grupoempresa = ec.cod_grupoempresa \n" +
                                 "      AND   iec.cod_empresa = ec.cod_empresa \n" +
                                 "      AND   iec.cod_filial = ec.cod_filial \n" +
                                 "      AND   iec.cod_safra = ec.cod_safra \n" +
                                 "      AND   iec.cod_entradacana = ec.cod_entradacana \n" +
                                 "      AND   iec.pesoliquido > 0 \n" +
                                 "      AND   a_pcts.cod_grupoempresa (+) = iec.cod_grupoempresa \n" +
                                 "      AND   a_pcts.cod_empresa (+)= iec.cod_empresa \n" +
                                 "      AND   a_pcts.cod_filial (+)= iec.cod_filial \n" +
                                 "      AND   a_pcts.cod_safra (+)= iec.cod_safra \n" +
                                 "      AND   a_pcts.cod_entradacana (+)= iec.cod_entradacana \n" +
                                 "      AND   a_pcts.seq_itensentradacana (+)= iec.seq_itensentradacana \n" +
                                 "      AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "      AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "      AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "      AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "      AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "      and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "      and   oc.cod_tipocorte    = 1 \n" +
                                 "      group by ec.datamovimento) \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select '1' tipo, '6' tipo2, '00-06h' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 0 horini, 5 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                 " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "and   to_number(substr(ec.horasaida,1,2)) between 0 and 5   \n" +
                               //"and   tc.cod_tipocana not in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 1 \n" +
                                 " \n" +
                                 "union all \n" +
                                 " \n" +
                                 "select '1' tipo, '7' tipo2, '06-12h' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 6 horini, 11 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                 " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "and   to_number(substr(ec.horasaida,1,2)) between 6 and 11   \n" +
                               //"and   tc.cod_tipocana not in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 1 \n" +
                                 " \n" +
                                 "union all  \n" +
                                 "  \n" +
                                 "select '1' tipo, '8' tipo2, '12-18h' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 12 horini, 17 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                 " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "and   to_number(substr(ec.horasaida,1,2)) between 12 and 17 \n" +
                               //"and   tc.cod_tipocana not in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 1 \n" +
                                 "  \n" +
                                 "union all  \n" +
                                 "  \n" +
                                 "select '1' tipo, '9' tipo2, '18-24h' periodo, to_char(trunc(sysdate),'dd/mm/rrrr') datini, to_char(trunc(sysdate),'dd/mm/rrrr') datfin, 18 horini, 23 horfin, \n" +
                                 "       sum(iec.pesoliquido) pesliq, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.POL_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) PCC, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.ATR * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) ATR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.AR_CANA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) AR, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.PUREZA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) pureza, \n" +
                                 "       round(DECODE(SUM(A_PCTS.PESOLIQUIDO),0,0,SUM(A_PCTS.FIBRA * A_PCTS.PESOLIQUIDO )/SUM(A_PCTS.PESOLIQUIDO) ),4) fibra, \n" +
                                 "       trim(to_char(round(DECODE(SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0)),0,0, \n" +
                                 "                    SUM(iec.qtdehorasposqueima * decode(upper(tc.imprime_posqueima),'S',1,0) * iec.pesoliquido )/SUM(iec.pesoliquido * decode(upper(tc.imprime_posqueima),'S',1,0))),2),'900'))||'h' tq \n" +
                                 "FROM agricola.entradacana ec, \n" +
                                 "     agricola.itensentradacana iec, \n" +
                                 "     agricola.analise_pcts a_pcts, \n" +
                                 "     agricola.safra s, \n" +
                                 "     agricola.ordem_corte_unica oc, \n" +
                                 "     agricola.tipocana tc, \n" +
								   "     agricola.historico_fazenda htfz \n" +
                                 "WHERE s.cod_grupoempresa = 1 \n" +
                                 "AND   s.cod_empresa = 1 \n" +
                                 "AND   s.cod_filial = 1 \n" +
								   "AND   iec.cod_fazenda      = htfz.cod_fazenda \n" +
								   "AND htfz.cod_tipofazenda not in (8) \n" +
                                 "AND   s.cod_safra = " + sCodSafra + " \n" +
                                 "AND   ec.cod_grupoempresa = s.cod_grupoempresa \n" +
                                 "AND   ec.cod_empresa = s.cod_empresa \n" +
                                 "AND   ec.cod_filial = s.cod_Filial \n" +
                                 "AND   ec.cod_safra = s.cod_Safra \n" +
                                 "AND   ec.datamovimento = trunc(sysdate)  \n" +
                                  " and   ec.datamovimento between htfz.data_inicio and nvl(htfz.data_fim, trunc(sysdate))   \n" +
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
                                 "AND   oc.cod_grupoempresa = iec.cod_grupoempresa    \n" +
                                 "AND   oc.cod_empresa      = iec.cod_empresa    \n" +
                                 "AND   oc.cod_filial       = iec.cod_filial \n" +
                                 "AND   oc.cod_safra        = iec.cod_safra    \n" +
                                 "AND   oc.numero_ordem     = iec.numeroordemcorte \n" +
                                 "and   tc.cod_tipocana     = oc.cod_tipocana \n" +
                                 "and   to_number(substr(ec.horasaida,1,2)) between 18 and 23 \n" +
                               //"and   tc.cod_tipocana not in (3,4) \n" +
                                 "and   oc.cod_tipocorte    = 1 \n" +
                                 "order by 1, 2, 3");
        //
             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             
             out.println("<td colspan=8 align='center' ><b>");
             out.println("Resumo da Entrada de Cana Corte Manual");
             out.println("</b></td></tr>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td align='center'><b>");
             out.println("Período");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Ton.Cana");
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
             out.println(" %/Tot ");
             out.println("</b></td>");
             out.println("</tr>");

             i = 0;
             lResumoHora = true;
             dDatini = null;
             dDatfin = null;
             nHorini = 0;
             nHorfin = 23;

             x = 0;
             while (rset.next()){
                 x++;
                 if (dDatini == null){
                     dDatini = rset.getString("datini");
                     dDatfin = rset.getString("datfin");
                     nHorini = rset.getInt("horini");
                     nHorfin = rset.getInt("horfin");
                 }
                 if (rset.getString("tipo").equals("2") && lResumoHora){
                     lResumoHora = false;
                     out.println("<tr bgcolor='#0099CC' style='color: white'>");
                     out.println("<td colspan=8 align='center' ><b>");
                     out.println("Entrada de Cana por Hora");
                     out.println("</b></td></tr>");

                     out.println("<tr bgcolor='#0099CC' style='color: white'>");
                     out.println("<td align='center'><b>");
                     out.println("Período");
                     out.println("</b></td>");
                     out.println("<td align='center'><b>");
                     out.println("Ton.Cana");
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
                 }
                 if (i == 0){
                     out.println("<tr onclick=\"acionatela('" + rset.getString("tipo") + "','" +
                                                                rset.getString("datini") + "','" +
                                                                rset.getString("datfin") + "', " +
                                                                rset.getString("horini") + ",  " +
                                                                rset.getString("horfin") + ",'MAN');\""+   
                             " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                     i = 1;
                 }else{
                     out.println("<tr onclick=\"acionatela('" + rset.getString("tipo") + "','" +
                                                                rset.getString("datini") + "','" +
                                                                rset.getString("datfin") + "', " +
                                                                rset.getString("horini") + ",  " +
                                                                rset.getString("horfin") + ",'MAN');\""+
                             " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                     i = 0;
                 }
                 out.println("<td align='center'>");
                 out.println((rset.getString("periodo") == null?" ":rset.getString("periodo")));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println((rset.getString("pesliq") == null?" ":nf3.format(rset.getFloat("pesliq"))));
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
                 
                 if (nCanaTotal[x-1] > 0){
                     out.println(nf0.format(rset.getFloat("pesliq") / nCanaTotal[x-1] * 100) + "%");
                 }else{
                     out.println(" ");
                 }
                 
                 //out.println((rset.getString("tq") == null?" ":rset.getString("tq")));
                 out.println("</td>");
                 out.println("</tr>");
             }
        
             out.println("</table>");

             out.println("</td><td valign='top' background='yellow'><iframe src='#' width='100%' marginwidth='0' marginheight='0' height='1000px' name='frmTexto' id='ifrmTexto'></iframe></td></tr>");
           //out.println("<tr><td align='left' valign='top'><iframe src='mobile_chuva.jsp' width='100%' marginwidth='0' marginheight='0' height='230px' name='frmChuva' id='frmChuva'></iframe></td></tr>");
           //out.println("<tr><td align='left' valign='top' colspan='2'><iframe src='mobile_chuva.jsp' width='100%' marginwidth='0' marginheight='0' height='230px' name='ifrmChuva' id='ifrmChuva'></iframe></td></tr>");

             out.println("</table>");
             
             out.println("<script>acionatela('0','" + dDatini + "','" +
                                                      dDatfin + "', " +
                                                      nHorini + ",  " +
                                                      nHorfin + ",'T');</script>'");
             rset.close();
             stmt.close();
             con.close();
             rset = null;
             rsetDatHor = null;
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
