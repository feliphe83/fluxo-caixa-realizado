/* ══════════════════════════════════════════════════════════════════════════
   BALANÇO — como se lê o quadro.

   Fica SEPARADO de balanco-historico.js porque aquele é gerado por
   ferramentas/gerar-balanco.py e reescrito por inteiro a cada regeração.
   Na primeira versão as duas coisas moravam no mesmo arquivo, e a primeira
   vez que rodei o gerador ele apagou estas funções — em silêncio, porque um
   arquivo gerado não avisa o que estava lá antes.

   Dado gerado e código escrito à mão em arquivos diferentes: é a única
   forma de o gerador poder ser rodado sem medo.

   As duas telas (computador e televisão) usam estas funções, para nunca
   desenharem hierarquias diferentes do mesmo documento.
   ══════════════════════════════════════════════════════════════════════════ */

const BAL_ANOS = BALANCO.anos;

/** A chave que o servlet usa nas linhas vivas. */
function balChave(l) { return l.tipo + '|' + l.grupo + '|' + l.nivel + '|' + l.nivel2; }

/**
 * O quadro em uso: os valores fixos, com a coluna do ano corrente trocada
 * pelo que veio do balancete quando ele responde. Começa igual ao histórico
 * — se o Oracle não responder, a tela mostra o último número oficial em vez
 * de um buraco, e diz que é ele.
 */
let BAL_QUADRO = BALANCO.linhas;

function balAplicar(linhas) {
  if (!linhas) { BAL_QUADRO = BALANCO.linhas; return false; }
  BAL_QUADRO = BALANCO.linhas.map(function (l) {
    const v = l.v.slice();
    const n = Number(linhas[balChave(l)]);
    // COM centavos. Quem arredonda é a hora de escrever na tela: arredondar
    // aqui faria o subtotal e o total serem somados sobre valores já
    // cortados, e o total do ativo sairia um mil diferente do documento
    // oficial — 294.296 onde a planilha mostra 294.297.
    if (Number.isFinite(n)) v[0] = n;
    return { tipo: l.tipo, grupo: l.grupo, nivel: l.nivel, nivel2: l.nivel2, v: v };
  });
  return true;
}

/**
 * Monta as linhas de um lado (Ativo ou Passivo) na ordem em que a planilha
 * as mostra: GRUPO, depois NÍVEL, depois os níveis 2, e o total no fim.
 *
 * Grupo, nível e total NÃO vêm de lugar nenhum — são somados aqui a partir
 * dos níveis 2. Guardar subtotal e detalhe separados é como um deixa de
 * bater com o outro sem ninguém ver.
 */
function balMontar(tipo) {
  const linhas = BAL_QUADRO.filter(l => l.tipo === tipo);
  const zero = () => BAL_ANOS.map(() => 0);
  const acumular = (a, b) => a.map((x, i) => x + (Number(b[i]) || 0));

  const porGrupo = new Map(), porNivel = new Map();
  linhas.forEach(l => {
    porGrupo.set(l.grupo, acumular(porGrupo.get(l.grupo) || zero(), l.v));
    const k = l.grupo + '|' + l.nivel;
    porNivel.set(k, acumular(porNivel.get(k) || zero(), l.v));
  });

  const saida = [];
  let grupoAtual = null, nivelAtual = null;
  linhas.forEach(l => {
    if (l.grupo !== grupoAtual) {
      grupoAtual = l.grupo; nivelAtual = null;
      saida.push({ tipo: 'grupo', rotulo: l.grupo, v: porGrupo.get(l.grupo) });
    }
    if (l.nivel !== nivelAtual) {
      nivelAtual = l.nivel;
      // Quando o nível repete o nome do grupo, a planilha mostra as duas
      // linhas mesmo assim. Mantido: é o documento que a diretoria conhece.
      saida.push({ tipo: 'nivel', rotulo: l.nivel, v: porNivel.get(l.grupo + '|' + l.nivel) });
    }
    saida.push({ tipo: 'item', rotulo: l.nivel2, v: l.v });
  });

  saida.push({ tipo: 'total', rotulo: 'Total', v: linhas.reduce((a, l) => acumular(a, l.v), zero()) });
  return saida;
}

function balTotal(lado, i) {
  return BAL_QUADRO.filter(l => l.tipo === lado).reduce((a, l) => a + (Number(l.v[i]) || 0), 0);
}
function balGrupo(lado, grupo, i) {
  return BAL_QUADRO.filter(l => l.tipo === lado && l.grupo === grupo)
                   .reduce((a, l) => a + (Number(l.v[i]) || 0), 0);
}
function balGrupos(lado) {
  return [...new Set(BAL_QUADRO.filter(l => l.tipo === lado).map(l => l.grupo))];
}

/**
 * Onde o balanço NÃO fecha.
 *
 * Um balanço em que ativo e passivo não dão o mesmo total é um fato sobre o
 * documento, não um detalhe de bastidor — e é o primeiro que um contador
 * procura. Nos valores fixos herdados da planilha, 2026, 2025 e 2024 fecham
 * ao centavo e de 2023 para trás não: as diferenças vêm de lá, não da soma
 * feita aqui. Mostrar isso é mais honesto do que deixar quem confere
 * descobrir sozinho e desconfiar da tela inteira.
 *
 * @return [{ano, ativo, passivo, diferenca}] só dos anos que não fecham.
 */
function balNaoFecha() {
  const fora = [];
  BAL_ANOS.forEach(function (ano, i) {
    const a = balTotal('Ativo', i), p = balTotal('Passivo', i);
    if (Math.abs(a - p) >= 1) fora.push({ ano: ano, ativo: a, passivo: p, diferenca: a - p });
  });
  return fora;
}
