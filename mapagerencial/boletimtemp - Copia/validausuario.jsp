<% 
    HttpSession sessao = request.getSession();

    if (!sessao.getAttribute("autenticado").equals(Boolean.TRUE)){
         //request.getRequestDispatcher("Empenho/EmpenhoBuscaRapida.jsp").forward(request, response);
        response.sendRedirect("autenticacao.jsp?msgsession=Sessão Expirada ou Inválida!");
    }
    
%>
