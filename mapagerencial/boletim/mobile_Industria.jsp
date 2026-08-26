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

            function acionatela(){
                window.open("mobile_Industria_detalhe.jsp",'frmTexto');
            }

            function acionatelaParadas(sMoenda){
                window.open("mobile_Industria_paradas.jsp?moenda=" + sMoenda,'frmTexto');
            }
            
        </script>

    </head>
    <body>

    <%@ page language="java" import="java.sql.*, java.io.*"%>

    <%

    Connection con = null;
    try{
 
        Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
        con = DriverManager.getConnection("jdbc:oracle:thin:@123.0.0.200:1521:o9i","consulta","consulta");
        Statement stmt = con.createStatement();
        ResultSet rset, rsetDatHor;
        rsetDatHor = stmt.executeQuery("select to_char(sysdate,'dd/mm/rrrr hh24:mi') dathor from dual");
        rsetDatHor.next();
        String dathor = rsetDatHor.getString("dathor");
        rsetDatHor.close(); rsetDatHor = null;

        DecimalFormat nf2 = new DecimalFormat("#,##0.00"); 
        DecimalFormat nf3 = new DecimalFormat("#,##0.000"); 
        DecimalFormat nf4 = new DecimalFormat("#,##0.0000"); 
        
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = ',. '");
        
        String sSql = "";
        
        sSql = "SELECT NVL(B.STATUS,'RODANDO') STATUS_A, \n" +
               "       NVL(B.MOTIVO_A,'.') MOTIVO_A, \n" +
               "       NVL(B.DATAHORA_A,'.') DATAHORA_A, \n" +
               "       NVL(B.HORAINI_A,'.') HORAINI_A, \n" +
               "       NVL(B.HORAFIM_A,'.') HORAFIM_A, \n" +
               "       NVL(B.TEMPO_A,'.') TEMPO_A, \n" +
                
               "       NVL(C.STATUS,'SAFRA NÃO INICIADA') STATUS_B, \n" +
               "       NVL(C.MOTIVO_B,'.') MOTIVO_B, \n" +
               "       NVL(C.DATAHORA_B,'.') DATAHORA_B, \n" +
               "       NVL(C.HORAINI_B,'.') HORAINI_B, \n" +
               "       NVL(C.HORAFIM_B,'.') HORAFIM_B, \n" +
               "       NVL(C.TEMPO_B,'.') TEMPO_B \n" +
               "FROM (SELECT 1 CODIGO FROM DUAL) A, \n" +
               "     (SELECT 1 CODIGO, \n" +
               "             'PARADA' STATUS, \n" +
               "             'Motivo: ' || TRIM(A.DESCRICAO) MOTIVO_A, \n" +
               "             'Parada em ' || TO_CHAR(A.DATAHORA,'DD/MM/RRRR') DATAHORA_A, \n" +
               "             'às ' || SUBSTR(A.HORAINI,1,5) || 'Hrs' HORAINI_A, \n" +
               "             SUBSTR(A.HORAFIM,1,5) HORAFIM_A, \n" +
               "             'Tempo total parada: ' || CSCLIENTE.GOT_CALCULO_HORA(HORAINI, HORAFIM) TEMPO_A \n" +
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
               "             'Motivo: ' || TRIM(A.DESCRICAO) MOTIVO_B, \n" +
               "             'Parada em ' || TO_CHAR(A.DATAHORA,'DD/MM/RRRR') DATAHORA_B, \n" +
               "             'às ' || SUBSTR(A.HORAINI,1,5) || 'Hrs' HORAINI_B, \n" +
               "             SUBSTR(A.HORAFIM,1,5) HORAFIM_B, \n" +
               "             'Tempo total parada: ' || CSCLIENTE.GOT_CALCULO_HORA(HORAINI, HORAFIM) TEMPO_B \n" +
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
               "      AND   TRUNC(A.DATAHORA) >= '01/10/2018' \n" +
               "      AND  (A.HORAINI = A.HORAFIM OR (HORAINI IS NOT NULL AND HORAFIM IS NULL)) \n" +
               "      AND   COD_CAUSA != 69 \n" +
               "      AND   ROWNUM <= 1) C \n" +
               "WHERE A.CODIGO = B.CODIGO (+) \n" +
               "AND   A.CODIGO = C.CODIGO (+)";
        
        rset = stmt.executeQuery(sSql);
        rset.next();
        
        out.println("<form>");
        out.println("<table width='100%' style='font-family: verdana' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

        out.println("<tr bgcolor='#009999' style='color: white; font-size: 25px'>");
        out.println("<td align='center' ><b>");
        out.println("Mapa Industrial - Posição em " + dathor + "hs");
        out.println("</b></td></tr></table>");

        out.println("<table width='100%' border='1'><tr><td width='30%' align='left' valign='top'>");
        
        out.println("<table width='100%' style='font-family: verdana; font-size: 12px' bgcolor='white' cellpadding='1' cellspacing='1' border='0'>");
        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td colspan=10 align='center' ><b>");
        out.println("Status da fábrica");
        out.println("</b></td></tr>");

        out.println("<tr style='color: white'>");
        out.println("<td rowspan='4' align='center' style='font-size: 20px' bgcolor=" + (rset.getString("status_a").equals("RODANDO")? "'green'" : "'red'") + "><b>");
        out.println("MOENDA");
        out.println("</b></td>");
        out.println("<td align='center' bgcolor=" + (rset.getString("status_a").equals("RODANDO")? "'green'" : "'red'") + "><b>");
        out.println(rset.getString("status_a"));
        out.println("</b></td>");
        out.println("<tr><td align='center'><b>");
        out.println(rset.getString("datahora_a") + " " + rset.getString("horaini_a"));
        out.println("</b></td></tr>");
        out.println("<tr><td align='center'><b>");
        out.println(rset.getString("tempo_a"));
        out.println("</b></td></tr>");
        out.println("<tr><td align='center'><b>");
        out.println(rset.getString("motivo_a"));
        out.println("</b></td></tr>");
        out.println("<tr><td align='center' colspan='2'><input style='width:100%' type='button' value='Listar últimas paradas' onclick=\"acionatelaParadas('A');\"></td></tr>");
        //        
        out.println("</table>");

        rset = stmt.executeQuery("select numero, codigo_objeto, nome_variavel, descricao descricao_boletim, descricao, unidade, mascara, mascarabanco, \n" +
                                 "       case when upper(trim(unidade)) = 'HORA' then valor else to_char(valor,mascarabanco) end valor \n" +
                                 "from (select a.numero, a.codigo_objeto, a.nome_variavel, a.descricao descricao_boletim, b.descricao, b.unidade, b.mascara, b.mascarabanco,  \n" +
                                 "             case when a.codigo_objeto is null then (select max(bb.valor) \n" +
                                 "                                                     from laboratorio.resultado bb  \n" +
                                 "                                                     where bb.cod_grupoempresa = a.cod_grupoempresa  \n" +
                                 "                                                     and   bb.cod_empresa      = a.cod_empresa  \n" +
                                 "                                                     and   bb.cod_filial       = a.cod_filial  \n" +
                                 "                                                     and   bb.nome_variavel    = a.nome_variavel  \n" +
                                 "                                                     and   bb.cod_safra        = 74  \n" +
                                 "                                                     and   bb.cod_turno        = 0  \n" +
                                 "                                                     and   trunc(bb.datahora)  = trunc(sysdate-1)) else  \n" +
                                 "                                                    (select max(bb.valor) \n" +
                                 "                                                     from laboratorio.resultado bb  \n" +
                                 "                                                     where bb.cod_grupoempresa = a.cod_grupoempresa  \n" +
                                 "                                                     and   bb.cod_empresa      = a.cod_empresa  \n" +
                                 "                                                     and   bb.cod_filial       = a.cod_filial  \n" +
                                 "                                                     and   bb.codigo_objeto    = a.codigo_objeto  \n" +
                                 "                                                     and   bb.nome_variavel    = a.nome_variavel  \n" +
                                 "                                                     and   bb.cod_turno        = 0  \n" +
                                 "                                                     and   bb.cod_safra        = 74  \n" +
                                 "                                                     and   trunc(bb.datahora)  = trunc(sysdate-1)) end valor \n" +
                                 "from laboratorio.linha a, laboratorio.variavel b  \n" +
                                 "where a.cod_grupoempresa = 1  \n" +
                                 "and   a.cod_empresa      = 1  \n" +
                                 "and   a.cod_filial       = 1  \n" +
                                 "and   a.cod_relatorio    = 1  \n" +
                                 "and   a.cod_grupoempresa = b.cod_grupoempresa   \n" +
                                 "and   a.cod_empresa      = b.cod_empresa   \n" +
                                 "and   a.cod_filial       = b.cod_filial   \n" +
                                 "and   a.nome_variavel    = b.nome_variavel   \n" +
                                 ") \n" +
                                 "order by numero");

        out.println("<table width='100%' style='font-family: verdana; font-size: 12px' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td colspan='2' align='center' ><b>");
        out.println("Boletim Diário de Ontem");
        out.println("</b></td></tr>");

        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td align='center'><b>");
        out.println("Item");
        out.println("</b></td>");
        out.println("<td align='center'><b>");
        out.println("Resultado");
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
            out.println("<td align='left'>");
            out.println(rset.getString("descricao_boletim"));
            out.println("</td>");
            out.println("<td align='right'>");
            out.println(rset.getString("valor"));
            out.println("</td>");
            out.println("</tr>");
        }

        out.println("</table>");
        //
        
        out.println("</td><td valign='top' background='yellow'><iframe src='graficos.jsp' width='100%' marginwidth='0' marginheight='0' height='1000px' name='frmTexto' id='ifrmTexto'></iframe></td></tr>");
      //out.println("<tr><td align='left' valign='top'><iframe src='mobile_chuva.jsp' width='100%' marginwidth='0' marginheight='0' height='230px' name='frmChuva' id='frmChuva'></iframe></td></tr>");
      //out.println("<tr><td align='left' valign='top' colspan='2'><iframe src='mobile_chuva.jsp' width='100%' marginwidth='0' marginheight='0' height='230px' name='ifrmChuva' id='ifrmChuva'></iframe></td></tr>");

        out.println("</table>");
        //out.println("<script>acionatela();</script>'");

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

    </form>
</body>
</html>
