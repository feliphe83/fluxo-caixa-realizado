/* ══════════════════════════════════════════════════════════════════
   BALANÇO PATRIMONIAL — estrutura e valores fixos, em R$ mil.

   GERADO por ferramentas/gerar-balanco.py a partir das abas "Ativo"
   e "passivo" de balanco.xlsx. Não edite à mão.

   Fica na raiz do webapp e não em /js/: o AuthFilter libera /js/ sem
   sessão, e o balanço da companhia num endereço público seria um
   vazamento por descuido de organização de pasta.
   ══════════════════════════════════════════════════════════════════ */

const BALANCO = {
 "anos": [
  2026,
  2025,
  2024,
  2023,
  2022,
  2021,
  2020,
  2019,
  2018
 ],
 "datas": [
  "2026-04-30",
  "2025-12-31",
  "2024-12-31",
  "2023-12-31",
  "2022-12-31",
  "2021-12-31",
  "2020-12-31",
  "2019-12-31",
  "2018-12-31"
 ],
 "nivel2DoResultado": "Prejuízos acumulados",
 "linhas": [
  {
   "tipo": "Ativo",
   "grupo": "Ativo  Circulante",
   "nivel": "Ativo  Circulante",
   "nivel2": "Caixa e Equivalentes de Caixa",
   "v": [
    1038.06,
    5300.99,
    2726.85,
    5871.29,
    901.73,
    4492.64,
    7721.53,
    1809.56,
    5667.03
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Ativo  Circulante",
   "nivel": "Ativo  Circulante",
   "nivel2": "Contas a Receber - Clientes",
   "v": [
    5547.97,
    7292.13,
    11537.63,
    4665.38,
    5121.84,
    6075.95,
    6021.62,
    4603.69,
    28561.79
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Ativo  Circulante",
   "nivel": "Ativo  Circulante",
   "nivel2": "Adiantamento a Fornecedores",
   "v": [
    2413.33,
    1832.72,
    3276.82,
    2676.42,
    3488.99,
    4190.26,
    3216.39,
    2792.89,
    10119.59
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Ativo  Circulante",
   "nivel": "Ativo  Circulante",
   "nivel2": "Tributos a Recuperar",
   "v": [
    31179.37,
    35417.01,
    30977.86,
    30044.28,
    26078.63,
    20550.26,
    15372.86,
    10290.21,
    9384.56
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Ativo  Circulante",
   "nivel": "Ativo  Circulante",
   "nivel2": "Outros Créditos",
   "v": [
    327.74,
    196.28,
    763.87,
    22.92,
    150.2,
    172.4,
    340.71,
    258.96,
    228.72
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Ativo  Circulante",
   "nivel": "Ativo  Circulante",
   "nivel2": "Estoques",
   "v": [
    10585.12,
    25946.53,
    30056.87,
    27570.37,
    28253.05,
    30265.06,
    21427.56,
    15134.98,
    6923.64
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Ativo  Circulante",
   "nivel": "Ativo  Circulante",
   "nivel2": "Despesas do Exercício Seguinte",
   "v": [
    6906.59,
    14416.26,
    9305.61,
    47.05,
    54.66,
    9093.54,
    4474.36,
    3443.69,
    3344.32
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Ativo  Circulante",
   "nivel": "Ativo  Circulante",
   "nivel2": "Ativo Biológico",
   "v": [
    28380.46,
    28034.08,
    35758.57,
    41037.29,
    43117.36,
    27558.02,
    16102.51,
    6210.75,
    3433.46
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Ativo  Circulante",
   "nivel": "Ativo  Circulante",
   "nivel2": "Depositos Judiciais",
   "v": [
    12681.46,
    12681.46,
    11926.4,
    19196.66,
    18305.59,
    13426.05,
    13279.39,
    0.0,
    0.0
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Não Circulante",
   "nivel": "Realizável a Longo Prazo",
   "nivel2": "Transações com Partes Relacionadas",
   "v": [
    7133.25,
    7133.25,
    2409.96,
    301.35,
    1020.0,
    8513.4,
    9905.95,
    9905.95,
    9657.24
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Não Circulante",
   "nivel": "Realizável a Longo Prazo",
   "nivel2": "Depositos Judiciais",
   "v": [
    586.05,
    548.03,
    504.3,
    639.84,
    451.19,
    228.59,
    202.59,
    152.8,
    35.17
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Não Circulante",
   "nivel": "Realizável a Longo Prazo",
   "nivel2": "Tributos a Recuperar",
   "v": [
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    7473.38,
    7473.38,
    7473.38
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Não Circulante",
   "nivel": "Realizável a Longo Prazo",
   "nivel2": "Outros Créditos",
   "v": [
    45.71,
    45.71,
    45.71,
    0.0,
    0.0,
    0.0,
    1270.28,
    1270.28,
    1270.28
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Não Circulante",
   "nivel": "Investimentos",
   "nivel2": "Investimentos",
   "v": [
    9540.85,
    19630.32,
    14821.02,
    11375.29,
    9541.0,
    287.63,
    16946.51,
    16946.51,
    16946.51
   ]
  },
  {
   "tipo": "Ativo",
   "grupo": "Não Circulante",
   "nivel": "Imobilizado",
   "nivel2": "Imobilizado",
   "v": [
    177930.67,
    177797.31,
    188499.75,
    158670.3,
    150501.01,
    131400.19,
    113031.69,
    100460.31,
    90783.3
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Passivo Circulante",
   "nivel": "Passivo Circulante",
   "nivel2": "Empréstimos e financiamentos",
   "v": [
    12201.72,
    13106.43,
    1709.83,
    966.12,
    44.16,
    254.82,
    0.0,
    71.38,
    181.73
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Passivo Circulante",
   "nivel": "Passivo Circulante",
   "nivel2": "Fornecedores",
   "v": [
    41696.17,
    49552.03,
    52341.68,
    24287.04,
    26824.09,
    17365.34,
    54414.1,
    50425.46,
    53420.37
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Passivo Circulante",
   "nivel": "Passivo Circulante",
   "nivel2": "Impostos taxas e Contribuições Diversas",
   "v": [
    829.32,
    4773.38,
    4255.85,
    21728.26,
    21798.18,
    139508.89,
    141435.26,
    124223.06,
    117922.38
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Passivo Circulante",
   "nivel": "Passivo Circulante",
   "nivel2": "Salarios e provisão de férias",
   "v": [
    9965.6,
    15028.27,
    14227.13,
    14494.0,
    12918.82,
    10822.07,
    37415.6,
    36603.42,
    33881.99
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Passivo Circulante",
   "nivel": "Passivo Circulante",
   "nivel2": "Parcelamentos Fiscais",
   "v": [
    19795.17,
    15203.46,
    14229.11,
    12160.31,
    11846.26,
    2846.71,
    1843.92,
    1476.06,
    1419.03
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Passivo Circulante",
   "nivel": "Passivo Circulante",
   "nivel2": "Adiantamentos de clientes",
   "v": [
    54358.93,
    53607.43,
    26596.05,
    22429.35,
    18891.01,
    8774.45,
    28975.59,
    12145.48,
    5110.59
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Passivo Circulante",
   "nivel": "Passivo Circulante",
   "nivel2": "Credores sob contrato",
   "v": [
    8507.31,
    3723.47,
    3472.91,
    2612.18,
    75.0,
    75.0,
    1178.74,
    3906.25,
    10205.0
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Passivo Circulante",
   "nivel": "Passivo Circulante",
   "nivel2": "Outras contas a pagar",
   "v": [
    497.6,
    347.97,
    2537.43,
    796.25,
    767.56,
    402.59,
    3692.41,
    5746.54,
    5680.85
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Passivo Circulante",
   "nivel": "Passivo Circulante",
   "nivel2": "Provisão Para Perdas",
   "v": [
    0.0,
    0.0,
    222.3,
    442.03,
    457.17,
    181.07,
    508.93,
    344.87,
    384.42
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Passivo Circulante",
   "nivel": "Passivo Circulante",
   "nivel2": "Dividendos a Distribuir Proposto",
   "v": [
    633.89,
    633.89,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Passivo Circulante",
   "nivel": "Passivo Circulante",
   "nivel2": "Adiantamentos De dividendos",
   "v": [
    15108.03,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Não Circulante",
   "nivel": "Passivo Não Circulante",
   "nivel2": "Empréstimos e financiamentos.",
   "v": [
    12371.1,
    25012.34,
    3800.4,
    1915.37,
    0.0,
    44.16,
    84703.13,
    84703.13,
    84703.13
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Não Circulante",
   "nivel": "Passivo Não Circulante",
   "nivel2": "Fornecedores.",
   "v": [
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    3185.02,
    3185.02,
    3185.02
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Não Circulante",
   "nivel": "Passivo Não Circulante",
   "nivel2": "Cooperativa conta corrente",
   "v": [
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    13341.99,
    13341.99,
    13341.99
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Não Circulante",
   "nivel": "Passivo Não Circulante",
   "nivel2": "Parcelamento fiscais",
   "v": [
    85398.21,
    90423.05,
    88663.41,
    91386.03,
    92661.62,
    7820.19,
    5278.57,
    5723.94,
    6984.53
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Não Circulante",
   "nivel": "Passivo Não Circulante",
   "nivel2": "Provisão para perda de investimentos",
   "v": [
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    16117.19,
    16117.19,
    16117.19,
    16117.19
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Não Circulante",
   "nivel": "Passivo Não Circulante",
   "nivel2": "Credores sob contrato.",
   "v": [
    0.0,
    0.0,
    0.0,
    200.0,
    237.5,
    312.5,
    0.0,
    0.0,
    0.0
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Não Circulante",
   "nivel": "Passivo Não Circulante",
   "nivel2": "Partes relacionadas",
   "v": [
    0.0,
    3403.58,
    78.16,
    78.16,
    78.16,
    78.16,
    2222.71,
    3225.77,
    4133.0
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Não Circulante",
   "nivel": "Passivo Não Circulante",
   "nivel2": "Impostos taxas e Contribuições Diversas.",
   "v": [
    12853.08,
    12853.08,
    12853.08,
    766.15,
    555.99,
    1824.77,
    1226.92,
    1013.47,
    1022.92
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Não Circulante",
   "nivel": "Passivo Não Circulante",
   "nivel2": "Operações Financeiras - NDF",
   "v": [
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Não Circulante",
   "nivel": "Passivo Não Circulante",
   "nivel2": "Credores recuperação judicial",
   "v": [
    35353.05,
    36061.33,
    37309.52,
    44173.63,
    46140.94,
    49120.54,
    0.0,
    0.0,
    0.0
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Não Circulante",
   "nivel": "Passivo Não Circulante",
   "nivel2": "Provisão para Contigências",
   "v": [
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    1203.0
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Não Circulante",
   "nivel": "Passivo Não Circulante",
   "nivel2": "Provisão Para Perdas",
   "v": [
    366.03,
    366.03,
    456.62,
    0.0,
    0.0,
    0.0,
    0.0,
    0.0,
    1203.0
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Patrimônio líquido",
   "nivel": "Patrimônio líquido",
   "nivel2": "Capital social",
   "v": [
    58212.26,
    58212.26,
    58212.26,
    58212.26,
    58212.26,
    58212.26,
    58212.26,
    58212.26,
    58212.26
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Patrimônio líquido",
   "nivel": "Patrimônio líquido",
   "nivel2": "Reserva de lucro",
   "v": [
    21011.58,
    0.0,
    21645.48,
    6679.5,
    0.0,
    5.75,
    5.75,
    5.75,
    5.75
   ]
  },
  {
   "tipo": "Passivo",
   "grupo": "Patrimônio líquido",
   "nivel": "Patrimônio líquido",
   "nivel2": "Prejuízos acumulados",
   "v": [
    -94862.43,
    -46035.93,
    0.0,
    0.0,
    -3510.34,
    -55506.63,
    -215234.89,
    -238358.77,
    -221879.23
   ]
  }
 ]
};
