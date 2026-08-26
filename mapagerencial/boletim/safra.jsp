<%@include file="validausuario.jsp"%>
<%@page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Config da safra APENAS do Mapa Gerencial (independente da intranet).
    // Grava em /safra.txt na raiz do contexto; as telas do boletim leem daí.
    java.io.File sf = new java.io.File(application.getRealPath("/safra.txt"));
    String msg = "", tipo = "";

    if ("POST".equalsIgnoreCase(request.getMethod())) {
        String nv = request.getParameter("safra");
        try {
            nv = nv == null ? "" : nv.trim();
            Integer.parseInt(nv);                    // valida número
            if (nv.length() > 4) throw new NumberFormatException();
            java.nio.file.Files.write(sf.toPath(), nv.getBytes("UTF-8"));
            msg = "Safra do Mapa Gerencial atualizada para " + nv + ".";
            tipo = "ok";
        } catch (Exception e) {
            msg = "Informe um número de safra válido (ex.: 74).";
            tipo = "erro";
        }
    }

    String atual = "74";
    try { if (sf.exists()) { String v = new String(java.nio.file.Files.readAllBytes(sf.toPath())).trim(); Integer.parseInt(v); atual = v; } } catch (Exception e) {}
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Safra do Mapa Gerencial</title>
<style>
  * { box-sizing: border-box; }
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #eef2f7; margin: 0; padding: 28px; color: #16342a; }
  .card { max-width: 460px; margin: 0 auto; background: #fff; border: 1.5px solid #dfe6ef; border-radius: 14px; padding: 24px; box-shadow: 0 6px 24px rgba(0,0,0,.06); }
  h2 { margin: 0 0 4px; font-size: 19px; }
  .sub { color: #64748b; font-size: 13px; margin-bottom: 18px; }
  .atual { background: #eaf7f0; border: 1.5px solid #b6e3cc; color: #14603f; border-radius: 10px; padding: 12px 14px; font-size: 14px; margin-bottom: 18px; }
  .atual b { font-size: 22px; }
  label { display: block; font-size: 12px; font-weight: 700; color: #475569; text-transform: uppercase; letter-spacing: .4px; margin-bottom: 6px; }
  input[type=number] { width: 140px; padding: 10px 12px; border: 1.5px solid #cbd5e1; border-radius: 9px; font-size: 18px; font-weight: 700; color: #16342a; }
  button { margin-left: 10px; padding: 11px 20px; border: none; border-radius: 9px; background: #234f3b; color: #fff; font-size: 14px; font-weight: 700; cursor: pointer; }
  button:hover { background: #1a3d2d; }
  .msg { margin-top: 16px; padding: 11px 14px; border-radius: 9px; font-size: 13.5px; font-weight: 600; }
  .msg.ok { background: #e7f7ef; border: 1.5px solid #a8dcc1; color: #14603f; }
  .msg.erro { background: #fdecec; border: 1.5px solid #f5c2c2; color: #b23b3b; }
  .obs { margin-top: 16px; font-size: 12px; color: #64748b; line-height: 1.5; }
</style>
</head>
<body>
  <div class="card">
    <h2>Safra do Mapa Gerencial</h2>
    <div class="sub">Vale só para as telas do Mapa Gerencial (entrada de cana, frota, etc.).</div>

    <div class="atual">Safra atual: <b><%= atual %></b></div>

    <form method="post">
      <label>Nova safra</label>
      <input type="number" name="safra" min="1" max="9999" step="1" value="<%= atual %>">
      <button type="submit">Salvar</button>
    </form>

    <% if (!msg.isEmpty()) { %>
      <div class="msg <%= tipo %>"><%= msg %></div>
    <% } %>

    <div class="obs">Ao salvar, as telas do Mapa Gerencial passam a consultar essa safra. Recarregue a tela (Agrícola/Frota) para ver os dados da nova safra.</div>
  </div>
</body>
</html>
