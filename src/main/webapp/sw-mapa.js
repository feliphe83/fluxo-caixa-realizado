/**
 * Service Worker do Mapa de Talhões — faz a tela funcionar no campo, sem sinal.
 *
 * Três políticas, porque as três coisas têm naturezas diferentes:
 *
 * 1. Casca do app (HTML, Leaflet, GeoJSON dos talhões): rede primeiro, com
 *    tempo limite, e cache como reserva. Com sinal o app abre sempre na
 *    versão atual; sem sinal, ou com sinal ruim, cai na última guardada
 *    depois de poucos segundos em vez de ficar esperando.
 * 2. Dados do ERP (/api/mapa-talhoes): mesma política, com um limite maior —
 *    a consulta bate no Oracle e pode ser lenta legitimamente.
 * 3. Imagens de satélite: cache do que já foi visto, com teto. NÃO baixa a
 *    região inteira em lote — os termos de uso do provedor não permitem
 *    espelhar o acervo. Sem sinal, área não visitada fica sem fundo, e os
 *    talhões continuam desenhados.
 */
const VERSAO = 'mapa-talhoes-v2';
const CACHE_CASCA = VERSAO + '-casca';
const CACHE_DADOS = VERSAO + '-dados';
const CACHE_TILES = VERSAO + '-tiles';

/**
 * Quanto esperar a rede antes de usar o que está guardado. Sem sinal a
 * falha é imediata; o limite existe para o sinal RUIM, que é o caso que
 * trava a tela — o celular fica tentando e a pessoa espera no meio do
 * talhão. Preferimos dado de ontem em 5 segundos a dado de agora em 40.
 */
const LIMITE_CASCA_MS = 5000;
const LIMITE_DADOS_MS = 15000;   // consulta ao Oracle é legitimamente lenta

function comTempoLimite(promessa, ms) {
  return Promise.race([
    promessa,
    new Promise((_, rejeitar) => setTimeout(() => rejeitar(new Error('tempo limite de rede')), ms))
  ]);
}

/** Teto de imagens guardadas — ~22 KB cada. Comporta os ~870 da
 *  pré-carga sobre os talhões mais o que for visitado navegando. */
const MAX_TILES = 4000;

const CASCA = [
  'mapa-talhoes.html',
  'js/leaflet.js',
  'css/leaflet.css',
  'mapas/talhoes.geojson',
  'img/logo.png',
  'img/mapa-talhoes-180.png',
  'img/mapa-talhoes-192.png',
  'img/mapa-talhoes-512.png'
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
        const resp = await comTempoLimite(fetch(req.clone()), LIMITE_DADOS_MS);
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
        // Nunca relançar: um throw aqui vira "FetchEvent.respondWith received
        // an error: TypeError: Load failed" na tela, que não diz nada a quem
        // está no campo. Uma resposta JSON deixa a página tratar sozinha.
        return new Response(JSON.stringify({ ok: false, offline: true,
                              erro: 'Sem conexão e sem cópia guardada desta consulta.' }),
          { status: 503, headers: { 'Content-Type': 'application/json;charset=UTF-8' } });
      }
    })());
    return;
  }

  // ── Casca: rede primeiro ───────────────────────────────────────────────
  if (CASCA.some(p => url.pathname.endsWith(p))) {
    evento.respondWith((async () => {
      try {
        const resp = await comTempoLimite(fetch(req.clone()), LIMITE_CASCA_MS);
        // redirected = sessão expirada, o AuthFilter mandou para o login.
        // Guardar ou exibir isso trocaria o mapa pela tela de login.
        if (resp && resp.ok && !resp.redirected) {
          const c = await caches.open(CACHE_CASCA);
          c.put(req, resp.clone());
          return resp;
        }
        throw new Error('resposta não aproveitável');
      } catch (e) {
        const guardado = await caches.match(req, { ignoreSearch: true });
        if (guardado) return guardado;
        throw e;      // primeira visita, sem rede: não há o que servir
      }
    })());
  }
});
