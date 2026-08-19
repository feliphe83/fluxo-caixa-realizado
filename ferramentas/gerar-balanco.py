#!/usr/bin/env python3
"""
Gera, de balanco.xlsx:
  src/main/resources/balanco-indice.csv   conta -> tipo;grupo;nivel;nivel 2
  src/main/webapp/balanco-historico.js    a estrutura e os 9 anos fixos

    python3 ferramentas/gerar-balanco.py balanco.xlsx

O DE/PARA PRECISA DO PREFIXO DA CONTA, e não só do rótulo. "Tributos a
Recuperar", "Outros Créditos", "Depositos Judiciais", "Impostos taxas e
Contribuições Diversas" e "Credores sob contrato" existem duas vezes no
balanço — uma no circulante e outra no longo prazo — com o MESMO nome. Só o
rótulo não decide, e chutar um dos dois joga o saldo inteiro no lado errado
sem o total denunciar, porque ele fecha do mesmo jeito.

Quem decide é o começo da conta:
    1.1 circulante · 1.2 longo prazo · 1.3 permanente
    2.1 circulante · 2.2 longo prazo · 2.4 patrimônio líquido
"""
import sys, os, zipfile, collections, datetime, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ler_xlsx import strings, abas, linhas

RAIZ = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))

# prefixo -> o nível a que ele pertence, para desempatar rótulo repetido
NIVEL_DO_PREFIXO = {
    '1.1': 'Ativo  Circulante',
    '1.2': 'Realizável a Longo Prazo',
    '1.3': None,                      # Investimentos x Imobilizado: o rótulo já separa
    '2.1': 'Passivo Circulante',
    '2.2': 'Passivo Não Circulante',
    '2.4': 'Patrimônio líquido',
}

# Um caso que nem o rótulo nem o prefixo de dois níveis resolvem, e que a
# conta sintética do balancete resolve sozinha: 2.1.2.15 é "CONTRATOS DE
# MUTUO / EMPRESTIMO", saldo -8.507.306,32 — exatamente a linha "Credores
# sob contrato" do circulante na planilha. Duas contas ali chamam-se
# "Raizen" no índice, nome que também existe sob "Empréstimos e
# financiamentos"; pelo rótulo elas iriam para a linha errada.
#
# Regra por PREFIXO, e não por conta: conta nova dentro de 2.1.2.15 nasce no
# lugar certo em vez de parar o script.
NIVEL2_POR_PREFIXO = {
    '2.1.2.15': ('Passivo', 'Passivo Circulante', 'Passivo Circulante', 'Credores sob contrato'),
}

# A conta 3 (resultado do exercício) não é mapeada conta a conta — o índice
# marca todo o grupo 3 como "NÃO UTILIZAR". Mas o resultado do ano compõe o
# patrimônio líquido, e sem ele o balanço não fecha: confere exatamente a
# diferença de "Prejuízos acumulados" na planilha.
NIVEL2_DO_RESULTADO = 'Prejuízos acumulados'

def num(x):
    try: return float(x)
    except (ValueError, TypeError): return 0.0

def serial_para_data(s):
    return datetime.date(1899, 12, 30) + datetime.timedelta(days=int(float(s)))

def main(caminho):
    z = zipfile.ZipFile(caminho); ss = strings(z)
    M = {n: linhas(z, t, ss) for n, t in abas(z)}

    cab = M['Ativo'][0]
    datas = [serial_para_data(s) for s in cab[10:] if s]
    anos = [d.year for d in datas]

    # ── estrutura e valores fixos, agregados no NÍVEL 2 ──
    ordem, valores, candidatos = [], collections.defaultdict(lambda: [0.0]*len(anos)), collections.defaultdict(list)
    for aba in ('Ativo', 'passivo'):
        for l in M[aba][1:]:
            if len(l) < 11 or not l[7].strip(): continue
            chave = (l[1].strip(), l[3].strip(), l[5].strip(), l[7].strip())
            if chave not in ordem: ordem.append(chave)
            for i in range(len(anos)):
                valores[chave][i] += num(l[10+i]) if 10+i < len(l) else 0.0
            for rot in (l[9].strip(), l[7].strip()):
                if rot and chave not in candidatos[rot]: candidatos[rot].append(chave)

    # ── conta -> nível 2, desempatando pelo prefixo ──
    idx = {l[0].strip(): l[3].strip() for l in M['indice']
           if len(l) > 3 and l[2] and l[2] != 'S' and l[0].count('.') == 4}
    resolvido, ambiguo = {}, []
    for conta, rot in idx.items():
        if rot == 'NÃO UTILIZAR': continue
        forcado = next((v for pref, v in NIVEL2_POR_PREFIXO.items()
                        if conta.startswith(pref + '.')), None)
        if forcado: resolvido[conta] = forcado; continue
        cands = candidatos.get(rot, [])
        if not cands: ambiguo.append((conta, rot, 'sem lugar na estrutura')); continue
        if len(cands) == 1:
            resolvido[conta] = cands[0]; continue
        nivel = NIVEL_DO_PREFIXO.get('.'.join(conta.split('.')[:2]))
        escolha = [c for c in cands if c[2] == nivel] if nivel else []
        if len(escolha) != 1:
            ambiguo.append((conta, rot, f'{len(cands)} candidatos, prefixo não decidiu')); continue
        resolvido[conta] = escolha[0]

    if ambiguo:
        print('CONTAS QUE NÃO FECHARAM — pare e resolva:', file=sys.stderr)
        for c, r, m in ambiguo[:20]: print(f'  {c}  {r!r}  {m}', file=sys.stderr)
        raise SystemExit(1)

    destino = os.path.join(RAIZ, 'src', 'main', 'resources', 'balanco-indice.csv')
    with open(destino, 'w', encoding='utf-8') as f:
        f.write('# GERADO por ferramentas/gerar-balanco.py a partir de balanco.xlsx.\n'
                '# Não edite à mão: rode o script de novo.\n'
                '#\n'
                '# Formato: conta;tipo;grupo;nivel;nivel 2\n'
                '#\n'
                '# O rótulo do índice sozinho não resolve: cinco nomes existem duas\n'
                '# vezes no balanço (circulante e longo prazo). O prefixo da conta é\n'
                '# que decide, e a resolução já vem feita aqui.\n'
                f'# resultado do exercício (grupo 3) -> {NIVEL2_DO_RESULTADO}\n')
        for conta in sorted(resolvido):
            t, g, n, n2 = resolvido[conta]
            f.write(f'{conta};{t};{g};{n};{n2}\n')

    # ── histórico fixo para a tela ──
    js = {'anos': anos,
          'datas': [d.isoformat() for d in datas],
          'nivel2DoResultado': NIVEL2_DO_RESULTADO,
          'linhas': [{'tipo': t, 'grupo': g, 'nivel': n, 'nivel2': n2,
                      # Duas casas, e nao inteiro: o subtotal e o total sao
                      # somados a partir daqui. Arredondando cada linha antes,
                      # o total do ativo saia 294.296 onde a planilha mostra
                      # 294.297 -- um real de mil, mas num balanco a conferencia
                      # e feita justamente batendo o total contra o documento.
                      'v': [round(x, 2) for x in valores[(t, g, n, n2)]]}
                     for (t, g, n, n2) in ordem if n2]}
    saida = os.path.join(RAIZ, 'src', 'main', 'webapp', 'balanco-historico.js')
    with open(saida, 'w', encoding='utf-8') as f:
        f.write('/* ══════════════════════════════════════════════════════════════════\n'
                '   BALANÇO PATRIMONIAL — estrutura e valores fixos, em R$ mil.\n'
                '\n'
                '   GERADO por ferramentas/gerar-balanco.py a partir das abas "Ativo"\n'
                '   e "passivo" de balanco.xlsx. Não edite à mão.\n'
                '\n'
                '   Fica na raiz do webapp e não em /js/: o AuthFilter libera /js/ sem\n'
                '   sessão, e o balanço da companhia num endereço público seria um\n'
                '   vazamento por descuido de organização de pasta.\n'
                '   ══════════════════════════════════════════════════════════════════ */\n\n'
                'const BALANCO = ' + json.dumps(js, ensure_ascii=False, indent=1) + ';\n')

    print(f'{len(resolvido)} contas -> {os.path.relpath(destino, RAIZ)}')
    print(f'{len(js["linhas"])} linhas de nível 2 -> {os.path.relpath(saida, RAIZ)}')
    print('anos:', anos)
    print('data da coluna corrente:', datas[0])

if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'balanco.xlsx')
