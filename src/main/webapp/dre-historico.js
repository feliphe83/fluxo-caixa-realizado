/* ═════════════════════════════════════════════════════════════════════════
   DRE — os valores fixos, num arquivo só.

   Mora AQUI e não dentro de cada tela porque são duas telas mostrando o
   mesmo demonstrativo: a de computador e a de televisão. Duas cópias de
   números de DRE divergem em alguma correção futura, e o pior é que
   divergem em silêncio — as duas continuam somando certo, cada uma com o
   seu número.

   E mora na raiz do webapp, não em /js/: o AuthFilter libera /js/ sem
   sessão, e o resultado da companhia num endereço público seria um vazamento
   por descuido de organização de pasta.
   ═════════════════════════════════════════════════════════════════════════ */

/*
   DRE — VALORES FIXOS DE 2018 A 2025, em R$ mil.

   Transcritos da planilha da controladoria como ela mostra, inclusive onde
   a apresentação diverge do sinal contábil: nas colunas de 2018, 2019 e
   2020 os custos e as despesas aparecem positivos, e alguns subtotais
   aparecem sem parênteses mesmo sendo negativos. Não "consertei" nada —
   este painel tem que bater com o documento que a diretoria já conhece, e
   um número diferente do que está no relatório oficial vira discussão sobre
   qual dos dois está certo em vez de sobre o resultado.

   Conferência que fiz linha a linha antes de fixar: receita líquida =
   bruta − tributos; lucro bruto = líquida − custos − ociosidade; despesas
   operacionais = a soma dos cinco detalhes; resultado operacional = lucro
   bruto + despesas; antes do IRPJ = operacional + financeiro. Fecha nos
   nove anos, o que dá confiança de que a transcrição não trocou nenhuma
   coluna de lugar.

   O ano corrente vem daqui também, como base — e é substituído assim que o
   de/para entre conta contábil e linha do DRE existir (ver a nota no fim
   da tela).
 */

const ANOS = [2026, 2025, 2024, 2023, 2022, 2021, 2020, 2019, 2018];

const DRE = [
  { chave:'receita_bruta', rotulo:'(+) RECEITA BRUTA DE VENDAS', tipo:'destaque',
    v:[ 66783, 183978, 325769, 352873, 271389, 237371, 176025, 100978,  88411] },
  { chave:'tributos', rotulo:'(-) TRIBUTOS SOBRE VENDAS', tipo:'item',
    v:[  -237, -10820,  -2228,   -504,  -2584,   -577,    818,   6637,   4297] },
  { chave:'receita_liquida', rotulo:'(=) RECEITA LÍQUIDA DE VENDAS', tipo:'destaque',
    v:[ 66546, 173157, 323541, 352370, 268805, 236794, 175206,  94341,  84114] },
  { chave:'cpv', rotulo:'(-) CUSTOS DOS PRODUTOS VENDIDOS', tipo:'item',
    v:[-79975,-197381,-275281,-257479,-196795,-167565, 117128,  92699,  87654] },
  { chave:'ociosidade', rotulo:'(-) CUSTOS DE OCIOSIDADE', tipo:'item',
    v:[  null,   null,   null, -36063, -27967,   null,   null,   null,   null] },
  { chave:'lucro_bruto', rotulo:'(=) LUCRO BRUTO', tipo:'destaque',
    v:[-13429, -24224,  48260,  58828,  44042,  69229,  58078,   1642,   3540] },
  { chave:'despesas_op', rotulo:'(-) DESPESAS E OUTRAS RECEITAS OPERACIONAIS', tipo:'destaque',
    v:[-10487, -21197, -22733, -36151,  -1465, 127493,  19604,  13753,   2162] },
  { chave:'desp_vendas', rotulo:'DESPESAS COM VENDAS', tipo:'sub',
    v:[ -2896, -10072, -11605, -13405,  -8936,  -6556,   7267,   4364,   4072] },
  { chave:'desp_admin', rotulo:'DESPESAS GERAIS E ADMINISTRATIVAS', tipo:'sub',
    v:[ -7857, -23424, -28715, -24353, -22928, -18193,  13888,  12700,  18926] },
  { chave:'outras_op', rotulo:'OUTRAS (RECEITAS)/ DESPESAS OPERACIONAIS', tipo:'sub',
    v:[   435,    601,   5061,   -227,   5923,  14091,  -1551,  -3311, -20836] },
  { chave:'equivalencia', rotulo:'RESULTADO DE EQUIVALÊNCIA PATRIMONIAL', tipo:'sub',
    v:[  null,   4850,   3446,   1834,   null,   null,   null,   null,   null] },
  { chave:'nao_recorrente', rotulo:'DESPESAS/RECEITAS NÃO RECORRENTE', tipo:'sub',
    v:[  -169,   6849,   9080,   null,  24477, 138152,   null,   null,   null] },
  { chave:'resultado_op', rotulo:'(=) RESULTADO OPERACIONAL ANTES DO RESULTADO FINANCEIRO', tipo:'destaque',
    v:[-23916, -45421,  25526,  22676,  42578, 196722,  38474,  12111,   5702] },
  { chave:'financeiro', rotulo:'RESULTADO FINANCEIRO LÍQUIDO', tipo:'destaque',
    v:[ -3899, -13174, -10560, -12487,  -6704,  -6362,  15204,   4369,   4000] },
  { chave:'antes_ir', rotulo:'RESULTADO ANTES DO IRPJ E CSLL', tipo:'destaque',
    v:[-27815, -58595,  14966,  10190,  35873, 190360,  23270, -16480,  -9702] },
  { chave:'ir_csll', rotulo:'IMPOSTO DE RENDA E CONTRIBUIÇÃO SOCIAL', tipo:'destaque',
    v:[  null,   null,   null,   null,   null,    728,    147,   null,   null] },
  { chave:'resultado_liquido', rotulo:'RESULTADO LÍQUIDO', tipo:'destaque',
    v:[-27815, -58595,  14966,  10190,  35873, 167202,  23123, -16480,  -9702] },
  { chave:'resultado_ajustado', rotulo:'RESULTADO LÍQUIDO - AJUSTADO', tipo:'destaque',
    v:[-27646, -65444,   5886,  10190,  11396,  29050,  23123, -16480,  -9702] }
];

