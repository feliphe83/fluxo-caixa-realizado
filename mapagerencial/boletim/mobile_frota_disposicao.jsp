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

    Connection con = null;
    try{
 
        String sCodSafra = "74" ;
        
        Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
        con = DriverManager.getConnection("jdbc:oracle:thin:@123.0.0.200:1521:o9i","consulta","consulta");
        Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
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
        rset = stmt.executeQuery("select distinct d.cod_tipoequipamento, e.descricaotipoequipamento, c.cod_equipamento, c.descricao, \n" +
                                 "               (SELECT COUNT(distinct bb.cod_equipamento) \n" +
                                 "                FROM AGRICOLA.ENTRADACANA AA, AGRICOLA.ITENSENTRADACANA BB \n" +
                                 "                WHERE AA.COD_GRUPOEMPRESA   = 1 \n" +
                                 "                AND   AA.COD_EMPRESA        = 1 \n" +
                                 "                AND   AA.COD_FILIAL         = 1 \n" +
                                 "                AND   AA.COD_SAFRA          = " + sCodSafra + " \n" +
                                 "                AND   AA.COD_EQUIPAMENTO    = A.COD_EQUIPAMENTO \n" +
                                 "                AND   BB.COD_GRUPOEMPRESA   = AA.COD_GRUPOEMPRESA \n" +
                                 "                AND   BB.COD_EMPRESA        = AA.COD_EMPRESA \n" +
                                 "                AND   BB.COD_FILIAL         = AA.COD_FILIAL \n" +
                                 "                AND   BB.COD_SAFRA          = AA.COD_SAFRA \n" +
                                 "                AND   BB.COD_ENTRADACANA    = AA.COD_ENTRADACANA \n" +
                                 "                AND   NVL(BB.PESOBRUTO,0)   > 0 \n" +
                                 "                AND   NVL(BB.PESOLIQUIDO,0) = 0 \n" +
                                 "                AND   AA.DATAMOVIMENTO IS NULL) DENTROFABRICA, \n" +
                                 "               (select count(*) \n" +
                                 "                from automotivo.ordemservico aa \n" +
                                 "                where aa.cod_grupoempresa = 1 \n" +
                                 "                and   aa.cod_empresa      = 1 \n" +
                                 "                and   aa.cod_filial       = 1 \n" +
                                 "                and   aa.dtencerramento is null \n" +
                                 "                and   aa.cod_equipamento  = a.cod_equipamento) QTDOFICINA, \n" +
                                 "               (select count(*) \n" +
                                 "                from automotivo.ordemservico aa \n" +
                                 "                where aa.cod_grupoempresa = 1 \n" +
                                 "                and   aa.cod_empresa      = 1 \n" +
                                 "                and   aa.cod_filial       = 1 \n" +
                                 "                and   nvl(aa.cod_planoprevencao,0) > 0 \n" +
                                 "                and   aa.dtencerramento is null \n" +
                                 "                and   aa.cod_equipamento  = a.cod_equipamento) revisao, \n" +
                                 "               (select to_char(aa.dtabertura,'dd/mm/rrrr hh24:mi') \n" +
                                 "                from automotivo.ordemservico aa \n" +
                                 "                where aa.cod_grupoempresa = 1 \n" +
                                 "                and   aa.cod_empresa      = 1 \n" +
                                 "                and   aa.cod_filial       = 1 \n" +
                                 "                and   aa.dtencerramento is null \n" +
								 "                  and    nvl(aa.cod_planoprevencao,0) > 0  \n" +
                                 "                and   aa.cod_equipamento  = a.cod_equipamento) OFICINA, \n" +
                                 "               (select max(aa.ordem_carregamento) \n" +
                                 "                from agricola.ordem_carregamento aa, agricola.ordem_corte_unica bb \n" +
                                 "                where aa.cod_grupoempresa   = 1 \n" +
                                 "                and   aa.cod_empresa        = 1 \n" +
                                 "                and   aa.cod_filial         = 1 \n" +
                                 "                and   aa.cod_safra          = " + sCodSafra + " \n" +
                                 "                and   bb.cod_grupoempresa   = aa.cod_grupoempresa \n" +
                                 "                and   bb.cod_empresa        = aa.cod_empresa \n" +
                                 "                and   bb.cod_filial         = aa.cod_filial \n" +
                                 "                and   bb.cod_safra          = aa.cod_safra \n" +
                                 "                and   bb.nr_ordem_colheita   = aa.numero_ordemcorte \n" +
                                 "                and   aa.data_exclusao is null \n" +
                                 "                and   aa.cod_equipamento  = a.cod_equipamento \n" +
                                 "                and   aa.ordem_carregamento not in (select bb.ordem_carregamento \n" +
                                 "                                                    from agricola.itensentradacana bb \n" +
                                 "                                                    where bb.cod_grupoempresa = 1 \n" +
                                 "                                                    and   bb.cod_empresa      = 1 \n" +
                                 "                                                    and   bb.cod_filial       = 1 \n" +
                                 "                                                    and   bb.cod_safra        = " + sCodSafra + " )) ORDEMCARREGAMENTO, \n" +
                                 "                                                     \n" +
                                 "            nvl((select 'Fazenda: ' || to_char(bb.cod_fazenda,'00000') || ' - ' || 'Talhão: ' || bb.cod_talhao || ' - ' || cc.descricao || ' - Distância: ' || cc.distancia || ' Km'\n" +
                                 "                from agricola.ordem_carregamento aa, agricola.ordem_corte_unica bb, agricola.fazenda cc \n" +
                                 "                where aa.cod_grupoempresa   = 1 \n" +
                                 "                and   aa.cod_empresa        = 1 \n" +
                                 "                and   aa.cod_filial         = 1 \n" +
                                 "                and   aa.cod_safra          = " + sCodSafra + " \n" +
                                 "                and   bb.cod_grupoempresa   = aa.cod_grupoempresa \n" +
                                 "                and   bb.cod_empresa        = aa.cod_empresa \n" +
                                 "                and   bb.cod_filial         = aa.cod_filial \n" +
                                 "                and   bb.cod_safra          = aa.cod_safra \n" +
                                 "                and   bb.nr_ordem_colheita  = aa.numero_ordemcorte \n" +
                                 "                and   cc.cod_fazenda        = bb.cod_fazenda \n" +
                                 "                and   aa.ordem_carregamento = (select max(aaa.ordem_carregamento) \n" +
                                 "                                               from agricola.ordem_carregamento aaa \n" +
                                 "                                               where aaa.cod_grupoempresa = 1 \n" +
                                 "                                               and   aaa.cod_empresa      = 1 \n" +
                                 "                                               and   aaa.cod_filial       = 1 \n" +
                                 "                                               and   aaa.cod_safra        = " + sCodSafra + " \n" +
                                 "                                               and   aaa.data_exclusao is null \n" +
                                 "                                               and   aaa.cod_equipamento  = a.cod_equipamento) and rownum <= 1),' ') FAZENDA \n" +
                                 " \n" +
                                 "from agricola.entradacana a,  \n" +
                                 "     automotivo.equipamento c,  \n" +
                                 "     automotivo.historico_tipoequipamento d,  \n" +
                                 "     automotivo.tipoequipamento e \n" +
                                 "where a.cod_grupoempresa = 1 \n" +
                                 "and   a.cod_empresa      = 1 \n" +
                                 "and   a.cod_filial       = 1 \n" +
                                 "and   a.cod_safra       >= " + sCodSafra + " \n" +
                                 "and   a.importado <> 'S' \n" +        
                                 "and   a.cod_equipamento  = c.cod_equipamento \n" +
                                 "and   c.cod_equipamento  = d.cod_equipamento \n" +
                                 "and   c.ativo            = 'S' \n" +
                                 "and   trunc(sysdate) between d.data_inicio and nvl(d.data_fim, trunc(sysdate)) \n" +
                                 "and   d.cod_tipoequipamento = e.cod_tipoequipamento \n" +
                                 "order by c.cod_equipamento"); 
                
         /*
                                 "union all \n" +
                
                                 "select distinct d.cod_tipoequipamento, e.descricaotipoequipamento, c.cod_equipamento, c.descricao, \n" +
                                 "               0 DENTROFABRICA, \n" +
                                 "               (select count(*) \n" +
                                 "                from automotivo.ordemservico aa \n" +
                                 "                where aa.cod_grupoempresa = 1 \n" +
                                 "                and   aa.cod_empresa      = 20 \n" +
                                 "                and   aa.cod_filial       = 1 \n" +
                                 "                and   aa.dtencerramento is null \n" +
                                 "                and   aa.cod_equipamento  = c.cod_equipamento) QTDOFICINA, \n" +
                                 "               (select to_char(aa.dtabertura,'dd/mm/rrrr hh24:mi') \n" +
                                 "                from automotivo.ordemservico aa \n" +
                                 "                where aa.cod_grupoempresa = 1 \n" +
                                 "                and   aa.cod_empresa      = 20 \n" +
                                 "                and   aa.cod_filial       = 1 \n" +
                                 "                and   aa.dtencerramento is null \n" +
                                 "                and   aa.cod_equipamento  = c.cod_equipamento) OFICINA, \n" +
                                 "               (select 'Fazenda: ' || to_char(bb.cod_fazenda,'0000') || ' - ' || 'Talhão: ' || bb.cod_talhao || ' - ' || cc.descricao \n" +
                                 "                from agricola.ordem_carregamento aa, agricola.ordem_corte_unica bb, agricola.fazenda cc \n" +
                                 "                where aa.cod_grupoempresa   = 1 \n" +
                                 "                and   aa.cod_empresa        = 20 \n" +
                                 "                and   aa.cod_filial         = 1 \n" +
                                 "                and   aa.cod_safra          = 42 \n" +
                                 "                and   bb.cod_grupoempresa   = aa.cod_grupoempresa \n" +
                                 "                and   bb.cod_empresa        = aa.cod_empresa \n" +
                                 "                and   bb.cod_filial         = aa.cod_filial \n" +
                                 "                and   bb.cod_safra          = aa.cod_safra \n" +
                                 "                and   bb.numero_ordem       = aa.numero_ordemcorte \n" +
                                 "                and   cc.cod_fazenda        = bb.cod_fazenda \n" +
                                 "                and   aa.ordem_carregamento = (select ordem_carregamento \n" +
                                 "                                               from (select bbb.ordem_carregamento \n" +
                                 "                                                     from agricola.entradacana aaa,   \n" +
                                 "                                                          agricola.itensentradacana bbb,  \n" +
                                 "                                                          agricola.itensentradacana_equip fff,  \n" +
                                 "                                                          automotivo.equipamento ccc,   \n" +
                                 "                                                          automotivo.historico_tipoequipamento ddd,   \n" +
                                 "                                                          automotivo.tipoequipamento eee,  \n" +
                                 "                                                          automotivo.histproprietarioequip ggg  \n" +
                                 "                                                     where aaa.cod_grupoempresa = 1  \n" +
                                 "                                                     and   aaa.cod_empresa      = 20  \n" +
                                 "                                                     and   aaa.cod_filial       = 1  \n" +
                                 "                                                     and   aaa.cod_safra        = 42  \n" +
                                 "                                                     and   aaa.cod_grupoempresa = bbb.cod_grupoempresa  \n" +
                                 "                                                     and   aaa.cod_empresa      = bbb.cod_empresa  \n" +
                                 "                                                     and   aaa.cod_filial       = bbb.cod_filial  \n" +
                                 "                                                     and   aaa.cod_safra        = bbb.cod_safra  \n" +
                                 "                                                     and   aaa.cod_entradacana  = bbb.cod_entradacana  \n" +
                                 "                                                     and   bbb.cod_grupoempresa = fff.cod_grupoempresa  \n" +
                                 "                                                     and   bbb.cod_empresa      = fff.cod_empresa  \n" +
                                 "                                                     and   bbb.cod_filial       = fff.cod_filial  \n" +
                                 "                                                     and   bbb.cod_safra        = fff.cod_safra  \n" +
                                 "                                                     and   bbb.cod_entradacana  = fff.cod_entradacana  \n" +
                                 "                                                     and   bbb.seq_itensentradacana =  fff.seq_itensentradacana  \n" +
                                 "                                                     and   fff.cod_funcao_equip in (3, 5)  \n" +
                                 "                                                     and   fff.cod_equipamento  = ccc.cod_equipamento  \n" +
                                 "                                                     and   ccc.cod_equipamento  = ddd.cod_equipamento  \n" +
                                 "                                                     and   ccc.cod_equipamento  = c.cod_equipamento \n" +
                                 "                                                     and   trunc(sysdate) between ddd.data_inicio and nvl(ddd.data_fim, trunc(sysdate))  \n" +
                                 "                                                     and   ddd.cod_tipoequipamento = eee.cod_tipoequipamento  \n" +
                                 "                                                     and   trunc(sysdate) between ggg.data_inicial and nvl(ggg.data_final, trunc(sysdate))  \n" +
                                 "                                                     and   ggg.cod_equipamento  = ccc.cod_equipamento   \n" +
                                 "                                                     order by aaa.datachegada desc, aaa.horachegada desc) \n" +
                                 "                                               where rownum <= 1)) FAZENDA \n" +
                                 " \n" +
                                 "from agricola.entradacana a,  \n" +
                                 "     automotivo.equipamento c,  \n" +
                                 "     automotivo.historico_tipoequipamento d,  \n" +
                                 "     automotivo.tipoequipamento e, \n" +
                                 "     agricola.itensentradacana_equip f \n" +
                                 "where a.cod_grupoempresa = 1 \n" +
                                 "and   a.cod_empresa      = 20 \n" +
                                 "and   a.cod_filial       = 1 \n" +
                                 "and   a.cod_safra        = 42 \n" +
                                 "and   a.cod_grupoempresa = f.cod_grupoempresa \n" +
                                 "and   a.cod_empresa      = f.cod_empresa \n" +
                                 "and   a.cod_filial       = f.cod_filial \n" +
                                 "and   a.cod_safra        = f.cod_safra \n" +
                                 "and   a.cod_entradacana  = f.cod_entradacana \n" +
                                 "and   f.cod_funcao_equip in (3, 5) \n" +
                                 "and   f.cod_equipamento  = c.cod_equipamento \n" +
                                 "and   c.cod_equipamento  = d.cod_equipamento \n" +
                                 "and   trunc(sysdate) between d.data_inicio and nvl(d.data_fim, trunc(sysdate)) \n" +
                                 "and   d.cod_tipoequipamento = e.cod_tipoequipamento"); 
                 */
        int nTotalEmDescarga   = 0;
        int nTotalEmManutencao = 0;
        int nTotalEmTransito   = 0;
        while (rset.next()){
            nTotalEmDescarga   += (rset.getInt("DENTROFABRICA") > 0? 1 : 0);
            nTotalEmManutencao += (rset.getInt("QTDOFICINA") > 0? 1 : 0);
            nTotalEmTransito   += (rset.getInt("DENTROFABRICA") + rset.getInt("QTDOFICINA") > 0? 0 : 1);
        }
        
        rset.beforeFirst();
        
        out.println("<form>");
        
        out.println("<table width='100%' style='font-family: verdana; font-size: 12px' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

        out.println("<tr bgcolor='#009999' style='color: white; font-size: 25px'>");
        out.println("<td align='center' ><b>");
        out.println("Disponibilidade da Frota - Posição em " + dathor + "hs");
        out.println("</b></td></tr></table>");

        out.println("<table width='100%' style='font-family: verdana; font-size: 12px' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");
        out.println("<tr bgcolor='#009999' style='color: white; font-size: 15px'>");
        out.println("<td align='center' colspan='3'><b>");
        out.println("Resumo");
        out.println("</b></td></tr>");
        
        out.println("<tr bgcolor='#009999' style='color: white; font-size: 15px'>");
        out.println("<td align='center'><b>Quantidade em processo de descarga: " + String.valueOf(nTotalEmDescarga) + "</b></td>");
        out.println("<td align='center'><b>Quantidade em trânsito: " + String.valueOf(nTotalEmTransito) + "</b></td>");
        out.println("<td align='center'><b>Quantidade em manutenção: " + String.valueOf(nTotalEmManutencao) + "</b></td>");
        
        out.println("</tr></table>");
                
        out.println("<table width='100%' border='1'><tr><td width='100%' align='left' valign='top'>");

        out.println("<table width='100%' style='font-family: verdana; font-size: 12px' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td colspan=7 align='center' ><b>");
        out.println("Caminhões");
        out.println("</b></td></tr>");
             
        out.println("<tr bgcolor='#0099CC' style='color: white'>");
        out.println("<td align='center'><b>");
        out.println("Codigo");
        out.println("</b></td>");
        out.println("<td align='center'><b>");
        out.println("Descrição");
        out.println("</b></td>");
        out.println("<td align='center'><b>");
        out.println("Onde está");
        out.println("</b></td>");
        out.println("<td align='center'><b>");
        out.println("Fazenda/Talhão");
        out.println("</b></td>");
        out.println("</tr>");

        int i = 0;
        
        while (rset.next()){
            if (i == 0){
                out.println("<tr onclick=\"acionatela('" + rset.getString("cod_tipoequipamento") + "','E');\""+   
                        " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='lavender'\" bgcolor='lavender' style='color=black'>");
                i = 1;
            }else{
                out.println("<tr onclick=\"acionatela('" + rset.getString("cod_tipoequipamento") + "','E');\""+   
                        " onmouseover=\"this.bgColor='yellow'\" onmouseout=\"this.bgColor='white'\" bgcolor='white' style='color=black'>");
                i = 0;
            }
            out.println("<td align='center'>");
            out.println(rset.getString("cod_equipamento"));
            out.println("</td>");
            out.println("<td align='left'>");
            out.println(rset.getString("descricao"));
            out.println("</td>");
            //out.println("<td align='center'>");
            if (rset.getInt("dentrofabrica") > 0){
                out.println("<td align='center' style='color:green'><b>Em descarga com " + rset.getString("dentrofabrica") + " Vol(s)</b>");
            }else{
                if (rset.getInt("qtdoficina") > 0){
                    if (rset.getInt("revisao") > 0){
                        out.println("<td align='center' style='color:red'><b>Em Revisão desde " + rset.getString("oficina") + "</b>");
                    }else{
                        out.println("<td align='center' style='color:red'><b>Em Manutenção desde " + rset.getString("oficina") + "</b>");
                    }
                }else{
                    out.println("<td align='center' style='color:orange'><b>Em trânsito</b>");
                }
            }
            out.println("</td>");
            out.println("<td align='left'>");
            out.println(rset.getString("fazenda"));
            out.println("</td>");
            out.println("</tr>");
        }

        out.println("</table>");

        out.println("</td><td valign='top' background='yellow'><iframe src='#' width='100%' marginwidth='0' marginheight='0' height='1000px' name='frmTexto' id='ifrmTexto'></iframe></td></tr>");
      //out.println("<tr><td align='left' valign='top'><iframe src='mobile_chuva.jsp' width='100%' marginwidth='0' marginheight='0' height='230px' name='frmChuva' id='frmChuva'></iframe></td></tr>");
      //out.println("<tr><td align='left' valign='top' colspan='2'><iframe src='mobile_chuva.jsp' width='100%' marginwidth='0' marginheight='0' height='230px' name='ifrmChuva' id='ifrmChuva'></iframe></td></tr>");

        out.println("</table>");
             
             
        //out.println("<script>acionatela('0','T');</script>'");
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
