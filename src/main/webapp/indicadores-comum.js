/* ══════════════════════════════════════════════════════════════════════════
   Indicadores econômicos — o que a tela de computador e a de televisão têm
   em comum: formatação, os avisos de idade e a montagem das tabelas.

   Mora num arquivo só porque são duas telas mostrando os MESMOS números.
   Duas cópias de uma tabela de preço divergem na primeira correção, e o
   pior é que divergem em silêncio: as duas continuam somando certo, cada
   uma com o seu número.

   Fica na raiz do webapp e não em /js/: aquele caminho é liberado sem
   sessão pelo AuthFilter, e cotação de compra da usina não é coisa para
   ficar num endereço público.
   ══════════════════════════════════════════════════════════════════════════ */

const esc = s => String(s == null ? '' : s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const fmt = (v, casas) => (Number(v) || 0).toLocaleString('pt-BR',
  { minimumFractionDigits: casas == null ? 2 : casas,
    maximumFractionDigits: casas == null ? 2 : casas });

/**
 * A partir de quanto o preço do açúcar VHP pisca na tela.
 *
 * Veio do painel antigo. É o valor a partir do qual vale a pena parar o que
 * se está fazendo e olhar — numa mesa de venda, o número que autoriza
 * fechar negócio não pode depender de alguém estar olhando na hora certa.
 */
const GATILHO_VHP = 2900;

/**
 * O selo de idade do dado.
 *
 * Toda fonte externa vem embrulhada em {dado, idadeMinutos, erro}. Um painel
 * de mercado sem hora é um painel em que não se pode confiar para decidir
 * preço: se a fonte caiu, o número de antes continua na tela, mas dizendo
 * quando foi buscado.
 */
function idade(env) {
  if (!env) return '';
  if (env.erro && (!env.dado || env.dado === null)) {
    return `<span class="idade erro" title="${esc(env.erro)}">fonte indisponível</span>`;
  }
  if (env.erro) {
    return `<span class="idade erro" title="${esc(env.erro)}">
              fonte fora do ar · mostrando de ${minutos(env.idadeMinutos)}</span>`;
  }
  const m = Number(env.idadeMinutos) || 0;
  if (m <= 1) return '<span class="idade">agora</span>';
  return `<span class="idade ${m > 60 ? 'velho' : ''}">de ${minutos(m)}</span>`;
}

function minutos(m) {
  m = Number(m) || 0;
  if (m < 60) return m + ' min';
  const h = Math.floor(m / 60);
  return h < 24 ? h + 'h' : Math.floor(h / 24) + ' dia(s)';
}

/** "2026-08-20" -> "20/08". */
function rotuloDia(iso) {
  const p = String(iso || '').split('-');
  return p.length === 3 ? p[2] + '/' + p[1] : String(iso || '');
}

/** "202607" -> "jul/26". */
function rotuloAnoMes(anoMes) {
  const nomes = ['jan','fev','mar','abr','mai','jun','jul','ago','set','out','nov','dez'];
  const n = Number(anoMes) || 0, mes = n % 100;
  return (mes >= 1 && mes <= 12) ? nomes[mes-1] + '/' + String(Math.floor(n/100)).slice(-2) : String(anoMes);
}

function semDado(msg) {
  return `<div class="vazio"><b>Sem dado.</b> ${esc(msg || 'A fonte não respondeu.')}</div>`;
}

// ── Tabelas ───────────────────────────────────────────────────────────────

function tabelaDolar(env) {
  const d = env && env.dado;
  if (!d) return semDado(env && env.erro);
  const cl = Number(d.variacao) >= 0 ? 'sobe' : 'desce';
  const seta = Number(d.variacao) >= 0 ? '↑' : '↓';
  return `<table>
    <thead><tr><th>Data</th><th>Último</th><th>Abertura</th><th>Alta</th><th>Baixa</th><th>Variação</th></tr></thead>
    <tbody><tr class="destaque">
      <td>${esc(String(d.data || '').slice(0, 16))}</td>
      <td>${fmt(d.ultimo, 4)}</td><td>${fmt(d.abertura, 4)}</td>
      <td>${fmt(d.alta, 4)}</td><td>${fmt(d.baixa, 4)}</td>
      <td class="${cl}">${seta} ${fmt(Math.abs(d.variacao), 2)}%</td>
    </tr></tbody></table>`;
}

/** As linhas de vencimento do açúcar, seja qual for a forma que o serviço mandou. */
function serieAcucar(env) {
  const d = env && env.dado;
  if (!d) return [];
  const arr = Array.isArray(d) ? d
            : (d.dados_historicos || d.dados_15d || d.dados || []);
  if (!Array.isArray(arr)) return [];
  return arr.map(x => ({
    rotulo: String(x.mes || x.data || '').trim(),
    valor: numeroBR(x.ultimo),
    abertura: numeroBR(x.abertura), maxima: numeroBR(x.maxima),
    minima: numeroBR(x.minima), variacao: numeroBR(x.variacao)
  })).filter(x => x.rotulo && x.valor > 0);
}

/** "18,50" e "18.50" chegam do mesmo serviço conforme o dia. */
function numeroBR(v) {
  if (v == null) return 0;
  if (typeof v === 'number') return v;
  const s = String(v).trim().replace(/\./g, '').replace(',', '.');
  const n = parseFloat(s);
  return isNaN(n) ? 0 : n;
}

/**
 * A série mensal (dólar ou açúcar): [{anoMes, valor|ultimo}] -> rótulo curto.
 * Um ponto por mês, ex.: "jun/26".
 */
function serieMensal(env) {
  const arr = (env && env.dado) || [];
  if (!Array.isArray(arr)) return [];
  return arr.map(x => ({
    rotulo: rotuloAnoMes(x.anoMes),
    valor: Number(x.valor != null ? x.valor : x.ultimo) || 0
  })).filter(x => x.valor > 0);
}

function tabelaAcucar(env) {
  // Só os próximos cinco vencimentos: a lista da bolsa vai longe, mas o que
  // interessa para decidir é a boca da curva.
  const linhas = serieAcucar(env).slice(0, 5);
  if (!linhas.length) return semDado(env && env.erro
    ? env.erro
    : 'A cotação do açúcar ainda não foi coletada.');
  return `<table>
    <thead><tr><th>Vencimento</th><th>Último</th><th>Abertura</th><th>Alta</th><th>Baixa</th><th>Variação</th></tr></thead>
    <tbody>${linhas.map(l => {
      const cl = l.variacao >= 0 ? 'sobe' : 'desce';
      return `<tr>
        <td>${esc(l.rotulo)}</td><td><b>${fmt(l.valor, 2)}</b></td>
        <td>${l.abertura ? fmt(l.abertura, 2) : '—'}</td>
        <td>${l.maxima ? fmt(l.maxima, 2) : '—'}</td>
        <td>${l.minima ? fmt(l.minima, 2) : '—'}</td>
        <td class="${cl}">${l.variacao ? (l.variacao >= 0 ? '↑ ' : '↓ ') + fmt(Math.abs(l.variacao), 2) : '—'}</td>
      </tr>`;
    }).join('')}</tbody></table>`;
}

function tabelaIndicadores(env) {
  const arr = (env && env.dado) || [];
  if (!arr.length) return semDado(env && env.erro);
  return `<table>
    <thead><tr><th>Indicador</th><th>Ref.</th><th>No mês</th><th>No ano</th><th>12 meses</th></tr></thead>
    <tbody>${arr.map(i => `<tr>
      <td><b>${esc(i.nome)}</b></td>
      <td>${esc(rotuloAnoMes(i.anoMes))}</td>
      <td class="${i.noMes < 0 ? 'desce' : ''}">${fmt(i.noMes, 2)}%</td>
      <td class="${i.noAno < 0 ? 'desce' : ''}">${fmt(i.noAno, 2)}%</td>
      <td class="${i.em12Meses < 0 ? 'desce' : ''}"><b>${fmt(i.em12Meses, 2)}%</b></td>
    </tr>`).join('')}</tbody></table>`;
}

function tabelaCana(arr) {
  arr = arr || [];
  if (!arr.length) return semDado('A tabela do CONSECANA não foi preenchida.');
  // Só o líquido (preço do kg de ATR após as deduções legais) — é o que o
  // produtor de fato recebe; o bruto antes das deduções fica de fora.
  return `<table>
    <thead><tr><th>Mês</th><th>Líquido (R$/kg ATR)</th></tr></thead>
    <tbody>${arr.map(c => `<tr class="${/acumulado/i.test(c.mes) ? 'destaque' : ''}">
      <td>${esc(c.mes)}</td><td>${fmt(c.liquido, 4)}</td>
    </tr>`).join('')}</tbody></table>`;
}

/**
 * As notícias de açúcar e dólar, lado a lado.
 *
 * Duas colunas, cada uma com as manchetes de um tema, o veículo e há quanto
 * tempo saíram. No computador o título abre a matéria; na TV ninguém clica,
 * mas o link não atrapalha.
 */
function blocoNoticias(env) {
  const d = env && env.dado;
  if (!d) return semDado(env && env.erro
    ? env.erro : 'As notícias não puderam ser carregadas.');
  return `<div class="noticias-cols">
    <div class="nt-col">
      <h3><span class="pt" style="--cor:var(--serie-2)"></span>Açúcar</h3>
      ${listaNoticias(d.acucar)}
    </div>
    <div class="nt-col">
      <h3><span class="pt" style="--cor:var(--serie-1)"></span>Dólar</h3>
      ${listaNoticias(d.dolar)}
    </div>
  </div>`;
}

function listaNoticias(arr) {
  arr = arr || [];
  if (!arr.length) return `<div class="vazio">Sem manchetes agora.</div>`;
  return arr.map(n => `<a class="nt" href="${esc(linkNoticia(n))}" target="_blank" rel="noopener">
      <div class="nt-tit">${esc(n.titulo)}</div>
      <div class="nt-meta">${esc(n.veiculo)}${n.idadeMinutos != null ? ' · há ' + minutos(n.idadeMinutos) : ''}</div>
    </a>`).join('');
}

/**
 * Para onde o clique leva.
 *
 * O link que o Google Notícias dá no feed é um redirecionador que só o
 * JavaScript deles decodifica — aberto direto, não carrega. Em vez de
 * depender dele, o clique abre uma BUSCA do Google pela manchete e o
 * veículo: o próprio artigo é o primeiro resultado, e isso nunca quebra.
 */
function linkNoticia(n) {
  return 'https://www.google.com/search?q=' +
    encodeURIComponent((n.titulo || '') + ' ' + (n.veiculo || ''));
}

function tabelaProdutos(arr) {
  arr = arr || [];
  if (!arr.length) return semDado('Nenhum preço pôde ser convertido — falta a cotação ou a fonte.');
  return `<table>
    <thead><tr><th>Produto</th><th>Data</th><th>Un.</th><th>Valor (R$)</th></tr></thead>
    <tbody>${arr.map(p => {
      const bate = /VHP/.test(p.produto) && Number(p.valor) > GATILHO_VHP;
      const casas = Number(p.valor) >= 100 ? 2 : 4;
      return `<tr>
        <td><b>${esc(p.produto)}</b></td>
        <td>${esc(p.data)}</td><td>${esc(p.unidade)}</td>
        <td class="${bate ? 'gatilho' : ''}"
            ${bate ? `title="Acima de R$ ${fmt(GATILHO_VHP, 0)}"` : ''}>${fmt(p.valor, casas)}</td>
      </tr>`;
    }).join('')}</tbody></table>`;
}

// ── Os quatro números do topo ─────────────────────────────────────────────

/**
 * Só os CARTÕES, sem a moldura.
 *
 * Quem envolve é cada tela: a de computador insere no fluxo e precisa da
 * div; a de televisão injeta dentro de um elemento que já é a grade. Na
 * primeira versão esta função devolvia a moldura junto, e na TV isso virou
 * uma grade dentro da outra — os quatro cartões espremidos na primeira
 * coluna da grade de fora, com o texto cortado.
 */
function cartoesCapa(d) {
  const dolar = d.dolar && d.dolar.dado;
  const vhp = (d.produtos || []).find(p => /VHP/.test(p.produto));
  const ipca = ((d.indicadores && d.indicadores.dado) || []).find(i => i.nome === 'IPCA');
  const acucar = serieAcucar(d.acucar)[0];

  const cartoes = [
    { rot: 'Dólar comercial', cor: 'var(--serie-1)',
      valor: dolar ? 'R$ ' + fmt(dolar.ultimo, 4) : '—',
      base: dolar
        ? `<span class="${dolar.variacao >= 0 ? 'sobe' : 'desce'}">${dolar.variacao >= 0 ? '↑' : '↓'} ${fmt(Math.abs(dolar.variacao), 2)}%</span> no dia`
        : 'fonte indisponível' },
    { rot: 'Açúcar NY nº 11', cor: 'var(--serie-2)',
      valor: acucar ? fmt(acucar.valor, 2) : '—',
      un: acucar ? '¢/lb' : '',
      base: acucar ? esc(acucar.rotulo) : 'serviço fora do ar' },
    { rot: 'Açúcar VHP · mercado externo', cor: 'var(--serie-6)',
      valor: vhp ? 'R$ ' + fmt(vhp.valor, 2) : '—',
      un: vhp ? '/t' : '',
      base: vhp ? 'convertido pela cotação do dia' : 'precisa da bolsa e do dólar' },
    { rot: 'IPCA em 12 meses', cor: 'var(--serie-4)',
      valor: ipca ? fmt(ipca.em12Meses, 2) + '%' : '—',
      base: ipca ? 'no mês ' + fmt(ipca.noMes, 2) + '% · ref. ' + rotuloAnoMes(ipca.anoMes)
                 : 'fonte indisponível' }
  ];
  return cartoes.map(c => `
    <div class="cartao" style="--cor:${c.cor}">
      <div class="rot">${esc(c.rot)}</div>
      <div class="cifra">${c.valor}${c.un ? `<span class="un">${esc(c.un)}</span>` : ''}</div>
      <div class="base">${c.base}</div>
    </div>`).join('');
}
