/**
 * Menu lateral global da intranet — injetado em toda tela autenticada (exceto
 * admin.html, que já tem seu próprio painel lateral, e as telas públicas de
 * login/recuperação de senha). Busca os módulos liberados para o usuário em
 * api/hub (mesma rota do hub.html) e a sessão em api/sessao, destaca a tela
 * atual e não exige nenhuma mudança de layout na página que o inclui — só
 * empurra o <body> para a direita via margin-left.
 *
 * Uso: <script src="js/sidebar-nav.js" defer></script> antes do fechamento
 * do <body>, igual ao padrão já usado para js/agro-chat-widget.js.
 */
(function () {
  const ARQUIVO_ATUAL = (location.pathname.split('/').pop() || 'hub.html').toLowerCase();

  const ICONES = {
    'dollar-sign': '<svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/></svg>',
    'package':     '<svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>',
    'trending-up': '<svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>',
    'users':       '<svg width="16" height="16" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/></svg>',
  };
  function iconeHtml(nome) { return ICONES[nome] || ICONES['package']; }

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
    body { margin-left: var(--snav-w); }

    .snav-sidebar {
      position: fixed; top: 0; left: 0; height: 100vh; width: var(--snav-w);
      background: #0f1e36; border-right: 1px solid rgba(26,58,124,0.35);
      display: flex; flex-direction: column; z-index: 40;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      transition: transform .25s ease;
    }
    .snav-logo {
      padding: 18px 16px; border-bottom: 1px solid rgba(26,58,124,0.35);
      display: flex; align-items: center; gap: 10px; flex-shrink: 0;
    }
    .snav-logo-box {
      width: 36px; height: 36px; border-radius: 9px; background: #1a3a7c;
      display: flex; align-items: center; justify-content: center;
      color: white; font-weight: 800; font-size: 13px; flex-shrink: 0;
    }
    .snav-logo-txt { font-size: 12.5px; font-weight: 700; color: #e2e8f0; line-height: 1.3; }
    .snav-logo-sub { font-size: 9.5px; color: #64748b; text-transform: uppercase; letter-spacing: .6px; }

    .snav-nav { flex: 1; padding: 12px 10px; overflow-y: auto; }
    .snav-sec { font-size: 9.5px; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: .8px; padding: 0 8px; margin: 14px 0 4px; }
    .snav-sec:first-child { margin-top: 2px; }
    .snav-item {
      display: flex; align-items: center; gap: 9px; padding: 8px 10px; border-radius: 7px;
      color: #94a3b8; font-size: 12.5px; font-weight: 500; text-decoration: none;
      margin-bottom: 1px; transition: all .15s; cursor: pointer;
    }
    .snav-item:hover { background: #162440; color: #e2e8f0; }
    .snav-item.ativo { background: rgba(15,36,96,0.5); color: #7db3ff; font-weight: 700; }
    .snav-item svg { flex-shrink: 0; }
    .snav-item .txt { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

    .snav-loading, .snav-erro { padding: 12px 8px; font-size: 11.5px; color: #64748b; }
    .snav-erro { color: #ef4444; }

    .snav-foot { padding: 12px; border-top: 1px solid rgba(26,58,124,0.35); flex-shrink: 0; }
    .snav-user { display: flex; align-items: center; gap: 9px; padding: 9px 10px; border-radius: 9px; background: #162440; }
    .snav-avatar { width: 30px; height: 30px; border-radius: 8px; background: #1a3a7c; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; color: white; flex-shrink: 0; }
    .snav-uinfo { flex: 1; min-width: 0; }
    .snav-uname { font-size: 11.5px; font-weight: 600; color: #e2e8f0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .snav-urole { font-size: 9.5px; color: #64748b; text-transform: uppercase; letter-spacing: .4px; }
    .snav-btn-sair { background: none; border: none; cursor: pointer; color: #64748b; padding: 5px; border-radius: 5px; flex-shrink: 0; transition: color .15s; }
    .snav-btn-sair:hover { color: #ef4444; }

    .snav-toggle {
      display: none; position: fixed; top: 14px; left: 14px; z-index: 41;
      width: 38px; height: 38px; border-radius: 9px; border: none;
      background: #0f2460; color: white; cursor: pointer;
      align-items: center; justify-content: center; box-shadow: 0 2px 10px rgba(0,0,0,.25);
    }

    @media (max-width: 900px) {
      body { margin-left: 0; }
      .snav-sidebar { transform: translateX(-100%); box-shadow: 0 0 40px rgba(0,0,0,.4); }
      .snav-sidebar.aberto { transform: translateX(0); }
      .snav-toggle { display: flex; }
    }
    @media print {
      .snav-sidebar, .snav-toggle { display: none !important; }
      body { margin-left: 0 !important; }
    }
  `;
  document.head.appendChild(style);

  // ── Botão de abrir/fechar (só aparece em telas estreitas) ───────────────
  const toggle = document.createElement('button');
  toggle.className = 'snav-toggle';
  toggle.setAttribute('aria-label', 'Abrir menu');
  toggle.innerHTML = '<svg width="18" height="18" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="18" x2="21" y2="18"/></svg>';
  document.body.appendChild(toggle);

  // ── Sidebar ──────────────────────────────────────────────────────────────
  const sidebar = document.createElement('nav');
  sidebar.className = 'snav-sidebar';
  sidebar.innerHTML = `
    <div class="snav-logo">
      <div class="snav-logo-box">USC</div>
      <div>
        <div class="snav-logo-txt">Usina Santa Clotilde</div>
        <div class="snav-logo-sub">Intranet</div>
      </div>
    </div>
    <div class="snav-nav" id="snavNav"><div class="snav-loading">Carregando módulos…</div></div>
    <div class="snav-foot" id="snavFoot"></div>
  `;
  document.body.prepend(sidebar);

  toggle.addEventListener('click', () => sidebar.classList.toggle('aberto'));
  sidebar.querySelectorAll('.snav-nav, .snav-foot').forEach(el => {
    el.addEventListener('click', e => {
      if (e.target.closest('a, .snav-item')) sidebar.classList.remove('aberto');
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
          ${iconeHtml(mod.icone)}
          <span class="txt">${escapeHtml(mod.nome)}</span>
        </a>`).join('')}
    `).join('');
  }).catch(() => {
    document.getElementById('snavNav').innerHTML =
      '<a class="snav-item" href="hub.html">Hub</a><div class="snav-erro">Erro ao carregar módulos.</div>';
  });
})();
