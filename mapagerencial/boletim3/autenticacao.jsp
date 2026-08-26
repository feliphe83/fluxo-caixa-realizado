<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%--
The taglib directive below imports the JSTL library. If you uncomment it,
you must also add the JSTL library to the project. The Add Library... action
on Libraries node in Projects view can be used to add the JSTL 1.1 library.
--%>
<%--
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
--%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
    "http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">
        <script>

            function acao() {
                if (document.getElementById('logusu').value == 'ciamercantil') {
                    alert('O campo NOME DE USUÁRIO é obrigatório!');
                }
                else {
                    if (document.getElementById('senusu').value == '') {
                        alert('O campo PASSWORD é obrigatório!');
                    } else {
                        document.FormAutenticacao.action = "../AutenticaServlet";
                    }
                }
            }

        </script>

    </head>

    <body onload="document.getElementById('logusu').focus();">

        <table width="100%" border="0" cellpadding="1" cellspacing="1">
            <tr bgcolor="#008080">
                <td colspan="20" align="center" style="font-family: Verdana; font-size:15pt; color: white">Usina Santa Clotilde - Autenticação de Usuário</td>
            </tr>
            <tr>
                <td colspan="20" align="center" style="font-family: Verdana; font-size:15pt">&nbsp;</td>
            </tr>
        </table>

        <form name="FormAutenticacao" method="post">
            <table width="100%" style="font-family: Verdana; font-size: 10pt"><tr><td align="center" valign="middle" height="300px">
                        <table style="border: 1px" align="center" border="0" cellpadding="1" cellspacing="1">
                            <tr><td colspan="2" align="center"><img align="middle" src="../Imagens/Logo.png"></td></tr>
                            <tr><td align="center"><img align="middle" src="../Imagens/chaves.jpg"></td>
                                <td><table width="100%" style="border: 1px" align="center" border="0" cellpadding="1" cellspacing="1">
                                        <tr><td align="left">Usuário</td><td><input style="width: 160px" type="text" id="logusu" name="logusu"</td></tr>
                                        <tr><td align="left">Senha</td><td><input style="width: 160px" type="password" size="20" id="senusu" name="senusu"></td></tr>
                                        <tr><td colspan="2"><hr width="100%"</td></tr>
                                        <tr><td colspan="2" align="center"><input type="submit" onclick="Javascript: acao();" value="Entrar" title="Entrar"></td></tr>
                                    </table>
                                </td></tr>
                        </table>
                    </td></tr>
            </table>
            <script>
                <% if (request.getParameter("msgsession") != null) { %>
                alert('Sessão Expirada!');
                <% } else if (request.getParameter("msg") != null) { %>
                alert('Usuário ou Senha Inválido!');
                <% }%>
            </script>

        </form>
    </body>
</html>
