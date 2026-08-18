/**
 * Menu lateral global da intranet — injetado em toda tela autenticada (exceto
 * admin.html, que já tem seu próprio painel lateral, e as telas públicas de
 * login/recuperação de senha). Busca os módulos liberados para o usuário em
 * api/hub (mesma rota do hub.html) e a sessão em api/sessao, destaca a tela
 * atual e não exige nenhuma mudança de layout na página que o inclui.
 *
 * Em tela larga fica FIXO à esquerda em TODAS as telas: ocupa a sua faixa e
 * o conteúdo começa ao lado dele. Trocar de módulo não muda a navegação de
 * lugar — a barra é sempre o mesmo ponto de referência.
 *
 * Em tela estreita (< 900px) a faixa comeria metade do espaço, então ele se
 * recolhe atrás da aba de "3 riscos" e abre por cima do conteúdo, saindo ao
 * escolher um módulo ou clicar fora.
 *
 * A aba fica na BORDA esquerda, e não no canto superior: no topo à esquerda
 * ela cairia em cima do logo, que é o que todas as telas têm ali.
 *
 * Uso: <script src="js/sidebar-nav.js" defer></script> antes do fechamento
 * do <body>, igual ao padrão já usado para js/agro-chat-widget.js.
 */
(function () {
  const ARQUIVO_ATUAL = (location.pathname.split('/').pop() || 'hub.html').toLowerCase();
  /** Abaixo disso, faixa fixa de 232px come metade da tela — vira overlay. */
  const LARGURA_MINIMA_FIXO = 900;

  // Ícones: js/icones-modulos.js, o mesmo arquivo do hub. Aqui existiam
  // quatro, e todo módulo fora dessa lista virava a mesma caixa — o menu
  // inteiro ficava com o mesmo desenho repetido.
  //
  // Se o arquivo não estiver carregado na página, cai num traço discreto em
  // vez de estourar: menu sem ícone ainda navega; menu que não abre, não.
  function iconeDoModulo(mod, tamanho) {
    if (window.IconesModulos) return window.IconesModulos.doModulo(mod, tamanho || 16);
    return '<svg width="' + (tamanho || 16) + '" height="' + (tamanho || 16) + '" fill="none" '
         + 'viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><line x1="5" y1="12" x2="19" y2="12"/></svg>';
  }

  function escapeHtml(s) {
    if (s == null) return '';
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function ultimoSegmento(url) {
    if (!url) return '';
    return url.split('/').pop().split('?')[0].toLowerCase();
  }

  // ── Estilos (namespaced com prefixo snav- e variáveis próprias, pra não
  // colidir com o :root de cada página) ────────────────────────────────────
  const style = document.createElement('style');
  style.textContent = `
    :root { --snav-w: 232px; }

    .snav-sidebar {
      position: fixed; top: 0; left: 0; height: 100vh; width: var(--snav-w);
      background: #0f2460; border-right: 1px solid rgba(255,255,255,0.09);
      display: flex; flex-direction: column; z-index: 1040;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      transform: translateX(-100%); transition: transform .22s ease;
      box-shadow: 0 0 40px rgba(0,0,0,.35);
    }
    .snav-sidebar.aberto { transform: translateX(0); }

    /* Fixo (só no hub, em tela larga): sem deslizar e sem sombra — não é uma
       camada por cima de nada, é uma coluna da página. O padding no body é o
       que abre lugar para ela; sem isso o conteúdo começaria embaixo do menu. */
    .snav-sidebar.fixo { transform: none; box-shadow: none; transition: none; }
    html.snav-fixo body { padding-left: var(--snav-w); }

    .snav-backdrop {
      display: none; position: fixed; inset: 0; background: rgba(10,22,40,.35);
      z-index: 1030;
    }
    .snav-backdrop.aberto { display: block; }
    .snav-logo {
      padding: 16px 14px; border-bottom: 1px solid rgba(255,255,255,0.09);
      display: flex; align-items: center; gap: 10px; flex-shrink: 0;
    }
    .snav-logo-box {
      width: 36px; height: 36px; border-radius: 9px; background: white;
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0; overflow: hidden; padding: 4px;
    }
    .snav-logo-box img { width: 100%; height: 100%; object-fit: contain; }
    .snav-logo-txt { font-size: 12.5px; font-weight: 700; color: #ffffff; line-height: 1.3; }
    .snav-logo-sub { font-size: 9.5px; color: #8492a5; text-transform: uppercase; letter-spacing: .6px; }

    /* Cresce só até onde a lista pede, e rola quando passar disso.
       Com "flex: 1" ele esticava até o rodapé e empurrava Administração e o
       usuário lá para baixo — o vazio no meio do menu vinha daí. Agora o
       rodapé vem logo depois do último módulo.
       O min-height: 0 é o que deixa a rolagem funcionar dentro do flex. */
    .snav-nav { flex: 0 1 auto; min-height: 0; padding: 10px 10px 14px; overflow-y: auto;
                scrollbar-width: thin; scrollbar-color: rgba(255,255,255,.22) transparent; }
    .snav-nav::-webkit-scrollbar { width: 6px; }
    .snav-nav::-webkit-scrollbar-thumb { background: rgba(255,255,255,.22); border-radius: 3px; }
    .snav-nav::-webkit-scrollbar-track { background: transparent; }
    .snav-sec { font-size: 9.5px; font-weight: 700; color: #8492a5;
                text-transform: uppercase; letter-spacing: 1px;
                padding: 0 12px; margin: 18px 0 6px; }
    .snav-sec:first-child { margin-top: 4px; }
    /* A barra de acento à esquerda nasce transparente e ganha cor no hover
       e no item atual. Assim o realce entra pelo mesmo lugar nos dois casos,
       em vez de o fundo mudar num e a cor do texto no outro. */
    .snav-item {
      display: flex; align-items: center; gap: 10px;
      padding: 9px 10px 9px 12px; border-radius: 8px;
      color: #c5d2e6; font-size: 12.5px; font-weight: 500; text-decoration: none;
      margin-bottom: 2px; transition: background .15s, color .15s, box-shadow .15s;
      cursor: pointer; position: relative;
      box-shadow: inset 3px 0 0 transparent;
    }
    .snav-item:hover { background: rgba(255,255,255,.09); color: #ffffff;
                       box-shadow: inset 3px 0 0 rgba(125,179,255,.55); }
    .snav-item.ativo { background: rgba(125,179,255,.16); color: #ffffff; font-weight: 700;
                       box-shadow: inset 3px 0 0 #7db3ff; }
    .snav-item svg { flex-shrink: 0; width: 17px; height: 17px; opacity: .78; }
    .snav-item:hover svg, .snav-item.ativo svg { opacity: 1; }
    .snav-item .txt { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

    .snav-loading, .snav-erro { padding: 12px 8px; font-size: 11.5px; color: #8492a5; }
    .snav-erro { color: #ef4444; }

    .snav-foot { padding: 10px 10px 12px; border-top: 1px solid rgba(255,255,255,0.09); flex-shrink: 0; }
    .snav-user { display: flex; align-items: center; gap: 10px; padding: 9px 10px;
                 border-radius: 10px; background: rgba(255,255,255,.07);
                 border: 1px solid rgba(255,255,255,.06); }
    .snav-avatar { width: 30px; height: 30px; border-radius: 8px; background: #2a5bb8; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; color: white; flex-shrink: 0; }
    .snav-uinfo { flex: 1; min-width: 0; }
    .snav-uname { font-size: 11.5px; font-weight: 600; color: #ffffff; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .snav-urole { font-size: 9.5px; color: #8492a5; text-transform: uppercase; letter-spacing: .4px; }
    .snav-btn-sair { background: none; border: none; cursor: pointer; color: #8492a5; padding: 5px; border-radius: 5px; flex-shrink: 0; transition: color .15s; }
    .snav-btn-sair:hover { color: #ef4444; }

    /* Aba na borda esquerda, na altura dos olhos. No canto superior esquerdo
       ela ficaria sobre o logo — que é o que todas as telas põem ali. */
    .snav-toggle {
      display: flex; position: fixed; left: 0; top: 50%; z-index: 1020;
      transform: translateY(-50%);
      width: 30px; height: 56px; border: none;
      border-radius: 0 10px 10px 0;
      background: #0f2460; color: rgba(255,255,255,.85); cursor: pointer;
      align-items: center; justify-content: center;
      box-shadow: 2px 0 12px rgba(0,0,0,.28);
      transition: background .15s, width .15s, color .15s;
    }
    .snav-toggle:hover { background: #1a3a7c; color: #fff; width: 36px; }
    .snav-toggle.escondido { display: none; }

    @media print {
      .snav-sidebar, .snav-toggle, .snav-backdrop { display: none !important; }
      html.snav-fixo body { padding-left: 0 !important; }
    }
  `;
  document.head.appendChild(style);

  // ── Botão de abrir/fechar — sempre visível, recolhido por padrão ────────
  const toggle = document.createElement('button');
  toggle.className = 'snav-toggle';
  toggle.setAttribute('aria-label', 'Abrir menu');
  toggle.innerHTML = '<svg width="18" height="18" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="18" x2="21" y2="18"/></svg>';
  document.body.appendChild(toggle);

  // Fundo escurecido atrás do menu quando aberto — clicar nele fecha.
  const backdrop = document.createElement('div');
  backdrop.className = 'snav-backdrop';
  document.body.appendChild(backdrop);

  // ── Sidebar ──────────────────────────────────────────────────────────────
  const sidebar = document.createElement('nav');
  sidebar.className = 'snav-sidebar';
  sidebar.innerHTML = `
    <div class="snav-logo">
      <div class="snav-logo-box"><img src="img/logo.png" alt="USC"></div>
      <div>
        <div class="snav-logo-txt">Usina Santa Clotilde</div>
        <div class="snav-logo-sub">Intranet</div>
      </div>
    </div>
    <div class="snav-nav" id="snavNav"><div class="snav-loading">Carregando módulos…</div></div>
    <div class="snav-foot" id="snavFoot"></div>
  `;
  document.body.prepend(sidebar);

  function fixado() { return sidebar.classList.contains('fixo'); }

  function abrirMenu() {
    if (fixado()) return;              // já está à vista
    sidebar.classList.add('aberto');
    backdrop.classList.add('aberto');
  }
  function fecharMenu() {
    if (fixado()) return;
    sidebar.classList.remove('aberto');
    backdrop.classList.remove('aberto');
  }

  /**
   * Decide entre fixo e recolhido. Roda na carga e a cada redimensionamento
   * porque a janela pode cruzar o limite de largura a qualquer momento —
   * girar um tablet já basta.
   */
  function ajustarModo() {
    const fixar = window.innerWidth >= LARGURA_MINIMA_FIXO;
    sidebar.classList.toggle('fixo', fixar);
    document.documentElement.classList.toggle('snav-fixo', fixar);
    toggle.classList.toggle('escondido', fixar);
    if (fixar) {
      // Sai de qualquer estado de overlay que tenha ficado da largura anterior.
      sidebar.classList.remove('aberto');
      backdrop.classList.remove('aberto');
    }
  }

  ajustarModo();
  window.addEventListener('resize', ajustarModo);

  toggle.addEventListener('click', () => {
    sidebar.classList.contains('aberto') ? fecharMenu() : abrirMenu();
  });
  backdrop.addEventListener('click', fecharMenu);
  // Esc fecha — quando o menu está por cima do conteúdo, é o caminho que a
  // mão já procura.
  document.addEventListener('keydown', e => { if (e.key === 'Escape') fecharMenu(); });
  sidebar.querySelectorAll('.snav-nav, .snav-foot').forEach(el => {
    el.addEventListener('click', e => {
      if (e.target.closest('a, .snav-item')) fecharMenu();
    });
  });

  // ── Sessão (avatar, nome, sair, administração) ──────────────────────────
  fetch('api/sessao').then(r => r.json()).then(j => {
    if (!j.ok) { window.location.href = 'login.html'; return; }
    const inicial = (j.nome || j.logon || '?').trim().charAt(0).toUpperCase();
    document.getElementById('snavFoot').innerHTML = `
      ${j.administrador ? `
        <a class="snav-item" href="admin.html" style="margin-bottom:8px">
          <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>
          <span class="txt">Administração</span>
        </a>` : ''}
      <div class="snav-user">
        <div class="snav-avatar">${escapeHtml(inicial)}</div>
        <div class="snav-uinfo">
          <div class="snav-uname" title="${escapeHtml(j.nome || j.logon)}">${escapeHtml(j.nome || j.logon)}</div>
          <div class="snav-urole">${j.administrador ? 'Administrador' : 'Usuário'}</div>
        </div>
        <button class="snav-btn-sair" title="Sair" onclick="fetch('api/login').finally(()=>location.href='login.html')">
          <svg width="15" height="15" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
        </button>
      </div>`;
  }).catch(() => {});

  // ── Módulos liberados (api/hub) ──────────────────────────────────────────
  fetch('api/hub').then(r => r.json()).then(j => {
    const nav = document.getElementById('snavNav');
    if (!j.ok) { nav.innerHTML = '<div class="snav-erro">Erro ao carregar módulos.</div>'; return; }

    const itemHub = `
      <a class="snav-item ${ARQUIVO_ATUAL === 'hub.html' ? 'ativo' : ''}" href="hub.html">
        <svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>
        <span class="txt">Hub</span>
      </a>`;

    if (!j.categorias || j.categorias.length === 0) {
      nav.innerHTML = itemHub;
      return;
    }

    nav.innerHTML = itemHub + j.categorias.map(cat => `
      <div class="snav-sec">${escapeHtml(cat.nome)}</div>
      ${cat.modulos.map(mod => `
        <a class="snav-item ${ultimoSegmento(mod.urlDestino) === ARQUIVO_ATUAL ? 'ativo' : ''}"
           href="${escapeHtml(mod.urlDestino)}" title="${escapeHtml(mod.descricao || mod.nome)}">
          ${iconeDoModulo(mod, 16)}
          <span class="txt">${escapeHtml(mod.nome)}</span>
        </a>`).join('')}
    `).join('');
  }).catch(() => {
    document.getElementById('snavNav').innerHTML =
      '<a class="snav-item" href="hub.html">Hub</a><div class="snav-erro">Erro ao carregar módulos.</div>';
  });
})();
