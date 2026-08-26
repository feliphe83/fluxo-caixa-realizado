<%
    // Guard de login unificado com a intranet.
    // Sem sessao valida aqui, volta pela ponte /ir-mapa-gerencial, que exige o
    // login da intranet (AuthFilter) e devolve com um token assinado.
    HttpSession sessao = request.getSession();
    if (!Boolean.TRUE.equals(sessao.getAttribute("autenticado"))) {
        response.sendRedirect("/fluxo-caixa/ir-mapa-gerencial");
        return;
    }
%>
