// Botão flutuante do Dr. Alfredo (agro-chat), exibido em todas as telas da
// intranet para quem tem a permissão "Chat — Acesso ao Assistente"
// (administradores sempre têm). Basta incluir nas páginas:
//   <script src="js/agro-chat-widget.js" defer></script>
(function () {
  if (window.location.pathname.endsWith('agro-chat.html')) return;

  fetch('api/ia/agro-chat-acesso')
    .then(function (r) { return r.json(); })
    .then(function (j) { if (j && j.ok && j.acesso) criar(); })
    .catch(function () { /* sem sessão ou erro: não mostra o botão */ });

  function criar() {
    var style = document.createElement('style');
    style.textContent = [
      '#agroChatFab {',
      '  position: fixed; right: 22px; bottom: 22px; z-index: 9999;',
      '  width: 58px; height: 58px; border-radius: 50%; border: none; cursor: pointer;',
      '  background: #0f2460; color: #fff;',
      '  display: flex; align-items: center; justify-content: center;',
      '  box-shadow: 0 6px 18px rgba(15,36,96,.35);',
      '  transition: transform .18s ease, box-shadow .18s;',
      '  animation: agroFabIn .5s ease;',
      '}',
      '#agroChatFab:hover { transform: scale(1.08); box-shadow: 0 8px 24px rgba(15,36,96,.45); }',
      '#agroChatFab::before {',
      "  content: ''; position: absolute; inset: -6px; border-radius: 50%;",
      '  border: 2px solid rgba(15,36,96,.45);',
      '  animation: agroFabPulse 2.4s ease-out infinite;',
      '}',
      '#agroChatFab svg { width: 26px; height: 26px; }',
      '#agroChatFabTip {',
      '  position: fixed; right: 90px; bottom: 38px; z-index: 9999;',
      "  background: #0f2460; color: #fff; font: 600 12px 'Segoe UI', system-ui, sans-serif;",
      '  padding: 6px 12px; border-radius: 8px; white-space: nowrap;',
      '  opacity: 0; pointer-events: none; transition: opacity .18s;',
      '}',
      '#agroChatFab:hover ~ #agroChatFabTip { opacity: 1; }',
      '@keyframes agroFabPulse { 0% { transform: scale(.92); opacity: .8; } 70% { transform: scale(1.35); opacity: 0; } 100% { opacity: 0; } }',
      '@keyframes agroFabIn { from { transform: translateY(90px); opacity: 0; } to { transform: none; opacity: 1; } }',
      '@media print { #agroChatFab, #agroChatFabTip { display: none !important; } }'
    ].join('\n');
    document.head.appendChild(style);

    var btn = document.createElement('button');
    btn.id = 'agroChatFab';
    btn.setAttribute('aria-label', 'Abrir o Dr. Alfredo');
    btn.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">'
      + '<path stroke-linecap="round" stroke-linejoin="round" '
      + 'd="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.97-4.03 9-9 9a9.01 9.01 0 01-5.24-1.67L3 21l1.67-3.76A8.96 8.96 0 013 12c0-4.97 4.03-9 9-9s9 4.03 9 9z"/></svg>';
    btn.onclick = function () { window.location.href = 'agro-chat.html'; };

    var tip = document.createElement('div');
    tip.id = 'agroChatFabTip';
    tip.textContent = 'Dr. Alfredo';

    document.body.appendChild(btn);
    document.body.appendChild(tip);
  }
})();
