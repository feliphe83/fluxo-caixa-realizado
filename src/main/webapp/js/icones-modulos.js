/**
 * Ícones dos módulos — fonte única para o hub e para o menu lateral.
 *
 * Antes cada um tinha a sua tabela com quatro ícones, e tudo o que não
 * estivesse nela virava a mesma caixa. Com quinze módulos, a tela era uma
 * parede de caixas iguais: a cor e o desenho não ajudavam a achar nada, só
 * ocupavam espaço.
 *
 * Quando o módulo não traz ícone cadastrado, ele é deduzido do nome e do
 * endereço. É um palpite, mas um palpite informado erra menos que a caixa
 * genérica — e cadastrar o ícone no admin continua vencendo o palpite.
 *
 * Uso: <script src="js/icones-modulos.js"></script> ANTES de quem precisa
 * (sem defer, para já estar disponível).
 */
(function (global) {
  'use strict';

  // Só o miolo do <svg>: o invólucro é montado por svg(), que decide o
  // tamanho conforme quem está desenhando.
  var CORPOS = {
    'dollar-sign': '<line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/>',
    'package':     '<path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/>',
    'trending-up': '<polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/>',
    'users':       '<path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>',
    'user':        '<path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/>',
    'map':         '<polygon points="1 6 1 22 8 18 16 22 23 18 23 2 16 6 8 2 1 6"/><line x1="8" y1="2" x2="8" y2="18"/><line x1="16" y1="6" x2="16" y2="22"/>',
    'bar-chart':   '<line x1="12" y1="20" x2="12" y2="10"/><line x1="18" y1="20" x2="18" y2="4"/><line x1="6" y1="20" x2="6" y2="16"/>',
    'droplet':     '<path d="M12 2.69l5.66 5.66a8 8 0 11-11.31 0z"/>',
    'truck':       '<rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/>',
    'clipboard':   '<path d="M16 4h2a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V6a2 2 0 012-2h2"/><rect x="8" y="2" width="8" height="4" rx="1"/>',
    'alert':       '<polygon points="7.86 2 16.14 2 22 7.86 22 16.14 16.14 22 7.86 22 2 16.14 2 7.86 7.86 2"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>',
    'cart':        '<circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.68 13.39a2 2 0 002 1.61h9.72a2 2 0 002-1.61L23 6H6"/>',
    'file-text':   '<path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>',
    'settings':    '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.6a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9c.14.36.46.63.85.74H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/>',
    'message':     '<path d="M21 11.5a8.38 8.38 0 01-.9 3.8 8.5 8.5 0 01-7.6 4.7 8.38 8.38 0 01-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 01-.9-3.8 8.5 8.5 0 014.7-7.6 8.38 8.38 0 013.8-.9h.5a8.48 8.48 0 018 8v.5z"/>',
    'grid':        '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
    'sprout':      '<path d="M12 22V11"/><path d="M12 11C12 7.5 9.3 5 5.5 5 5.5 8.5 8.2 11 12 11z"/><path d="M12 11c0-3 2.3-5.5 5.5-5.5 0 3-2.3 5.5-5.5 5.5z"/>',
    // Não é ícone de módulo — é a seta do cartão. Fica aqui para quem desenha
    // não precisar montar SVG na mão.
    'chevron':     '<polyline points="9 18 15 12 9 6"/>'
  };

  // Palavra no nome ou no endereço -> ícone. A ordem importa: a primeira que
  // casar vence, então o mais específico vem antes. "cana" antes de
  // "dashboard" faz "Entrada de Cana" virar safra e não gráfico genérico.
  var PISTAS = [
    [/manobra|bdo|boletim/,                                                    'truck'],
    [/combust|diesel|abastec/,                                                 'droplet'],
    [/parada|moagem/,                                                          'alert'],
    [/mapa|talh|geo/,                                                          'map'],
    [/cana|safra|colheita|plantio|moenda/,                                     'sprout'],
    [/agric|producao|produtiv/,                                                'trending-up'],
    [/servic|apontamento|obra/,                                                'clipboard'],
    [/compra|cota|fornecedor|oc\b/,                                            'cart'],
    [/contrato|parcela|nota|nf\b|document/,                                    'file-text'],
    [/caixa|financ|banco|pagar|receber/,                                       'dollar-sign'],
    [/folha|rh\b|funcion|pessoa|vaga|candidat|contrata|solicita|ficha|admiss/, 'users'],
    [/conta|senha|perfil/,                                                     'user'],
    [/admin|permiss|config|parametr/,                                          'settings'],
    [/chat|alfredo|whats|mensagem/,                                            'message'],
    [/dashboard|painel|relat|indicador/,                                       'bar-chart']
  ];

  function svg(nome, tamanho) {
    var corpo = CORPOS[nome] || CORPOS['grid'];
    return '<svg width="' + tamanho + '" height="' + tamanho + '" fill="none" viewBox="0 0 24 24" '
         + 'stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">'
         + corpo + '</svg>';
  }

  // Os quatro que existiam antes de haver escolha. Um módulo cadastrado com
  // um destes não teve o ícone escolhido — teve o que sobrou, porque a lista
  // inteira eram esses quatro. Por isso a dedução passa na frente deles.
  var LEGADO = { 'dollar-sign': 1, 'package': 1, 'trending-up': 1, 'users': 1 };

  /**
   * Ícone do módulo, nesta ordem:
   *
   * 1. o cadastrado no admin, se for FORA do conjunto antigo — aí alguém
   *    escolheu de verdade, e escolha de gente vence palpite de código;
   * 2. o deduzido do nome e do endereço;
   * 3. o cadastrado antigo, se não deu para deduzir nada;
   * 4. o genérico.
   */
  function nomeDoModulo(mod) {
    var cadastrado = mod && mod.icone && CORPOS[mod.icone] ? mod.icone : null;
    if (cadastrado && !LEGADO[cadastrado]) return cadastrado;

    var texto = (((mod && mod.nome) || '') + ' ' + ((mod && mod.urlDestino) || ''))
      .normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    for (var i = 0; i < PISTAS.length; i++) {
      if (PISTAS[i][0].test(texto)) return PISTAS[i][1];
    }
    return cadastrado || 'grid';
  }

  /** SVG pronto para o módulo, no tamanho pedido. */
  function doModulo(mod, tamanho) {
    return svg(nomeDoModulo(mod), tamanho || 18);
  }

  global.IconesModulos = { svg: svg, doModulo: doModulo, nomeDoModulo: nomeDoModulo };
})(window);
