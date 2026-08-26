<%@include file="validausuario.jsp"%>

<%-- 
    Document   : mobile_menu
    Created on : 17/02/2011, 15:57:56
    Author     : Nichael
--%>

<%@page contentType="text/html" pageEncoding="windows-1252"%>
<!DOCTYP<%-- 
    Document   : mobile_menu
    Created on : 17/02/2011, 15:57:56
    Author     : Nichael
--%>E HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=windows-1252">

        <script language="JavaScript">

            var nAtualizacaoAutomatica = 0;

            function acionatela(nNumTela){

               if (nNumTela == 1) {
                   window.open("mobile_tela1.jsp",'frm_mobile_tela');
               }
               if (nNumTela == 2) {
                   window.open("mobile_Industria.jsp",'frm_mobile_tela');
               }
               if (nNumTela == 3) {
                   window.open("mobile_frota.jsp",'frm_mobile_tela');
               }
               if (nNumTela == 4) {
                   window.open("mobile_frota_disposicao.jsp",'frm_mobile_tela');
               }
            }
            
            function ativaatualizacao(){

               if (nAtualizacaoAutomatica == 0){
                   nAtualizacaoAutomatica = 1;
                   document.getElementById("btnAtivaAutomatico").value = "Desativar Atualiz. Automática";
               }else{
                   nAtualizacaoAutomatica = 0;
                   document.getElementById("btnAtivaAutomatico").value = "Ativar Atualiz. Automática";
               }
            }            

        </script>



    </head>
    <body bgcolor="lavender">
        <table width="100%">
            <tr bgcolor="#009999" style="font-family: Verdana; font-size: 15px; color: white">
                <td align="center"><input type="button" value="Agrícola"  style="width: 180px" onclick="acionatela(1);">
                                   <input type="button" value="Indústria" style="width: 180px" onclick="acionatela(2);">
                                   <input type="button" value="Frota"     style="width: 180px" onclick="acionatela(3);">
                                   <input type="button" value="Disponibilidade da Frota"     style="width: 180px" onclick="acionatela(4);">
                                   <input id="btnAtivaAutomatico" type="button" value="Ativar Atualiz. Automática"  style="width: 190px" onclick="ativaatualizacao();">
                </td>
            </tr>
        </table>
        
        
        <script>
            var nTela = 0;
            var nTempo = 10000;

            contator();

            function contator() {
                if (nAtualizacaoAutomatica == 1){
                    nTela += 1;
                    if (nTela >= 5) {
                        nTela = 1;
                    }
                    acionatela(nTela);
                    if (nTela == 1){
                        nTempo = 30000;
                    }else{
                        nTempo = 10000;
                    }
                }
                setTimeout(contator, nTempo);
            }
            
            acionatela(1)

        </script>
        
    </body>
</html>
