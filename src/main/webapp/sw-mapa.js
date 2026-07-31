/**
 * Service Worker do Mapa de Talhões — faz a tela funcionar no campo, sem sinal.
 *
 * Três políticas, porque as três coisas têm naturezas diferentes:
 *
 * 1. Casca do app (HTML, Leaflet, GeoJSON dos talhões): cache primeiro. São
 *    arquivos que só mudam em deploy, e é o que precisa estar garantido no
 *    celular quando não há rede.
 * 2. Dados do ERP (/api/mapa-talhoes): rede primeiro, cache como reserva.
 *    Com sinal mostra o dado de agora; sem sinal, o da última vez que abriu.
 * 3. Imagens de satélite: cache do que já foi visto, com teto. NÃO baixa a
 *    região inteira em lote — os termos de uso do provedor não permitem
 *    espelhar o acervo. Sem sinal, área não visitada fica sem fundo, e os
 *    talhões continuam desenhados.
 */
const VERSAO = 'mapa-talhoes-v1';
const CACHE_CASCA = VERSAO + '-casca';
const CACHE_DADOS = VERSAO + '-dados';
const CACHE_TILES = VERSAO + '-tiles';

/** Teto de imagens guardadas — cada uma tem ~20 KB. */
const MAX_TILES = 3000;

const CASCA = [
  'mapa-talhoes.html',
  'js/leaflet.js',
  'css/leaflet.css',
  'mapas/talhoes.geojson',
  'img/logo.png'
];

/**
 * Guarda um arquivo só se a resposta for realmente ele.
 *
 * cache.add() segue redirecionamento e guarda o destino: com a sessão
 * expirada, o AuthFilter manda para login.html e o cache passaria a servir a
 * tela de login no lugar do mapa — que é justamente o que não pode acontecer
 * quando a pessoa está no campo, sem sinal, e depende do que foi guardado.
 */
async function guardarSeValido(cache, url) {
  const resp = await fetch(url, { redirect: 'follow' });
  if (!resp.ok || resp.redirected) return false;
  await cache.put(url, resp);
  return true;
}

self.addEventListener('install', evento => {
  evento.waitUntil(
    caches.open(CACHE_CASCA)
      // Um a um: se um arquivo falhar, os outros ainda ficam guardados.
      .then(c => Promise.allSettled(CASCA.map(u => guardarSeValido(c, u))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', evento => {
  evento.waitUntil(
    caches.keys()
      .then(nomes => Promise.all(nomes.filter(n => !n.startsWith(VERSAO)).map(n => caches.delete(n))))
      .then(() => self.clients.claim())
  );
});

/** Descarta as mais antigas quando passa do teto (FIFO, ordem de inserção). */
async function podarTiles() {
  const c = await caches.open(CACHE_TILES);
  const chaves = await c.keys();
  if (chaves.length <= MAX_TILES) return;
  await Promise.all(chaves.slice(0, chaves.length - MAX_TILES).map(k => c.delete(k)));
}

self.addEventListener('fetch', evento => {
  const req = evento.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // ── Imagens de satélite (outro domínio) ────────────────────────────────
  if (url.origin !== self.location.origin) {
    if (!/arcgisonline|tile\.openstreetmap/.test(url.host)) return;
    evento.respondWith((async () => {
      const c = await caches.open(CACHE_TILES);
      const guardado = await c.match(req);
      if (guardado) return guardado;
      try {
        const resp = await fetch(req);
        // Tile de outro domínio vem opaca (sem CORS); dá pra guardar e exibir.
        if (resp && (resp.ok || resp.type === 'opaque')) {
          await c.put(req, resp.clone());
          podarTiles();
        }
        return resp;
      } catch (e) {
        return new Response('', { status: 504, statusText: 'Sem conexão' });
      }
    })());
    return;
  }

  // ── Dados do ERP ───────────────────────────────────────────────────────
  if (url.pathname.includes('/api/mapa-talhoes')) {
    evento.respondWith((async () => {
      const c = await caches.open(CACHE_DADOS);
      try {
        const resp = await fetch(req);
        // Só guarda resposta boa: se a sessão caiu, o servidor devolve o
        // redirecionamento do login, e guardar isso envenenaria o cache.
        if (resp && resp.ok && (resp.headers.get('content-type') || '').includes('json')) {
          await c.put(req, resp.clone());
        }
        return resp;
      } catch (e) {
        const guardado = await c.match(req);
        if (guardado) {
          // Marca a resposta para a tela avisar que o dado é da última visita.
          const corpo = await guardado.json();
          corpo.doCache = true;
          return new Response(JSON.stringify(corpo),
            { headers: { 'Content-Type': 'application/json;charset=UTF-8' } });
        }
        throw e;
      }
    })());
    return;
  }

  // ── Casca ──────────────────────────────────────────────────────────────
  if (CASCA.some(p => url.pathname.endsWith(p))) {
    evento.respondWith((async () => {
      const guardado = await caches.match(req, { ignoreSearch: true });
      if (guardado) {
        // Atualiza por trás para o próximo acesso, sem segurar a tela — e
        // pelo mesmo motivo do install, sem deixar um redirecionamento de
        // login substituir o que já está guardado.
        fetch(req).then(r => {
          if (r && r.ok && !r.redirected) caches.open(CACHE_CASCA).then(c => c.put(req, r));
        }).catch(() => {});
        return guardado;
      }
      return fetch(req);
    })());
  }
});
