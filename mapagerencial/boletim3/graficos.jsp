<%@include file="validausuario.jsp"%>
<%-- 
    Document   : index
    Created on : 17/02/2011, 15:26:14
    Author     : Nichael
--%>

<%@page import="java.text.DecimalFormat"%>
<%@page contentType="text/html" pageEncoding="windows-1252"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=windows-1252">
        <link rel="icon" type="image/png" href="../../Imagens/logo.png" />
        
        <script src='../Chart/Chart.min.js'></script>
        
        <script type='text/javascript'>
            window.onload = function(){
                var ctx = document.getElementById('GraficoLine').getContext('2d');
                var LineChart = new Chart(ctx).Line(data, options);
                
                var ctx2 = document.getElementById('GraficoLine2').getContext('2d');
                var LineChart2 = new Chart(ctx2).Line(data2, options2);
                
                var _GraficoEquivalente = document.getElementById('GraficoEquivalente').getContext('2d');
                var  GraficoEquivalente = new Chart(_GraficoEquivalente).Line(dadosEquivalente, optionsEquivalente);
                
                var _GraficoProdEquivalente = document.getElementById('GraficoProdEquivalente').getContext('2d');
                var  GraficoProdEquivalente = new Chart(_GraficoProdEquivalente).Line(dadosProdEquivalente, optionsProdEquivalente);
                
                var _GraficoAcucar = document.getElementById('GraficoAcucar').getContext('2d');
                var  GraficoAcucar = new Chart(_GraficoAcucar).Line(dadosAcucar, optionsAcucar);
                
                var _GraficoAlcool = document.getElementById('GraficoAlcool').getContext('2d');
                var  GraficoAlcool = new Chart(_GraficoAlcool).Line(dadosAlcool, optionsAlcool);
                
                var _GraficoAnidro = document.getElementById('GraficoAnidro').getContext('2d');
                var  GraficoAnidro = new Chart(_GraficoAnidro).Line(dadosAnidro, optionsAnidro);
                
                var _GraficoHidratado = document.getElementById('GraficoHidratado').getContext('2d');
                var  GraficoHidratado = new Chart(_GraficoHidratado).Line(dadosHidratado, optionsHidratado);
            }
        </script>
        
    <style type="text/css">

    *{
        font-family: calibri;        
    }

    .box {
        margin: 0px auto;
        width: 70%;
    }

    .box-chart {
        width: 100%;
        margin: 0 auto;
        padding: 1 px;
        alignment-adjust: baseline;
        vertical-align: top;
        background-color: white;
    }

    </style>  
        
    </head>
             
    <body>
        
        <form>
 
            <table width='100%' style='font-family: verdana' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>
                <!-- Cana -->
                <tr bgcolor='white'><td width="50%" ><span>Entrada de Cana</span>
                                                     <span style="background: orange; color: white">&nbspHoje&nbsp</span>
                                                     <span style="background: green; color: white">&nbspOntem&nbsp</span></td>
                    <td width="50%"><span>Entrada de Cana Safra</span>
                                    <span style="background: orange; color: white">&nbspAtual&nbsp</span>
                                    <span style="background: green; color: white">&nbspAnterior&nbsp</span></td></tr>
                <tr bgcolor='white'>
                    <td width="50%">
                        <div class='box-chart'>
                            <canvas id='GraficoLine' style='width:100%;'></canvas>
                        </div>
                        <div align="center">Horas</div>
                    </td>
                    <td>
                        <div class='box-chart'>
                            <canvas id='GraficoLine2' style='width:100%;'></canvas>
                        </div>
                        <div align="center">Meses</div>
                    </td>
                </tr>
                
                <!-- Produção Equivalente -->
                
                <tr bgcolor='white'><td width="50%" ><span>Produção de Açúcar (EQUIVALENTE) Safra</span>
                                                     <span style="background: orange; color: white">&nbspAtual&nbsp</span>
                                                     <span style="background: green; color: white">&nbspAnterior&nbsp</span></td>
                    <td width="50%"><span>Sacos Açúcar por Ton.Cana (EQUIVALENTE) Safra</span>
                                    <span style="background: orange; color: white">&nbspAtual&nbsp</span>
                                    <span style="background: green; color: white">&nbspAnterior&nbsp</span></td></tr>
                <tr bgcolor='white'>
                    <td width="50%">
                        <div class='box-chart'>
                            <canvas id='GraficoEquivalente' style='width:100%;'></canvas>
                        </div>
                        <div align="center">Meses</div>
                    </td>
                   <td>
                        <div class='box-chart'>
                            <canvas id='GraficoProdEquivalente' style='width:100%;'></canvas>
                        </div>
                        <div align="center">Meses</div>
                    </td>
                </tr>
                 
                <!-- Açúcar / Álcool -->
                
                <tr bgcolor='white'><td width="50%" ><span>Produção de Açúcar Safra</span>
                                                     <span style="background: orange; color: white">&nbspAtual&nbsp</span>
                                                     <span style="background: green; color: white">&nbspAnterior&nbsp</span></td>
                    <td width="50%"><span>Produção de Álcool Total</span>
                                    <span style="background: orange; color: white">&nbspAtual&nbsp</span>
                                    <span style="background: green; color: white">&nbspAnterior&nbsp</span></td></tr>
                <tr bgcolor='white'>
                    <td width="50%">
                        <div class='box-chart'>
                            <canvas id='GraficoAcucar' style='width:100%;'></canvas>
                        </div>
                        <div align="center">Meses</div>
                    </td>
                   <td>
                        <div class='box-chart'>
                            <canvas id='GraficoAlcool' style='width:100%;'></canvas>
                        </div>
                        <div align="center">Meses</div>
                    </td>
                </tr>
                
                <!-- Alcool -->                
                
                <tr bgcolor='white'><td width="50%" ><span>Produção de Álcool Anidro Safra</span>
                                                     <span style="background: orange; color: white">&nbspAtual&nbsp</span>
                                                     <span style="background: green; color: white">&nbspAnterior&nbsp</span></td>
                    <td width="50%"><span>Produção de Álcool Hidratado Safra</span>
                                    <span style="background: orange; color: white">&nbspAtual&nbsp</span>
                                    <span style="background: green; color: white">&nbspAnterior&nbsp</span></td></tr>
                <tr bgcolor='white'>
                    <td width="50%">
                        <div class='box-chart'>
                            <canvas id='GraficoAnidro' style='width:100%;'></canvas>
                        </div>
                        <div align="center">Meses</div>
                    </td>
                    <td>
                        <div class='box-chart'>
                            <canvas id='GraficoHidratado' style='width:100%;'></canvas>
                        </div>
                        <div align="center">Meses</div>
                    </td>
                </tr>
                
            </table>
           
    <%@ page language="java" import="java.sql.*, java.io.*"%>

    <%

    Connection con = null;
    try{

        String sCodSafra = "70";
        
        Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
        con = DriverManager.getConnection("jdbc:oracle:thin:@123.0.0.200:1521:o9i","consulta","consulta");
        Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        ResultSet rset, rsetDatHor, rsetGraficoAcucar;
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = ',. '");
        
        rsetDatHor = stmt.executeQuery("select to_char(sysdate,'dd/mm/rrrr hh24:mi') dathor from dual");
        rsetDatHor.next();
        String dathor = rsetDatHor.getString("dathor");
        rsetDatHor.close(); rsetDatHor = null;

        float[] nCanaHoje    = new float[25];
        float[] nCanaOntem   = new float[25];
        float   nTotalHoje   = 0;
        float   nTotalOntem  = 0;
        int     x            = 0;

        float[] nSafraAtu    = new float[30];
        float[] nSafraAnt    = new float[30];
        float   nTotalSafAtu = 0;
        float   nTotalSafAnt = 0;
        
        
        double[] nProEqui_SafAtu = new double[30];
        double[] nProEqui_SafAnt = new double[30];
        
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/yyyy'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = ',. '");
        rset = stmt.executeQuery("select a.hora, nvl(b.pesliq,0) hoje, nvl(c.pesliq,0) ontem \n" +
                                 "from (select trim(to_char(00 + rownum - 1,'00')) hora  \n" +
                                 "      from ALL_OBJECTS \n" +
                                 "      where rownum <= 24) a, \n" +
                                 "     (select trim(to_char(to_number(substr(ec.horasaida,1,2)),'00')) hora, sum(iec.pesoliquido) pesliq \n" +
                                 "      FROM agricola.entradacana ec,  \n" +
                                 "           agricola.itensentradacana iec,  \n" +
                                 "           agricola.safra s \n" +
                                 "      WHERE s.cod_grupoempresa   = 1  \n" +
                                 "      AND   s.cod_empresa        = 1  \n" +
                                 "      AND   s.cod_filial         = 1  \n" +
                                 "      AND   s.cod_safra          = " + sCodSafra + " \n" +
                                 "      AND   ec.cod_grupoempresa  = s.cod_grupoempresa  \n" +
                                 "      AND   ec.cod_empresa       = s.cod_empresa  \n" +
                                 "      AND   ec.cod_filial        = s.cod_Filial  \n" +
                                 "      AND   ec.cod_safra         = s.cod_Safra  \n" +
                                 "      AND   ec.datamovimento     = trunc(sysdate) \n" +
                                 "      AND   iec.cod_grupoempresa = ec.cod_grupoempresa  \n" +
                                 "      AND   iec.cod_empresa      = ec.cod_empresa  \n" +
                                 "      AND   iec.cod_filial       = ec.cod_filial  \n" +
                                 "      AND   iec.cod_safra        = ec.cod_safra  \n" +
                                 "      AND   iec.cod_entradacana  = ec.cod_entradacana  \n" +
                                 "      AND   iec.pesoliquido > 0  \n" +
                                 "      group by to_char(to_number(substr(ec.horasaida,1,2)),'00')) b, \n" +
                                 "     (select trim(to_char(to_number(substr(ec.horasaida,1,2)),'00')) hora, sum(iec.pesoliquido) pesliq \n" +
                                 "      FROM agricola.entradacana ec,  \n" +
                                 "           agricola.itensentradacana iec,  \n" +
                                 "           agricola.safra s \n" +
                                 "      WHERE s.cod_grupoempresa   = 1  \n" +
                                 "      AND   s.cod_empresa        = 1  \n" +
                                 "      AND   s.cod_filial         = 1  \n" +
                                 "      AND   s.cod_safra          = " + sCodSafra + " \n" +
                                 "      AND   ec.cod_grupoempresa  = s.cod_grupoempresa  \n" +
                                 "      AND   ec.cod_empresa       = s.cod_empresa  \n" +
                                 "      AND   ec.cod_filial        = s.cod_Filial  \n" +
                                 "      AND   ec.cod_safra         = s.cod_Safra  \n" +
                                 "      AND   ec.datamovimento     = trunc(sysdate) - 1 \n" +
                                 "      AND   iec.cod_grupoempresa = ec.cod_grupoempresa  \n" +
                                 "      AND   iec.cod_empresa      = ec.cod_empresa  \n" +
                                 "      AND   iec.cod_filial       = ec.cod_filial  \n" +
                                 "      AND   iec.cod_safra        = ec.cod_safra  \n" +
                                 "      AND   iec.cod_entradacana  = ec.cod_entradacana  \n" +
                                 "      AND   iec.pesoliquido > 0  \n" +
                                 "      group by to_char(to_number(substr(ec.horasaida,1,2)),'00')) c \n" +
                                 "where a.hora = b.hora (+) \n" +
                                 "and   a.hora = c.hora (+) \n" +
                                 "order by 1");
        x = 0;
        nTotalHoje  = 0;
        nTotalOntem = 0;
        nCanaHoje[x]  = nTotalHoje;
        nCanaOntem[x] = nTotalOntem;
        while (rset.next()){
            x++;
            nTotalHoje   += rset.getFloat("hoje");
            nTotalOntem  += rset.getFloat("ontem");
            nCanaHoje[x]  = nTotalHoje;
            nCanaOntem[x] = nTotalOntem;
        }
        
        rset = stmt.executeQuery("select to_char(ec.datamovimento,'mm') anomes, \n" +
                                 "       case when to_char(ec.datamovimento,'mm') < '09' then  \n" +
                                 "                 to_char(to_number(to_char(ec.datamovimento,'mm')) + 20) else  \n" +
                                 "                 to_char(ec.datamovimento,'mm') end ordem, \n" +
                                 "       sum(decode(s.cod_safra, 70, iec.pesoliquido, 0)) safatu, \n" +
                                 "       sum(decode(s.cod_safra, 69, iec.pesoliquido, 0)) safant \n" +
                                 "FROM agricola.entradacana ec,   \n" +
                                 "     agricola.itensentradacana iec,   \n" +
                                 "     agricola.safra s  \n" +
                                 "WHERE s.cod_grupoempresa   = 1   \n" +
                                 "AND   s.cod_empresa        = 1   \n" +
                                 "AND   s.cod_filial         = 1   \n" +
                                 "AND   s.cod_safra          in (69, 70)   \n" +
                                 "AND   ec.cod_grupoempresa  = s.cod_grupoempresa   \n" +
                                 "AND   ec.cod_empresa       = s.cod_empresa   \n" +
                                 "AND   ec.cod_filial        = s.cod_Filial   \n" +
                                 "AND   ec.cod_safra         = s.cod_Safra   \n" +
                                 "AND   iec.cod_grupoempresa = ec.cod_grupoempresa   \n" +
                                 "AND   iec.cod_empresa      = ec.cod_empresa   \n" +
                                 "AND   iec.cod_filial       = ec.cod_filial   \n" +
                                 "AND   iec.cod_safra        = ec.cod_safra   \n" +
                                 "AND   iec.cod_entradacana  = ec.cod_entradacana   \n" +
                                 "AND   iec.pesoliquido > 0   \n" +
                                 "group by to_char(ec.datamovimento,'mm') \n" +
                                 "order by 2");
        x = 0;
        nTotalSafAtu = 0;
        nTotalSafAnt = 0;
        nSafraAtu[x] = 0;
        nSafraAnt[x] = 0;
        
        while (rset.next()){
            x = x + 1;
            nTotalSafAtu = nTotalSafAtu + rset.getFloat("safatu");
            nTotalSafAnt = nTotalSafAnt + rset.getFloat("safant");
            nSafraAtu[x] = nTotalSafAtu;
            nSafraAnt[x] = nTotalSafAnt;
        }
        
        out.println("<script type='text/javascript'> ");
        out.println("                 var options = { ");
        out.println("                     scaleOverride: true, ");
        out.println("                     scaleSteps: Math.ceil((12000-0)/2000), ");
        out.println("                     scaleStepWidth: 2000, ");
        out.println("                     scaleStartValue: 0, ");
        out.println("                     responsive:true,  datasetFill: false");
        out.println("                 }; ");
        out.println(" ");
        out.println("                 var data = { ");
        out.println("                     labels: ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9','10','11','12','13','14','15','16','17','18','19','20','21','22','23','24'], ");
        out.println("                     datasets: [ ");
        out.println("                         { ");
        out.println("                             label: 'HOJE', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             fillColor: 'orange', ");
        out.println("                             strokeColor: 'orange', ");
        out.println("                             pointColor: 'orange', ");
        out.println("                             pointStrokeColor: 'orange', ");
        out.println("                             pointHighlightFill: 'orange', ");
        out.println("                             pointHighlightStroke: 'orange', ");
        out.println("                             data: [" + String.valueOf(nCanaHoje[0]) + "," +
                                                             String.valueOf(nCanaHoje[1]) + "," +
                                                             String.valueOf(nCanaHoje[2]) + "," +
                                                             String.valueOf(nCanaHoje[3]) + "," +
                                                             String.valueOf(nCanaHoje[4]) + "," +
                                                             String.valueOf(nCanaHoje[5]) + "," +
                                                             String.valueOf(nCanaHoje[6]) + "," +
                                                             String.valueOf(nCanaHoje[7]) + "," +
                                                             String.valueOf(nCanaHoje[8]) + "," +
                                                             String.valueOf(nCanaHoje[9]) + "," +
                                                             String.valueOf(nCanaHoje[10]) + "," +
                                                             String.valueOf(nCanaHoje[11]) + "," +
                                                             String.valueOf(nCanaHoje[12]) + "," +
                                                             String.valueOf(nCanaHoje[13]) + "," +
                                                             String.valueOf(nCanaHoje[14]) + "," +
                                                             String.valueOf(nCanaHoje[15]) + "," +
                                                             String.valueOf(nCanaHoje[16]) + "," +
                                                             String.valueOf(nCanaHoje[17]) + "," +
                                                             String.valueOf(nCanaHoje[18]) + "," +
                                                             String.valueOf(nCanaHoje[19]) + "," +
                                                             String.valueOf(nCanaHoje[20]) + "," +
                                                             String.valueOf(nCanaHoje[21]) + "," +
                                                             String.valueOf(nCanaHoje[22]) + "," +
                                                             String.valueOf(nCanaHoje[23]) + "," +
                                                             String.valueOf(nCanaHoje[24]) + "] ");
        out.println("                         }, ");
        out.println("                         { ");
        out.println("                             label: 'ONTEM', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             strokeColor: 'green', ");
        out.println("                             pointColor: 'green', ");
        out.println("                             pointStrokeColor: 'green', ");
        out.println("                             pointHighlightFill: 'green', ");
        out.println("                             pointHighlightStroke: 'green', ");
        out.println("                             data: [" + String.valueOf(nCanaOntem[0]) + "," +
                                                             String.valueOf(nCanaOntem[1]) + "," +
                                                             String.valueOf(nCanaOntem[2]) + "," +
                                                             String.valueOf(nCanaOntem[3]) + "," +
                                                             String.valueOf(nCanaOntem[4]) + "," +
                                                             String.valueOf(nCanaOntem[5]) + "," +
                                                             String.valueOf(nCanaOntem[6]) + "," +
                                                             String.valueOf(nCanaOntem[7]) + "," +
                                                             String.valueOf(nCanaOntem[8]) + "," +
                                                             String.valueOf(nCanaOntem[9]) + "," +
                                                             String.valueOf(nCanaOntem[10]) + "," +
                                                             String.valueOf(nCanaOntem[11]) + "," +
                                                             String.valueOf(nCanaOntem[12]) + "," +
                                                             String.valueOf(nCanaOntem[13]) + "," +
                                                             String.valueOf(nCanaOntem[14]) + "," +
                                                             String.valueOf(nCanaOntem[15]) + "," +
                                                             String.valueOf(nCanaOntem[16]) + "," +
                                                             String.valueOf(nCanaOntem[17]) + "," +
                                                             String.valueOf(nCanaOntem[18]) + "," +
                                                             String.valueOf(nCanaOntem[19]) + "," +
                                                             String.valueOf(nCanaOntem[20]) + "," +
                                                             String.valueOf(nCanaOntem[21]) + "," +
                                                             String.valueOf(nCanaOntem[22]) + "," +
                                                             String.valueOf(nCanaOntem[23]) + "," +
                                                             String.valueOf(nCanaOntem[24]) + "] ");
        out.println("                         } ");
        out.println("                     ] ");
        out.println("                 }; ");
        out.println("            </script> ");
        
        out.println("<script type='text/javascript'> ");
        out.println("                 var options2 = { ");
        out.println("                     scaleOverride: true, ");
        out.println("                     scaleSteps: Math.ceil((1400000-0)/200000), ");
        out.println("                     scaleStepWidth: 200000, ");
        out.println("                     scaleStartValue: 0, ");
        out.println("                     responsive:true,  datasetFill: false");
        out.println("                 }; ");
        out.println(" ");
        out.println("                 var data2 = { ");
        out.println("                     labels: ['Set', 'Out', 'Nov', 'Dez', 'Jan', 'Fev', 'Mar', 'Abr'], ");
        out.println("                     datasets: [ ");
        out.println("                         { ");
        out.println("                             label: 'Safra Atual', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             fillColor: 'orange', ");
        out.println("                             strokeColor: 'orange', ");
        out.println("                             pointColor: 'orange', ");
        out.println("                             pointStrokeColor: 'orange', ");
        out.println("                             pointHighlightFill: 'orange', ");
        out.println("                             pointHighlightStroke: 'orange', ");
        out.println("                             data: [" + String.valueOf(nSafraAtu[1]) + "," +
                                                             String.valueOf(nSafraAtu[2]) + "," +
                                                             String.valueOf(nSafraAtu[3]) + "," +
                                                             String.valueOf(nSafraAtu[4]) + "," +
                                                             String.valueOf(nSafraAtu[5]) + "," +
                                                             String.valueOf(nSafraAtu[6]) + "," +
                                                             String.valueOf(nSafraAtu[7]) + "," +
                                                             String.valueOf(nSafraAtu[8]) + "] ");
        out.println("                         }, ");
        out.println("                         { ");
        out.println("                             label: 'Safra Anterior', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             fillColor: 'green', ");
        out.println("                             strokeColor: 'green', ");
        out.println("                             pointColor: 'green', ");
        out.println("                             pointStrokeColor: 'green', ");
        out.println("                             pointHighlightFill: 'green', ");
        out.println("                             pointHighlightStroke: 'green', ");
        out.println("                             data: [" + String.valueOf(nSafraAnt[1]) + "," +
                                                             String.valueOf(nSafraAnt[2]) + "," +
                                                             String.valueOf(nSafraAnt[3]) + "," +
                                                             String.valueOf(nSafraAnt[4]) + "," +
                                                             String.valueOf(nSafraAnt[5]) + "," +
                                                             String.valueOf(nSafraAnt[6]) + "," +
                                                             String.valueOf(nSafraAnt[7]) + "," +
                                                             String.valueOf(nSafraAnt[8]) + "] ");
        out.println("                         } ");
        out.println("                     ] ");
        out.println("                 }; ");
        out.println("            </script> ");
        
        // Select Graficos das produções de açúcar e álcool
        rset = stmt.executeQuery("select mes, desmes, proequi_atu, proequi_ant, alcani_atu, alcani_ant, alchid_atu, alchid_ant, alcpro_atu, alcpro_ant, acupro_atu, acupro_ant \n" +
                                 "from (select case when to_number(to_char(trunc(re.datahora),'mm')) < 9 then \n" +
                                 "                       to_number(to_char(trunc(re.datahora),'mm')) + 20 else \n" +
                                 "                       to_number(to_char(trunc(re.datahora),'mm')) end mes, \n" +
                                 "             to_char(trunc(re.datahora),'month') desmes, \n" +
                                 "             sum(case when sf.cod_safra = 70 then decode(Trim(re.nome_variavel),'PROD_EQ',To_Number(nvl(trim(replace(re.valor,',','.')),0)),0) else 0 end) PROEQUI_ATU, \n" +
                                 "             sum(case when sf.cod_safra = 69 then decode(Trim(re.nome_variavel),'PROD_EQ',To_Number(nvl(trim(replace(re.valor,',','.')),0)),0) else 0 end) PROEQUI_ANT, \n" +
                                 "             sum(case when sf.cod_safra = 70 then decode(Trim(re.nome_variavel),'ETANPR',To_Number(nvl(trim(re.valor),0)),0) else 0 end) ALCANI_ATU, \n" +
                                 "             sum(case when sf.cod_safra = 69 then decode(Trim(re.nome_variavel),'ETANPR',To_Number(nvl(trim(re.valor),0)),0) else 0 end) ALCANI_ANT, \n" +
                                 "             sum(case when sf.cod_safra = 70 then decode(Trim(re.nome_variavel),'ET_PR', To_Number(nvl(trim(re.valor),0)),0) else 0 end) ALCHID_ATU, \n" +
                                 "             sum(case when sf.cod_safra = 69 then decode(Trim(re.nome_variavel),'ET_PR', To_Number(nvl(trim(re.valor),0)),0) else 0 end) ALCHID_ANT, \n" +
                                 "             sum(case when sf.cod_safra = 70 then decode(Trim(re.nome_variavel),'ETTOPR',To_Number(nvl(trim(re.valor),0)),0) else 0 end) ALCPRO_ATU, \n" +
                                 "             sum(case when sf.cod_safra = 69 then decode(Trim(re.nome_variavel),'ETTOPR',To_Number(nvl(trim(re.valor),0)),0) else 0 end) ALCPRO_ANT, \n" +
                                 "             sum(case when sf.cod_safra = 70 and re.codigo_objeto = 23 then decode(Trim(re.nome_variavel),'SAP_VH',To_Number(nvl(trim(replace(re.valor,',','.')),0)),0) else 0 end) ACUPRO_ATU, \n" +
                                 "             sum(case when sf.cod_safra = 69 and re.codigo_objeto = 23 then decode(Trim(re.nome_variavel),'SAP_VH',To_Number(nvl(trim(replace(re.valor,',','.')),0)),0) else 0 end) ACUPRO_ANT \n" +
                                 "      from laboratorio.resultado re, agricola.safra sf \n" +
                                 "      where sf.cod_grupoempresa = 1 \n" +
                                 "      and   sf.cod_empresa      = 1 \n" +
                                 "      and   sf.cod_filial       = 1 \n" +
                                 "      and   sf.cod_safra in (69, 70) \n" +
                                 "      and   re.cod_grupoempresa = sf.cod_grupoempresa \n" +
                                 "      and   re.cod_empresa      = sf.cod_empresa \n" +
                                 "      and   re.cod_filial       = sf.cod_filial \n" +
                                 "      and   re.cod_safra        = sf.cod_safra \n" +
                                 "      and   re.codigo_objeto in (23,47,90) \n" +
                                 "      and   re.valor is not null \n" +
                                 "      and   length(trim(re.valor)) > 1 \n" +
                                 "      and   trim(re.nome_variavel) in ('ETANPR','ET_PR','ETTOPR','SAP_VH','PROD_EQ') \n" +
                                 "      group by case when to_number(to_char(trunc(re.datahora),'mm')) < 9 then \n" +
                                 "                         to_number(to_char(trunc(re.datahora),'mm')) + 20 else \n" +
                                 "                         to_number(to_char(trunc(re.datahora),'mm')) end, \n" +
                                 "               to_char(trunc(re.datahora),'month')) \n" +
                                 "where mes = 9 or \n" +
                                 "      alcani_atu + alcani_ant + alchid_atu + alchid_ant + alcpro_atu + alcpro_ant + acupro_atu + acupro_ant > 0 \n" +
                                 "order by mes"); 

        // Montagem do gráfico de produção equivalente
        out.println("<script type='text/javascript'> ");
        out.println("                 var optionsEquivalente = { ");
        out.println("                     responsive:true,  datasetFill: false");
        out.println("                 }; ");
        out.println("                 var dadosEquivalente = { ");
        
        String s = "";
        while (rset.next()){
            if (s.length() > 1)
                s = s + ", ";
            s = s + "'" + rset.getString("desmes") + "'";
        }
        out.println("labels: [" + s + "], ");
        out.println("                     datasets: [ ");
        out.println("                         { ");
        out.println("                             label: 'Safra Atual', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             fillColor: 'orange', ");
        out.println("                             strokeColor: 'orange', ");
        out.println("                             pointColor: 'orange', ");
        out.println("                             pointStrokeColor: 'orange', ");
        out.println("                             pointHighlightFill: 'orange', ");
        out.println("                             pointHighlightStroke: 'orange', ");
        s = "";
        float nTotal = 0;
        rset.beforeFirst();
        x = 0;
        while (rset.next()){
            x = x + 1;
            nTotal += rset.getFloat("proequi_atu");
            nProEqui_SafAtu[x] = Math.round((nTotal / nSafraAtu[x]) * 100) / 100d;             
            if (s.length() > 1)
                s = s + ", ";
            s = s + String.valueOf(nTotal);
        }
        out.println("data: [" + s + "] ");
        out.println("                         }, ");
        out.println("                         { ");
        out.println("                             label: 'Safra Anterior', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             strokeColor: 'green', ");
        out.println("                             pointColor: 'green', ");
        out.println("                             pointStrokeColor: 'green', ");
        out.println("                             pointHighlightFill: 'green', ");
        out.println("                             pointHighlightStroke: 'green', ");
        s = "";
        nTotal = 0;
        x = 0;
        rset.beforeFirst();
        while (rset.next()){
            x = x + 1;
            nTotal += rset.getFloat("proequi_ant");
            nProEqui_SafAnt[x] = Math.round((nTotal / nSafraAnt[x]) * 100) / 100d; 
            if (s.length() > 1)
                s = s + ", ";
            s = s + String.valueOf(nTotal);
        }
        out.println("data: [" + s + "] ");
        out.println("                         } ");
        out.println("                     ] ");
        out.println("                 }; ");
        out.println("            </script> ");
                
        // Montagem do gráfico de produção (Sacos por Tonelada de Cana) Equivalente
        out.println("<script type='text/javascript'> ");
        out.println("                 var optionsProdEquivalente = { ");
        out.println("                     scaleOverride: true, ");
        out.println("                     scaleSteps: Math.ceil(5/2), ");
        out.println("                     scaleStepWidth: 1, ");
        out.println("                     scaleStartValue: 0, ");
        out.println("                     responsive:true,  datasetFill: false");
        out.println("                 }; ");
        out.println(" ");
        out.println("                 var dadosProdEquivalente = { ");
        out.println("                     labels: ['Set', 'Out', 'Nov', 'Dez', 'Jan', 'Fev', 'Mar', 'Abr'], ");
        out.println("                     datasets: [ ");
        out.println("                         { ");
        out.println("                             label: 'Safra Atual', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             fillColor: 'orange', ");
        out.println("                             strokeColor: 'orange', ");
        out.println("                             pointColor: 'orange', ");
        out.println("                             pointStrokeColor: 'orange', ");
        out.println("                             pointHighlightFill: 'orange', ");
        out.println("                             pointHighlightStroke: 'orange', ");
        out.println("                             data: [" + String.valueOf(nProEqui_SafAtu[1]) + "," +
                                                             String.valueOf(nProEqui_SafAtu[2]) + "," +
                                                             String.valueOf(nProEqui_SafAtu[3]) + "," +
                                                             String.valueOf(nProEqui_SafAtu[4]) + "," +
                                                             String.valueOf(nProEqui_SafAtu[5]) + "," +
                                                             String.valueOf(nProEqui_SafAtu[6]) + "," +
                                                             String.valueOf(nProEqui_SafAtu[7]) + "," +
                                                             String.valueOf(nProEqui_SafAtu[8]) + "] ");
        out.println("                         }, ");
        out.println("                         { ");
        out.println("                             label: 'Safra Anterior', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             fillColor: 'green', ");
        out.println("                             strokeColor: 'green', ");
        out.println("                             pointColor: 'green', ");
        out.println("                             pointStrokeColor: 'green', ");
        out.println("                             pointHighlightFill: 'green', ");
        out.println("                             pointHighlightStroke: 'green', ");
        out.println("                             data: [" + String.valueOf(nProEqui_SafAnt[1]) + "," +
                                                             String.valueOf(nProEqui_SafAnt[2]) + "," +
                                                             String.valueOf(nProEqui_SafAnt[3]) + "," +
                                                             String.valueOf(nProEqui_SafAnt[4]) + "," +
                                                             String.valueOf(nProEqui_SafAnt[5]) + "," +
                                                             String.valueOf(nProEqui_SafAnt[6]) + "," +
                                                             String.valueOf(nProEqui_SafAnt[7]) + "," +
                                                             String.valueOf(nProEqui_SafAnt[8]) + "] ");
        out.println("                         } ");
        out.println("                     ] ");
        out.println("                 }; ");
        out.println("            </script> ");
        
        
        // Montagem do gráfico de açúcar
        out.println("<script type='text/javascript'> ");
        out.println("                 var optionsAcucar = { ");
        out.println("                     responsive:true,  datasetFill: false");
        out.println("                 }; ");
        out.println("                 var dadosAcucar = { ");
        
        s = "";
        rset.beforeFirst();
        while (rset.next()){
            if (s.length() > 1)
                s = s + ", ";
            s = s + "'" + rset.getString("desmes") + "'";
        }
        out.println("labels: [" + s + "], ");
        out.println("                     datasets: [ ");
        out.println("                         { ");
        out.println("                             label: 'Safra Atual', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             fillColor: 'orange', ");
        out.println("                             strokeColor: 'orange', ");
        out.println("                             pointColor: 'orange', ");
        out.println("                             pointStrokeColor: 'orange', ");
        out.println("                             pointHighlightFill: 'orange', ");
        out.println("                             pointHighlightStroke: 'orange', ");
        s = "";
        nTotal = 0;
        rset.beforeFirst();
        while (rset.next()){
            nTotal += rset.getFloat("acupro_atu");
            if (s.length() > 1)
                s = s + ", ";
            s = s + String.valueOf(nTotal);
        }
        out.println("data: [" + s + "] ");
        out.println("                         }, ");
        out.println("                         { ");
        out.println("                             label: 'Safra Anterior', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             strokeColor: 'green', ");
        out.println("                             pointColor: 'green', ");
        out.println("                             pointStrokeColor: 'green', ");
        out.println("                             pointHighlightFill: 'green', ");
        out.println("                             pointHighlightStroke: 'green', ");
        s = "";
        nTotal = 0;
        rset.beforeFirst();
        while (rset.next()){
            nTotal += rset.getFloat("acupro_ant");
            if (s.length() > 1)
                s = s + ", ";
            s = s + String.valueOf(nTotal);
        }
        out.println("data: [" + s + "] ");
        out.println("                         } ");
        out.println("                     ] ");
        out.println("                 }; ");
        out.println("            </script> ");
        
        // Montagem do gráfico do álcool total
        out.println("<script type='text/javascript'> ");
        out.println("                 var optionsAlcool = { ");
        out.println("                     responsive:true,  datasetFill: false");
        out.println("                 }; ");
        out.println("                 var dadosAlcool = { ");
        
        s = "";
        rset.beforeFirst();
        while (rset.next()){
            if (s.length() > 1)
                s = s + ", ";
            s = s + "'" + rset.getString("desmes") + "'";
        }
        out.println("labels: [" + s + "], ");
        out.println("                     datasets: [ ");
        out.println("                         { ");
        out.println("                             label: 'Safra Atual', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             fillColor: 'orange', ");
        out.println("                             strokeColor: 'orange', ");
        out.println("                             pointColor: 'orange', ");
        out.println("                             pointStrokeColor: 'orange', ");
        out.println("                             pointHighlightFill: 'orange', ");
        out.println("                             pointHighlightStroke: 'orange', ");
        s = "";
        nTotal = 0;
        rset.beforeFirst();
        while (rset.next()){
            nTotal += rset.getFloat("alcpro_atu");
            if (s.length() > 1)
                s = s + ", ";
            s = s + String.valueOf(nTotal);
        }
        out.println("data: [" + s + "] ");
        out.println("                         }, ");
        out.println("                         { ");
        out.println("                             label: 'Safra Anterior', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             strokeColor: 'green', ");
        out.println("                             pointColor: 'green', ");
        out.println("                             pointStrokeColor: 'green', ");
        out.println("                             pointHighlightFill: 'green', ");
        out.println("                             pointHighlightStroke: 'green', ");
        s = "";
        nTotal = 0;
        rset.beforeFirst();
        while (rset.next()){
            nTotal += rset.getFloat("alcpro_ant");
            if (s.length() > 1)
                s = s + ", ";
            s = s + String.valueOf(nTotal);
        }
        out.println("data: [" + s + "] ");
        out.println("                         } ");
        out.println("                     ] ");
        out.println("                 }; ");
        out.println("            </script> ");
        
        // Montagem do gráfico de alcool anidro
        out.println("<script type='text/javascript'> ");
        out.println("                 var optionsAnidro = { ");
        out.println("                     responsive:true,  datasetFill: false");
        out.println("                 }; ");
        out.println("                 var dadosAnidro = { ");
        
        s = "";
        rset.beforeFirst();
        while (rset.next()){
            if (s.length() > 1)
                s = s + ", ";
            s = s + "'" + rset.getString("desmes") + "'";
        }
        out.println("labels: [" + s + "], ");
        out.println("                     datasets: [ ");
        out.println("                         { ");
        out.println("                             label: 'Safra Atual', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             fillColor: 'orange', ");
        out.println("                             strokeColor: 'orange', ");
        out.println("                             pointColor: 'orange', ");
        out.println("                             pointStrokeColor: 'orange', ");
        out.println("                             pointHighlightFill: 'orange', ");
        out.println("                             pointHighlightStroke: 'orange', ");
        s = "";
        nTotal = 0;
        rset.beforeFirst();
        while (rset.next()){
            nTotal += rset.getFloat("alcani_atu");
            if (s.length() > 1)
                s = s + ", ";
            s = s + String.valueOf(nTotal);
        }
        out.println("data: [" + s + "] ");
        out.println("                         }, ");
        out.println("                         { ");
        out.println("                             label: 'Safra Anterior', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             strokeColor: 'green', ");
        out.println("                             pointColor: 'green', ");
        out.println("                             pointStrokeColor: 'green', ");
        out.println("                             pointHighlightFill: 'green', ");
        out.println("                             pointHighlightStroke: 'green', ");
        s = "";
        nTotal = 0;
        rset.beforeFirst();
        while (rset.next()){
            nTotal += rset.getFloat("alcani_ant");
            if (s.length() > 1)
                s = s + ", ";
            s = s + String.valueOf(nTotal);
        }
        out.println("data: [" + s + "] ");
        out.println("                         } ");
        out.println("                     ] ");
        out.println("                 }; ");
        out.println("            </script> ");
        
        // Montagem do gráfico de alcool Hidratado
        out.println("<script type='text/javascript'> ");
        out.println("                 var optionsHidratado = { ");
        out.println("                     responsive:true,  datasetFill: false");
        out.println("                 }; ");
        out.println("                 var dadosHidratado = { ");
        
        s = "";
        rset.beforeFirst();
        while (rset.next()){
            if (s.length() > 1)
                s = s + ", ";
            s = s + "'" + rset.getString("desmes") + "'";
        }
        out.println("labels: [" + s + "], ");
        out.println("                     datasets: [ ");
        out.println("                         { ");
        out.println("                             label: 'Safra Atual', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             fillColor: 'orange', ");
        out.println("                             strokeColor: 'orange', ");
        out.println("                             pointColor: 'orange', ");
        out.println("                             pointStrokeColor: 'orange', ");
        out.println("                             pointHighlightFill: 'orange', ");
        out.println("                             pointHighlightStroke: 'orange', ");
        s = "";
        nTotal = 0;
        rset.beforeFirst();
        while (rset.next()){
            nTotal += rset.getFloat("alchid_atu");
            if (s.length() > 1)
                s = s + ", ";
            s = s + String.valueOf(nTotal);
        }
        out.println("data: [" + s + "] ");
        out.println("                         }, ");
        out.println("                         { ");
        out.println("                             label: 'Safra Anterior', ");
        out.println("                             scaleShowLabels:true, ");
        out.println("                             strokeColor: 'green', ");
        out.println("                             pointColor: 'green', ");
        out.println("                             pointStrokeColor: 'green', ");
        out.println("                             pointHighlightFill: 'green', ");
        out.println("                             pointHighlightStroke: 'green', ");
        s = "";
        nTotal = 0;
        rset.beforeFirst();
        while (rset.next()){
            nTotal += rset.getFloat("alchid_ant");
            if (s.length() > 1)
                s = s + ", ";
            s = s + String.valueOf(nTotal);
        }
        out.println("data: [" + s + "] ");
        out.println("                         } ");
        out.println("                     ] ");
        out.println("                 }; ");
        out.println("            </script> ");
        

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
