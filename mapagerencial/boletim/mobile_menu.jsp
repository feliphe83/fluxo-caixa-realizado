<%@include file="validausuario.jsp"%>
<%@page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Mapa Gerencial</title>
<style>
  * { box-sizing: border-box; }
  html, body { margin: 0; height: 100%; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: linear-gradient(135deg, #16342a 0%, #234f3b 100%);
    color: #fff;
  }
  .barra {
    display: flex; align-items: center; gap: 14px;
    padding: 8px 16px; height: 100%;
    overflow-x: auto; white-space: nowrap;
  }
  .logo-box {
    width: 44px; height: 44px; border-radius: 9px; background: #fff;
    display: flex; align-items: center; justify-content: center;
    overflow: hidden; padding: 4px; flex-shrink: 0;
  }
  .logo-box img { width: 100%; height: 100%; object-fit: contain; }
  .titulo { font-size: 15px; font-weight: 800; letter-spacing: .2px; margin-right: 6px; }
  .titulo small { display: block; font-size: 10.5px; font-weight: 600; opacity: .8; text-transform: uppercase; letter-spacing: .5px; }
  .voltar {
    display: inline-flex; align-items: center; gap: 6px;
    padding: 8px 12px; border-radius: 8px; text-decoration: none;
    border: 1.5px solid rgba(255,255,255,.3); background: rgba(255,255,255,.08);
    color: rgba(255,255,255,.92); font-size: 12.5px; font-weight: 700; flex-shrink: 0;
  }
  .voltar:hover { background: rgba(255,255,255,.18); }
  .menu { display: inline-flex; align-items: center; gap: 8px; margin-left: auto; flex-shrink: 0; }
  .bt {
    padding: 9px 16px; border-radius: 9px; cursor: pointer;
    border: 1.5px solid rgba(255,255,255,.28); background: rgba(255,255,255,.10);
    color: #fff; font-size: 13px; font-weight: 700; white-space: nowrap;
  }
  .bt:hover { background: rgba(255,255,255,.22); }
  .bt.ativo { background: #fff; color: #16342a; border-color: #fff; box-shadow: 0 2px 8px rgba(0,0,0,.25); }
  .bt.toggle { border-style: dashed; }
  .bt.toggle.ativo { background: #1baf7a; color: #fff; border-color: #1baf7a; border-style: solid; }

  <%-- Barra de rolagem discreta quando os botões não cabem --%>
  .barra::-webkit-scrollbar { height: 6px; }
  .barra::-webkit-scrollbar-thumb { background: rgba(255,255,255,.35); border-radius: 3px; }
</style>
<script>
  var nAtualizacaoAutomatica = 0;

  var TELAS = { 1: "mobile_tela1.jsp", 2: "mobile_Industria.jsp", 3: "mobile_frota.jsp", 4: "mobile_frota_disposicao.jsp" };

  function acionatela(n){
    var tela = TELAS[n];
    if (tela) window.open(tela, 'frm_mobile_tela');
    for (var i = 1; i <= 4; i++){
      var el = document.getElementById('bt' + i);
      if (el) { if (i == n) el.classList.add('ativo'); else el.classList.remove('ativo'); }
    }
  }

  function ativaatualizacao(){
    var b = document.getElementById("btnAtivaAutomatico");
    if (nAtualizacaoAutomatica == 0){
      nAtualizacaoAutomatica = 1;
      b.textContent = "⏸ Desativar automática";
      b.classList.add("ativo");
    } else {
      nAtualizacaoAutomatica = 0;
      b.textContent = "▶ Atualização automática";
      b.classList.remove("ativo");
    }
  }
</script>
</head>
<body>
  <div class="barra">
    <a class="voltar" href="/fluxo-caixa/hub.html" target="_top" title="Voltar à intranet">← Intranet</a>
    <div class="logo-box"><img src="/fluxo-caixa/img/logo.png" alt="USC"></div>
    <div class="titulo">Mapa Gerencial<small>Usina Santa Clotilde</small></div>

    <nav class="menu">
      <button id="bt1" class="bt" onclick="acionatela(1)">Agrícola</button>
      <button id="bt2" class="bt" onclick="acionatela(2)">Indústria</button>
      <button id="bt3" class="bt" onclick="acionatela(3)">Frota</button>
      <button id="bt4" class="bt" onclick="acionatela(4)">Disponibilidade da Frota</button>
      <button id="btnAtivaAutomatico" class="bt toggle" onclick="ativaatualizacao()">▶ Atualização automática</button>
    </nav>
  </div>

  <script>
    var nTela = 0;
    var nTempo = 10000;

    function contator() {
      if (nAtualizacaoAutomatica == 1){
        nTela += 1;
        if (nTela >= 5) { nTela = 1; }
        acionatela(nTela);
        nTempo = (nTela == 1) ? 30000 : 10000;
      }
      setTimeout(contator, nTempo);
    }
    contator();

    <%-- Abre a Agrícola (entrada de cana) já ao carregar. --%>
    acionatela(1);
  </script>
</body>
</html>
