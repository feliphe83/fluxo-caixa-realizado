
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
        <script type="text/javascript" src="../Scripts/jscharts.js"></script>
    </head>
    <body>
    <%@ page language="java" import="java.sql.*, java.io.*, java.text.DecimalFormat"%>

    <%

    Connection con = null;
    try{

        DecimalFormat nf = new DecimalFormat("#,##0.00"); 
        
        Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
        con = DriverManager.getConnection("jdbc:oracle:thin:@172.16.0.64:1521:VETORH","sifrota","edisa95");
        Statement stmt  = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        ResultSet rset, rsetDatHor;
        rsetDatHor = stmt.executeQuery("select to_char(sysdate,'dd/mm/rrrr hh24:mi') dathor from dual");
        rsetDatHor.next();
        String dathor = rsetDatHor.getString("dathor");
        rsetDatHor.close(); rsetDatHor = null;
        
        rset = stmt.executeQuery("alter session set nls_date_format='dd/mm/rrrr'");
        rset = stmt.executeQuery("alter session set NLS_NUMERIC_CHARACTERS = '. '");
        
        rset = stmt.executeQuery("select '2' ordem, 'Ton.Cana Moida' item, nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/1000,0) valdia,  " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_sem,'.',''))/1000,0) valsem,  " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_mes,'.',''))/1000,0) valmes,  " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/1000,0) valsaf,  " +
                                 "                                 (select nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/1000,0)  " +
                                 "                                  from sigind.db_acu11 " +
                                 "                                  where data >= '01/09/2011' " +
                                 "                                  and   n     = a.n " +
                                 "                                  and   varia = 'TOCAM') valsafant " +
                                 "from sigind.db_acum a where data=TRUNC(SYSDATE)-1 and varia = 'TOCAM'   " +
                                 "union   " +
                                 "select '3' o, 'Acucar produzido-scs' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.','')),0) valdia,  " +
                                 "                                        nk_fnum(TO_NUMBER(replace(valor_sem,'.','')),0) valsem,  " +
                                 "                                        nk_fnum(TO_NUMBER(replace(valor_mes,'.','')),0) valmes,  " +
                                 "                                        nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0) valsaf,  " +
                                 "                                (select nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0)  " +
                                 "                                 from sigind.db_acu11 " +
                                 "                                 where data >= '01/09/2011' " +
                                 "                                 and   n     = a.n " +
                                 "                                 and   varia = 'PACDE') valsafant " +
                                 "from sigind.db_acum a where data=TRUNC(SYSDATE)-1 and varia='PACDE'   " +
                                 "union   " +
                                 "select '4' o, 'Kgs Acu/Ton (kg)' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/1000,3) valdia,  " +
                                 "                                    nk_fnum(TO_NUMBER(replace(valor_sem,'.',''))/1000,3) valsem,  " +
                                 "                                    nk_fnum(TO_NUMBER(replace(valor_mes,'.',''))/1000,3) valmes,  " +
                                 "                                    nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/1000,3) valsaf,  " +
                                 "                            (select nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/1000,3)  " +
                                 "                             from sigind.db_acu11 " +
                                 "                             where data >= '01/09/2011' " +
                                 "                             and   n     = a.n " +
                                 "                             and   varia = 'KGATC') valsafant " +
                                 "from sigind.db_acum a where data=TRUNC(SYSDATE)-1 and varia='KGATC'   " +
                                 "union   " +
                                 "select '5' o, 'Alcool Anidro (lt)' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.','')),0) valdia,  " +
                                 "                                      nk_fnum(TO_NUMBER(replace(valor_sem,'.','')),0) valsem,  " +
                                 "                                      nk_fnum(TO_NUMBER(replace(valor_mes,'.','')),0) valmes,  " +
                                 "                                      nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0) valsaf,  " +
                                 "                              (select nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0)  " +
                                 "                               from sigind.db_acu11 " +
                                 "                               where data >= '01/09/2011' " +
                                 "                               and   n     = a.n-2 " +
                                 "                               and   varia = 'PALAN') valsafant " +
                                 "from sigind.db_acum a where a.data =TRUNC(SYSDATE)-1 and varia='PALAN'   " +
                                 "union   " +
                                 "select '6' o, 'Alcool Hidratado (lt)' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.','')),0) valdia,  " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_sem,'.','')),0) valsem,  " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_mes,'.','')),0) valmes,  " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0) valsaf,  " +
                                 "                                 (select nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0)  " +
                                 "                                  from sigind.db_acu11 " +
                                 "                                  where data >= '01/09/2011' " +
                                 "                                  and   n     = a.n " +
                                 "                                  and   varia = 'PALHI') valsafant " +
                                 "from sigind.db_acum a  " +
                                 "where data =TRUNC(SYSDATE)-1 and varia='PALHI'   " +
                                 "union   " +
                                 "select '6.5' o, 'Alcool Total (lt)' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.','')),0) valdia,  " +
                                 "                                       nk_fnum(TO_NUMBER(replace(valor_sem,'.','')),0) valsem,  " +
                                 "                                       nk_fnum(TO_NUMBER(replace(valor_mes,'.','')),0) valmes,  " +
                                 "                                       nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0) valsaf,  " +
                                 "                               (select nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0)  " +
                                 "                                from sigind.db_acu11 " +
                                 "                                where data >= '01/09/2011' " +
                                 "                                and   n     = a.n " +
                                 "                                and   varia = 'TOALP') valsafant " +
                                 "from sigind.db_acum a  " +
                                 "where data =TRUNC(SYSDATE)-1 and varia='TOALP'   " +
                                 "union   " +                                 
                                 "select '7' o, 'Lts Alc/T.Cana (lt)' p, nk_fnum( TO_NUMBER(replace(valor_dia,'.' ,''))/1000,3) valdia,  " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_sem,'.' ,''))/1000,3) valsem, " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_mes,'.' ,''))/1000,3) valmes, " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_saf,'.' ,''))/1000,3) valsaf, " +
                                 "                               (select nk_fnum( TO_NUMBER(replace(valor_saf,'.' ,''))/1000,3)  " +
                                 "                                from sigind.db_acu11 " +
                                 "                                where data >= '01/09/2011' " +
                                 "                                and   n     = a.n " +
                                 "                                and   varia = 'LATC') valsafant " +
                                 "from sigind.db_acum a where data=TRUNC(SYSDATE)-1 and varia='LATC'   " +
                                 "union   " +
                                 "select '8' o, 'PCC - RIG (kg)' p, nk_fnum(((to_number(trim(replace(valor_dia,'.','')))*10) - " +
                                 "                                 (select to_number(trim(replace(valor_dia,'.','')))  " +
                                 "                                  from sigind.db_acum " +
                                 "                                  where data >= '01/09/2011'  " +
                                 "                                  and   n     = a.n  " +
                                 "                                  and   varia = 'RIG'))/1000, 3) valdia, " +
                                 "                        nk_fnum(((to_number(trim(replace(valor_sem,'.','')))*10) - " +
                                 "                                 (select to_number(trim(replace(valor_sem,'.','')))  " +
                                 "                                  from sigind.db_acum " +
                                 "                                  where data >= '01/09/2011'  " +
                                 "                                  and   n     = a.n  " +
                                 "                                  and   varia = 'RIG'))/1000, 3) valsem, " +
                                 "                        nk_fnum(((to_number(trim(replace(valor_mes,'.','')))*10) - " +
                                 "                                 (select to_number(trim(replace(valor_mes,'.','')))  " +
                                 "                                  from sigind.db_acum " +
                                 "                                  where data >= '01/09/2011'  " +
                                 "                                  and   n     = a.n  " +
                                 "                                  and   varia = 'RIG'))/1000, 3) valmes, " +
                                 "                        nk_fnum(((to_number(trim(replace(valor_saf,'.','')))*10) - " +
                                 "                                 (select to_number(trim(replace(valor_saf,'.','')))  " +
                                 "                                  from sigind.db_acum " +
                                 "                                  where data >= '01/09/2011'  " +
                                 "                                  and   n     = a.n  " +
                                 "                                  and   varia = 'RIG'))/1000, 3) valsaf, " +
                                 "                        nk_fnum((select ((select to_number(trim(replace(valor_saf,'.','')))*10  " +
                                 "                                          from sigind.db_acu11  " +
                                 "                                          where data >= '01/09/2011'  " +
                                 "                                          and   n     = a.n  " +
                                 "                                          and   varia = 'MPPCC') -  " +
                                 "                                         (select to_number(trim(replace(valor_saf,'.','')))  " +
                                 "                                          from sigind.db_acu11  " +
                                 "                                          where data >= '01/09/2011'  " +
                                 "                                          and   n     = a.n  " +
                                 "                                          and   varia = 'RIG'))/1000 from dual), 3) valsafant   " +
                                 "from sigind.db_acum a where data=TRUNC(SYSDATE)-1 and varia='MPPCC'  " +
                                 "union   " +
                                 "select '9' o, '% Imp.Veg '||(select count(*) from sigind.db_hist where varia='IMPVE' and data=trunc(sysdate)-1)||' mostras' p,   " +
                                 "                             nvl(nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/100,2),' ') valdia,  " +
                                 "                             nvl(nk_fnum(TO_NUMBER(replace(valor_sem,'.',''))/100,2),' ') valsem,  " +
                                 "                             nvl(nk_fnum(TO_NUMBER(replace(valor_mes,'.',''))/100,2),' ') valmes,  " +
                                 "                             nvl(nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/100,2),' ') valsaf,  " +
                                 "                     (select nvl(nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/100,2),' ')  " +
                                 "                      from sigind.db_acu11 " +
                                 "                      where data >= '01/09/2011' " +
                                 "                      and   n     = a.n " +
                                 "                      and   rownum <= 1 " +
                                 "                      and   varia = 'IMPVE') valsafant " +
                                 "from sigind.db_acum a where data=TRUNC(SYSDATE)-1 and varia='IMPVE'  " +
                                 "union   " +
                                 "select '72' o, '% t p/Etanol' p, nk_fnum(to_number(a.valor_dia)/(select to_number(nvl(b.valor_dia,1)) from sigind.db_acum b  " +
                                 "where b.data=a.data and b.varia='TOCAM'),2) valdia,   " +
                                 "nk_fnum(to_number(a.valor_sem)/(select to_number(nvl(b.valor_sem,1)) from sigind.db_acum b  " +
                                 "where b.data=a.data and b.varia='TOCAM'),2) valsem, " +
                                 "nk_fnum(to_number(a.valor_mes)/(select to_number(nvl(b.valor_mes,1)) from sigind.db_acum b  " +
                                 "where b.data=a.data and b.varia='TOCAM'),2) valmes, " +
                                 "nk_fnum(to_number(a.valor_saf)/(select to_number(nvl(b.valor_saf,1)) from sigind.db_acum b  " +
                                 "where b.data=a.data and b.varia='TOCAM'),2) valsaf, " +                                 
                                 "(select nk_fnum(to_number(aa.valor_saf)/(select to_number(nvl(bb.valor_saf,1)) " +
                                 "                                        from sigind.db_acu11 bb  " +
                                 "                                        where bb.n     = aa.n " +
                                 "                                        and   bb.varia = 'TOCAM' and rownum = 1),2)   " +
                                 " from sigind.db_acu11 aa " +
                                 " where aa.data >= '01/09/2011' " +
                                 " and   aa.n     = a.n " +
                                 " and   aa.varia = 'CAMAL' " +
                                 " and   rownum   = 1) valsafant  " +                                 
                                 "from sigind.db_acum a where a.data=TRUNC(SYSDATE)-1 and varia='CAMAL'  " +
                                 "union  " +
                                 "select '73' o, 'RIG' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/1000,3) valdia,  " +
                                 "                                  nk_fnum(TO_NUMBER(replace(valor_sem,'.',''))/1000,3) valsem, " +
                                 "                                  nk_fnum(TO_NUMBER(replace(valor_mes,'.',''))/1000,3) valmes, " +
                                 "                                  nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/1000,3) valsaf, " +
//                                 "                          (select nk_fnum(round((sum(pcc*plf))/sum(plf*decode(pcc,'',0,1)),3)*10,3) " +
//                                 "                           from sifrota.ent_saf " +
//                                 "                           where dia between '01/09/2011' and  to_date(to_char(trunc(sysdate-1),'dd/mm')||'/'||trim(to_char(to_number(to_char(trunc(sysdate),'rrrr'))-1,'0000')),'dd/mm/rrrr')) valsafant " +
                                                                   
                                 "                          (select nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/1000,3)  " +
                                 "                           from sigind.db_acu11 " +
                                 "                           where data >= '01/09/2011' " +
                                 "                           and   n     = a.n " +
                                 "                           and   varia = 'RIG' and rownum = 1) valsafant " +
                                 "from sigind.db_acum a where data=TRUNC(SYSDATE)-1 and varia='RIG' " +
                                 "union  " +
                                 "select '73' o, 'PCC' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/100,3) valdia,  " +
                                 "                                  nk_fnum(TO_NUMBER(replace(valor_sem,'.',''))/100,3) valsem, " +
                                 "                                  nk_fnum(TO_NUMBER(replace(valor_mes,'.',''))/100,3) valmes, " +
                                 "                                  nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/100,3) valsaf, " +
//                                 "                          (select nk_fnum(round((sum(pcc*plf))/sum(plf*decode(pcc,'',0,1)),3)*10,3) " +
//                                 "                           from sifrota.ent_saf " +
//                                 "                           where dia between '01/09/2011' and  to_date(to_char(trunc(sysdate-1),'dd/mm')||'/'||trim(to_char(to_number(to_char(trunc(sysdate),'rrrr'))-1,'0000')),'dd/mm/rrrr')) valsafant " +
                                                                   
                                 "                          (select nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/100,3)  " +
                                 "                           from sigind.db_acu11 " +
                                 "                           where data >= '01/09/2011' " +
                                 "                           and   n     = a.n " +
                                 "                           and   varia = 'MPPCC' and rownum = 1) valsafant " +
                                 "from sigind.db_acum a where data=TRUNC(SYSDATE)-1 and varia='MPPCC' " +
                                 "union  " +
                                 "select '71' o, 'Lts Alc/T.Mel (lt)' p, nk_fnum( TO_NUMBER(replace(valor_dia,'.' ,''))/1000,3) valdia,  " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_sem,'.' ,''))/1000,3) valsem, " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_mes,'.' ,''))/1000,3) valmes, " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_saf,'.' ,''))/1000,3) valsaf, " +
                                 "                               (select nk_fnum( TO_NUMBER(replace(valor_saf,'.' ,''))/1000,3)  " +
                                 "                                from sigind.db_acu11 " +
                                 "                                where data >= '01/09/2011' " +
                                 "                                and   n     = a.n " +
                                 "                                and   varia = 'LATM' and rownum = 1) valsafant " +
                                 "from sigind.db_acum a where data=TRUNC(SYSDATE)-1 and varia='LATM' " +
                                 "union all " +
                                 "select '99' o, 'ATR Entrado' p, a.atr_dia valdia, " +
                                 "                                b.atr_sem valsem, " + 
                                 "                                c.atr_mes valmes, " +
                                 "                                d.atr_saf valsaf, " +
                                 "                                e.atr_saf_ant valsafant " +
                                 "from (select nk_fnum(round((sum(atr*plf))/sum(plf*decode(atr,'',0,1)),2),4) atr_dia " +
                                 "      from sifrota.ent_saf " +
                                 "      where planta = '0' and dia = TRUNC(SYSDATE)-1 and planta = '0') a, " +

                                 "     (select nk_fnum(round((sum(atr*plf))/sum(plf*decode(atr,'',0,1)),2),4) atr_sem " +
                                 "      from sifrota.ent_saf " +
                                 "      where planta = '0' and dia between next_day(TRUNC(SYSDATE)-1-7,1) and TRUNC(SYSDATE)-1 and planta = '0') b, " +

                                 "     (select nk_fnum(round((sum(atr*plf))/sum(plf*decode(atr,'',0,1)),2),4) atr_mes " +
                                 "      from sifrota.ent_saf " +
                                 "      where planta = '0' and dia between to_date('01/' || trim(to_char(trunc(SYSDATE)-1,'mm/rrrr')), 'dd/mm/rrrr') and TRUNC(SYSDATE)-1 and planta = '0') c, " +
                                 
                                 "     (select nk_fnum(round((sum(atr*plf))/sum(plf*decode(atr,'',0,1)),2),4) atr_saf " +
                                 "      from sifrota.ent_saf " +
                                 "      where planta = '0' and dia between to_date('01/09/2012', 'dd/mm/rrrr') and TRUNC(SYSDATE)-1 and planta = '0') d, " +
                                 
                                 "     (select nk_fnum(round((sum(atr*plf))/sum(plf*decode(atr,'',0,1)),2),4) atr_saf_ant " +
                                 "      from sifrota.ent_saf " +
                                 "      where planta = '0' and dia between to_date('01/09/2011', 'dd/mm/rrrr') and to_char(TRUNC(SYSDATE)-1,'dd/mm') || '/' ||trim(to_char(to_number(to_char(sysdate,'rrrr'))-1,'0000')) and planta = '0') e " +
                                 " order by 1 ");
        
        /*
        rset = stmt.executeQuery("select '2' ordem, 'Ton.Cana Moida' item, nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/1000,0) valdia, " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_sem,'.',''))/1000,0) valsem, " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_mes,'.',''))/1000,0) valmes,  " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/1000,0) valsaf  " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia = 'TOCAM'   " +
                                 "union   " +
                                 "select '3' o, 'Acucar produzido-scs' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.','')),0) valdia,  " +
                                 "                                        nk_fnum(TO_NUMBER(replace(valor_sem,'.','')),0) valsem,  " +
                                 "                                        nk_fnum(TO_NUMBER(replace(valor_mes,'.','')),0) valmes,  " +
                                 "                                        nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0) valsaf  " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia='PACDE'   " +
                                 "union   " +
                                 "select '4' o, 'Kgs Acu/Ton (kg)' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/1000,3) valdia,  " +
                                 "                                    nk_fnum(TO_NUMBER(replace(valor_sem,'.',''))/1000,0) valsem,  " +
                                 "                                    nk_fnum(TO_NUMBER(replace(valor_mes,'.',''))/1000,0) valmes,  " +
                                 "                                    nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/1000,3) valsaf  " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia='KGATC'   " +
                                 "union   " +
                                 "select '5' o, 'Alcool Anidro (lt)' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.','')),0) valdia,  " +
                                 "                                      nk_fnum(TO_NUMBER(replace(valor_sem,'.','')),0) valsem,  " +
                                 "                                      nk_fnum(TO_NUMBER(replace(valor_mes,'.','')),0) valmes,  " +
                                 "                                      nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0) valsaf  " +
                                 "from sigind.db_acum where data =TRUNC(SYSDATE)-1 and varia='PALAN'   " +
                                 "union   " +
                                 "select '6' o, 'Alcool Hidratado (lt)' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.','')),0) valdia,  " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_sem,'.','')),0) valsem,  " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_mes,'.','')),0) valmes,  " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0) valsaf  " +
                                 "from sigind.db_acum   " +
                                 "where data =TRUNC(SYSDATE)-1 and varia='PALHI'   " +
                                 "union   " +
                                 "select '7' o, 'Lts Alc/T.Cana (lt)' p, nk_fnum( TO_NUMBER(replace(valor_dia,'.' ,''))/1000,3) valdia,  " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_sem,'.' ,''))/1000,3) valsem, " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_mes,'.' ,''))/1000,3) valmes, " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_saf,'.' ,''))/1000,3) valsaf  " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia='LATC'   " +
                                 "union   " +
                                 "select '8' o, 'PCC - RIG (kg)' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/1000,3) valdia,  " +
                                 "                                  nk_fnum(TO_NUMBER(replace(valor_sem,'.',''))/1000,3) valsem, " +
                                 "                                  nk_fnum(TO_NUMBER(replace(valor_mes,'.',''))/1000,3) valmes, " +
                                 "                                  nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/1000,3) valsaf  " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia='RRPCC'   " +
                                 "union   " +
                                 "select '9' o, '% Imp.Veg '||(select count(*) from sigind.db_hist where varia='IMPVE' and data=trunc(sysdate)-1)||' mostras' p,   " +
                                 "nvl(nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/100,2),' ') valdia,  " +
                                 "nk_fnum(TO_NUMBER(replace(valor_sem,'.',''))/100,2) valsem,  " +
                                 "nk_fnum(TO_NUMBER(replace(valor_mes,'.',''))/100,2) valmes,  " +
                                 "nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/100,2) valsaf  " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia='IMPVE'  " +
                                 "union   " +
                                 "select '72' o, '% t p/Etanol' p, nk_fnum(to_number(a.valor_dia)/(select to_number(nvl(b.valor_dia,1)) from sigind.db_acum b  " +
                                 "where b.data=a.data and b.varia='TOCAM'),2) valdia,   " +
                                 "nk_fnum(to_number(a.valor_sem)/(select to_number(nvl(b.valor_sem,1)) from sigind.db_acum b  " +
                                 "where b.data=a.data and b.varia='TOCAM'),2) valsem, " +
                                 "nk_fnum(to_number(a.valor_mes)/(select to_number(nvl(b.valor_mes,1)) from sigind.db_acum b  " +
                                 "where b.data=a.data and b.varia='TOCAM'),2) valmes, " +
                                 "nk_fnum(to_number(a.valor_saf)/(select to_number(nvl(b.valor_saf,1)) from sigind.db_acum b  " +
                                 "where b.data=a.data and b.varia='TOCAM'),2) valsaf   " +
                                 "from sigind.db_acum a where a.data=TRUNC(SYSDATE)-1 and varia='CAMAL'  " +
                                 "union  " +
                                 "select '71' o, 'Lts Alc/T.Mel (lt)' p, nk_fnum( TO_NUMBER(replace(valor_dia,'.' ,''))/1000,3) valdia,  " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_sem,'.' ,''))/1000,3) valsem, " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_mes,'.' ,''))/1000,3) valmes, " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_saf,'.' ,''))/1000,3) valsaf  " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia='LATM' order by 1 ");

        /*
        rset = stmt.executeQuery("select '2' ordem, 'Ton.Cana Moida' item, nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/1000,0) valdia, " +
                                 "                                          nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/1000,0) valsaf " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia = 'TOCAM'  " +
                                 "union  " +
                                 "select '3' o, 'Acucar produzido-scs' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.','')),0) valdia, " +
                                 "                                        nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0) valsaf " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia='PACDE'  " +
                                 "union  " +
                                 "select '4' o, 'Kgs Acu/Ton (kg)' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/1000,3) valdia, " +
                                 "                                    nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/1000,3) valsaf " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia='KGATC'  " +
                                 "union  " +
                                 "select '5' o, 'Alcool Anidro (lt)' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.','')),0) valdia, " +
                                 "                                      nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0) valsaf " +
                                 "from sigind.db_acum where data =TRUNC(SYSDATE)-1 and varia='PALAN'  " +
                                 "union  " +
                                 "select '6' o, 'Alcool Hidratado (lt)' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.','')),0) valdia, " +
                                 "                                         nk_fnum(TO_NUMBER(replace(valor_saf,'.','')),0) valsaf " +
                                 "from sigind.db_acum  " +
                                 "where data =TRUNC(SYSDATE)-1 and varia='PALHI'  " +
                                 "union  " +
                                 "select '7' o, 'Lts Alc/T.Cana (lt)' p, nk_fnum( TO_NUMBER(replace(valor_dia,'.' ,''))/1000,3) valdia, " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_saf,'.' ,''))/1000,3) valsaf " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia='LATC'  " +
                                 "union  " +
                                 "select '8' o, 'PCC - RIG (kg)' p, nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/1000,3) valdia, " +
                                 "                                  nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/1000,3) valsaf " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia='RRPCC'  " +
                                 "union  " +
                                 "select '9' o, '% Imp.Veg '||(select count(*) from sigind.db_hist where varia='IMPVE' and data=trunc(sysdate)-1)||' mostras' p,  " +
                                 "nk_fnum(TO_NUMBER(replace(valor_dia,'.',''))/100,2) valdia, " +
                                 "nk_fnum(TO_NUMBER(replace(valor_saf,'.',''))/100,2) valsaf " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia='IMPVE' " +
                                 "union  " +
                                 "select '72' o, '% t p/Etanol' p, nk_fnum(to_number(a.valor_dia)/(select to_number(nvl(b.valor_dia,1)) from sigind.db_acum b " +
                                 "where b.data=a.data and b.varia='TOCAM'),2) valdia,  " +
                                 "nk_fnum(to_number(a.valor_saf)/(select to_number(nvl(b.valor_saf,1)) from sigind.db_acum b " +
                                 "where b.data=a.data and b.varia='TOCAM'),2) valsaf  " +
                                 "from sigind.db_acum a where a.data=TRUNC(SYSDATE)-1 and varia='CAMAL' " +
                                 "union " +
                                 "select '71' o, 'Lts Alc/T.Mel (lt)' p, nk_fnum( TO_NUMBER(replace(valor_dia,'.' ,''))/1000,3) valdia, " +
                                 "                                       nk_fnum( TO_NUMBER(replace(valor_saf,'.' ,''))/1000,3) valsaf " +
                                 "from sigind.db_acum where data=TRUNC(SYSDATE)-1 and varia='LATM' order by 1");
          */

             out.println("<form>");
             out.println("<table width='100%' style='font-family: verdana' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

             out.println("<tr bgcolor='#009999' style='color: white; font-size: 25px'>");
             out.println("<td align='center' ><b>");
             out.println("Boletim Indústrial - Posição em " + dathor + "hs");
             out.println("</b></td></tr></table>");

             out.println("<table style='font-family: verdana' bgcolor='#00CCCC' cellpadding='1' cellspacing='1' border='0'>");

             out.println("<tr bgcolor='#0099CC' style='color: white'>");
             out.println("<td align='center'><b>");
             out.println("Item");
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
             out.println("Saf.Ant");
             out.println("</b></td>");
             out.println("<td align='center'><b>");
             out.println("Var.Per%");
             out.println("</b></td>");             
             out.println("</tr>");
             
             float nSafatu;
             float nSafant;

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
                 out.println(rset.getString("item"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("valdia"));
                 out.println("</td>");

                 out.println("<td align='right'>");
                 out.println(rset.getString("valsem"));
                 out.println("</td>");

                 out.println("<td align='right'>");
                 out.println(rset.getString("valmes"));
                 out.println("</td>");

                 out.println("<td align='right'>");
                 out.println(rset.getString("valsaf"));
                 out.println("</td>");
                 out.println("<td align='right'>");
                 out.println(rset.getString("valsafant"));
                 out.println("</td>");
                 
                 try{
                    nSafatu = Float.parseFloat(rset.getString("valsaf").replace(".", "$").replace(",", ".").replace("$", ""));
                    nSafant = Float.parseFloat(rset.getString("valsafant").replace(".", "$").replace(",", ".").replace("$", ""));
                    if (nSafatu != 0 && nSafant != 0){
                        out.println("<td align='right'>");
                        out.println(nf.format(((nSafatu / nSafant) - 1) * 100) + " %");
                        //out.println(nf.format((((nSafatu / nSafant) * 100)-1) - 100) + " %");
                        out.println("</td>");
                    }else{
                        if (nSafatu > 0){
                            out.println("<td align='right'>");
                            out.println("100,00 %");
                            out.println("</td>");
                        }else{
                            if (nSafant> 0){
                                out.println("<td align='right'>");
                                out.println("-100,00 %");
                                out.println("</td>");
                            }else{
                                out.println("<td align='right'>");
                                out.println("100,00 %");
                                out.println("</td>");
                            }
                        }
                    }
                 }catch(Exception e){
                    out.println("<td align='right'>");
                    out.println(" ");
                    out.println("</td>");
                 }
                 
                 out.println("</tr>");
             }

             out.println("</table>");
             
             out.println("<hr with='100%'>");
             
             rset = stmt.executeQuery("select anomes, mesano, sum(safatu) safatu, max(safant) safant from ( \n" + 
                                      "select to_char(a.data,'rrrr/mm') anomes, to_char(a.data,'mm/rrrr') mesano, \n" + 
                                      "       TO_NUMBER(replace(sum(a.valor_dia),'.',''))/1000 safatu, \n" + 
                                      "      (select max(TO_NUMBER(replace(b.valor_mes,'.',''))/1000) safant  \n" + 
                                      "       from sigind.db_acu11 b \n" + 
                                      "       where b.data between '01/09/2011' and TRUNC(SYSDATE)-1 \n" + 
                                      "       and   b.n <= (select max(bb.n) from sigind.db_acum bb \n" + 
                                      "                     where bb.varia = 'TOCAM' \n" + 
                                      "                     and   bb.data  = trunc(sysdate) -1) \n" + 
                                      "       and   to_char(b.data,'mm') = substr(to_char(a.data,'rrrr/mm'),6,2) \n" + 
                                      "       and   b.varia = 'TOCAM') safant \n" + 
                                      "from sigind.db_acum a  \n" + 
                                      "where a.data between '01/09/2012' and TRUNC(SYSDATE)-1  \n" + 
                                      "and   a.varia = 'TOCAM' \n" + 
                                      "group by to_char(a.data,'rrrr/mm'), to_char(a.data,'mm/rrrr'), a.n \n" + 
                                      ") \n" + 
                                      "group by anomes, mesano \n" + 
                                      "order by 1");
             out.println("<div id='chartcontainer_1'></div>");
             out.println("<script type='text/javascript'>");
             out.println("var myData1 = new Array([' ', 0]");
             nSafant = 0;
             while (rset.next()){
                 nSafant = nSafant + rset.getFloat("safant");                 
                 if (rset.getFloat("safant") > 0){
                     out.println(", ['" + rset.getString("mesano") + "', " + nSafant + "]");                 
                 }
             }
             out.println(");");
             out.println("var myData2 = new Array([' ', 0]");
             nSafatu = 0;
             rset.beforeFirst();
             while (rset.next()){
                 nSafatu = nSafatu + rset.getFloat("safatu");                 
                 if (rset.getFloat("safatu") > 0){
                     out.println(", ['" + rset.getString("mesano") + "', " + nSafatu + "]");                 
                 }
             }
             out.println(");");
             out.println("var myChart = new JSChart('chartcontainer_1', 'line');");
             out.println("myChart.setDataArray(myData1, 'Safra Anterior');");
             out.println("myChart.setDataArray(myData2, 'Safra Atual');");
             out.println("myChart.setAxisNameX('Meses');");
             out.println("myChart.setAxisNameY(' ');");
             out.println("myChart.setTitle('Toneladas Moidas');");
             out.println("myChart.setLegendDetect(true);");
             out.println("myChart.setLegendFontSize(12);");
             out.println("myChart.setLegendColor('#000000');");
             out.println("myChart.setLegendShow(true);");                         
             //out.println("myChart.setBackgroundColor('#99CCFF');");
             out.println("myChart.setAxisNameColor('#000000');");
             out.println("myChart.setAxisNameColorX('#000000');");
             out.println("myChart.setAxisNameColorY('#000000');");
             out.println("myChart.setAxisColor('#000000');");
             out.println("myChart.setAxisValuesColor('#000000');");
             out.println("myChart.setAxisValuesFontSize(15);");             
             out.println("myChart.setSize(700, 300);");
             //out.println("myChart.setLegend('#000000','Teste')");
             out.println("myChart.setShowYValues(false);");
             
             out.println("myChart.setGridColor('#000000');");
             out.println("myChart.setTitleColor('#000000');");
             out.println("myChart.setTitleFontSize(20);");
             out.println("myChart.setLineColor('#FF6600', 'Safra Anterior');");
             out.println("myChart.setLineColor('#000099', 'Safra Atual');");
             out.println("myChart.setLineWidth(5, 'Safra Anterior');");
             out.println("myChart.setLineWidth(5, 'Safra Atual');");
             out.println("myChart.draw();");
             out.println("</script>");
             out.println("<hr with='100%'>");
             //
             // Açúcar
             //
             rset = stmt.executeQuery("select anomes, mesano, sum(safatu) safatu, max(safant) safant from ( \n" + 
                                      "select to_char(a.data,'rrrr/mm') anomes, to_char(a.data,'mm/rrrr') mesano, \n" + 
                                      "       TO_NUMBER(replace(sum(a.valor_dia),'.',''))/1000 safatu, \n" + 
                                      "      (select max(TO_NUMBER(replace(b.valor_mes,'.',''))/1000) safant  \n" + 
                                      "       from sigind.db_acu11 b \n" + 
                                      "       where b.data between '01/09/2011' and TRUNC(SYSDATE)-1 \n" + 
                                      "       and   b.n <= (select max(bb.n) from sigind.db_acum bb \n" + 
                                      "                     where bb.varia = 'PACDE' \n" + 
                                      "                     and   bb.data  = trunc(sysdate) -1) \n" + 
                                      "       and   to_char(b.data,'mm') = substr(to_char(a.data,'rrrr/mm'),6,2) \n" + 
                                      "       and   b.varia = 'PACDE') safant \n" + 
                                      "from sigind.db_acum a  \n" + 
                                      "where a.data between '01/09/2012' and TRUNC(SYSDATE)-1  \n" + 
                                      "and   a.varia = 'PACDE' \n" + 
                                      "group by to_char(a.data,'rrrr/mm'), to_char(a.data,'mm/rrrr'), a.n \n" + 
                                      ") \n" + 
                                      "group by anomes, mesano \n" + 
                                      "order by 1");
             
             
             out.println("<div id='chartcontainer_2'> </div>");
             out.println("<script type='text/javascript'>");
             out.println("var myData1 = new Array([' ', 0]");
             nSafant = 0;
             while (rset.next()){
                 nSafant = nSafant + rset.getFloat("safant");                 
                 if (rset.getFloat("safant") > 0){
                     out.println(", ['" + rset.getString("mesano") + "', " + nSafant + "]");                 
                 }
             }
             out.println(");");
             out.println("var myData2 = new Array([' ', 0]");
             nSafatu = 0;
             rset.beforeFirst();
             while (rset.next()){
                 nSafatu = nSafatu + rset.getFloat("safatu");                 
                 if (rset.getFloat("safatu") > 0){
                     out.println(", ['" + rset.getString("mesano") + "', " + nSafatu + "]");                 
                 }
             }
             out.println(");");
             out.println("var myChart = new JSChart('chartcontainer_2', 'line');");
             out.println("myChart.setDataArray(myData1, 'Safra Anterior');");
             out.println("myChart.setDataArray(myData2, 'Safra Atual');");
             out.println("myChart.setAxisNameX('Meses');");
             out.println("myChart.setAxisNameY(' ');");
             out.println("myChart.setTitle('Açúcar Produzido');");
             //out.println("myChart.setBackgroundColor('#99CCFF');");
             out.println("myChart.setAxisNameColor('#000000');");
             out.println("myChart.setAxisNameColorX('#000000');");
             out.println("myChart.setAxisNameColorY('#000000');");
             out.println("myChart.setAxisColor('#000000');");
             out.println("myChart.setAxisValuesColor('#000000');");
             out.println("myChart.setAxisValuesFontSize(15);");             
             out.println("myChart.setSize(700, 300);");
             out.println("myChart.setShowYValues(false);");
             //
             out.println("myChart.setLegendDetect(true);");
             out.println("myChart.setLegendFontSize(12);");
             out.println("myChart.setLegendColor('#000000');");
             out.println("myChart.setLegendShow(true);");                         
             //             
             out.println("myChart.setGridColor('#000000');");
             out.println("myChart.setTitleColor('#000000');");
             out.println("myChart.setTitleFontSize(20);");
             out.println("myChart.setLineColor('#FF6600', 'Safra Anterior');");
             out.println("myChart.setLineColor('#000099', 'Safra Atual');");
             out.println("myChart.setLineWidth(5, 'Safra Anterior');");
             out.println("myChart.setLineWidth(5, 'Safra Atual');");
             out.println("myChart.draw();");
             out.println("</script>");
             out.println("<hr with='100%'>");
             //
             // Álcool
             //
             rset = stmt.executeQuery("select anomes, mesano, sum(safatu) safatu, max(safant) safant from ( \n" + 
                                      "select to_char(a.data,'rrrr/mm') anomes, to_char(a.data,'mm/rrrr') mesano, \n" + 
                                      "       TO_NUMBER(replace(sum(a.valor_dia),'.',''))/1000 safatu, \n" + 
                                      "      (select max(TO_NUMBER(replace(b.valor_mes,'.',''))/1000) safant  \n" + 
                                      "       from sigind.db_acu11 b \n" + 
                                      "       where b.data between '01/09/2011' and TRUNC(SYSDATE)-1 \n" + 
                                      "       and   b.n <= (select max(bb.n) from sigind.db_acum bb \n" + 
                                      "                     where bb.varia = 'TOALP' \n" + 
                                      "                     and   bb.data  = trunc(sysdate) -1) \n" + 
                                      "       and   to_char(b.data,'mm') = substr(to_char(a.data,'rrrr/mm'),6,2) \n" + 
                                      "       and   b.varia = 'TOALP') safant \n" + 
                                      "from sigind.db_acum a  \n" + 
                                      "where a.data between '01/09/2012' and TRUNC(SYSDATE)-1  \n" + 
                                      "and   a.varia = 'TOALP' \n" + 
                                      "group by to_char(a.data,'rrrr/mm'), to_char(a.data,'mm/rrrr'), a.n \n" + 
                                      ") \n" + 
                                      "group by anomes, mesano \n" + 
                                      "order by 1");
             
             out.println("<div id='chartcontainer_3'> </div>");
             out.println("<script type='text/javascript'>");
             out.println("var myData1 = new Array([' ', 0]");
             nSafant = 0;
             while (rset.next()){
                 nSafant = nSafant + rset.getFloat("safant");                 
                 if (rset.getFloat("safant") > 0){
                     out.println(", ['" + rset.getString("mesano") + "', " + nSafant + "]");                 
                 }
             }
             out.println(");");
             out.println("var myData2 = new Array([' ', 0]");
             nSafatu = 0;
             rset.beforeFirst();
             while (rset.next()){
                 nSafatu = nSafatu + rset.getFloat("safatu");                 
                 if (rset.getFloat("safatu") > 0){
                     out.println(", ['" + rset.getString("mesano") + "', " + nSafatu + "]");                 
                 }
             }
             out.println(");");
             if (nSafant > 0 && nSafatu > 0){
                out.println("var myChart = new JSChart('chartcontainer_3', 'line');");
                out.println("myChart.setDataArray(myData1, 'Safra Anterior');");
                out.println("myChart.setDataArray(myData2, 'Safra Atual');");
                out.println("myChart.setAxisNameX('Meses');");
                out.println("myChart.setAxisNameY(' ');");
                out.println("myChart.setTitle('Álcool Total Produzido');");
                //out.println("myChart.setBackgroundColor('#99CCFF');");
                out.println("myChart.setAxisNameColor('#000000');");
                out.println("myChart.setAxisNameColorX('#000000');");
                out.println("myChart.setAxisNameColorY('#000000');");
                out.println("myChart.setAxisColor('#000000');");
                out.println("myChart.setAxisValuesColor('#000000');");
                out.println("myChart.setAxisValuesFontSize(15);");             
                out.println("myChart.setSize(700, 300);");
                out.println("myChart.setShowYValues(false);");
                //
                out.println("myChart.setLegendDetect(true);");
                out.println("myChart.setLegendFontSize(12);");
                out.println("myChart.setLegendColor('#000000');");
                out.println("myChart.setLegendShow(true);");                         
                //             
                out.println("myChart.setGridColor('#000000');");
                out.println("myChart.setTitleColor('#000000');");
                out.println("myChart.setTitleFontSize(20);");
                out.println("myChart.setLineColor('#FF6600', 'Safra Anterior');");
                out.println("myChart.setLineColor('#000099', 'Safra Atual');");
                out.println("myChart.setLineWidth(5, 'Safra Anterior');");
                out.println("myChart.setLineWidth(5, 'Safra Atual');");
                out.println("myChart.draw();");
             }
             out.println("</script>");
             
             
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

        <!--
        <div id='chartcontainer'> </div>
        <script type="text/javascript">
            var myData1 = new Array([10, 20], [15, 10], [20, 30], [25, 10], [30, 5]);
            var myData2 = new Array([10, 25], [15, 15], [20, 35], [25, 15], [30, 10]);
            var myChart = new JSChart('chartcontainer', 'line');
            myChart.setDataArray(myData1, 'line_1');
            myChart.setDataArray(myData2, 'line_2');
            myChart.setAxisNameX('Toneladas');
            myChart.setAxisNameY('Meses');
            myChart.setTitle('Nichael');
            myChart.setBackgroundColor('blue');
            myChart.setSize(500, 400);
            myChart.setTitleColor('#ff0f0f');
            myChart.setTitleFontSize(20);
            myChart.setLineColor('#ff0f0f', 'line_1');
            myChart.setLineWidth(5, 'line_2');
            myChart.set3D(true);
            myChart.draw();
        </script>                       
        -->
        
    </body>
</html>
