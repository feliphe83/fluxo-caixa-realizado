#!/usr/bin/env python3
"""
Regenera src/main/resources/dre-indice.csv a partir da planilha da
controladoria (aba "indice" de deparacontabil1.xlsx).

    python3 ferramentas/gerar-dre-indice.py deparacontabil1.xlsx

Existe porque o de/para vai mudar: conta nova no plano, linha reclassificada.
Sem este script, atualizar o mapa vira trabalho manual de 232 linhas, e
trabalho manual em de/para contábil erra em silêncio — a conta cai na linha
vizinha e o total continua fechando.

Só contas ANALÍTICAS entram (a coluna 3 traz 'S' nas sintéticas). Sintética é
o somatório das filhas: somar as duas contaria cada real duas vezes.
"""
import sys, os, zipfile, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ler_xlsx import strings, abas, linhas

# rótulo como está na planilha -> chave usada em webapp/dre-historico.js
CHAVE = {
    '(+) RECEITA BRUTA DE VENDAS':              'receita_bruta',
    '(-)TRIBUTOS SOBRE VENDAS':                 'tributos',
    '(-)CUSTOS DOS PRODUTOS VENDIDOS':          'cpv',
    'CUSTO DE OCIOSIDADE':                      'ociosidade',
    'DESPESAS COM VENDAS':                      'desp_vendas',
    'DESPESAS GERAIS E ADMINISTRATIVAS':        'desp_admin',
    'OUTRAS (RECEITAS)/ DESPESAS OPERACIONAIS': 'outras_op',
    'RESULTADO DE EQUIVALÊNCIA PATRIMONIAL':    'equivalencia',
    'DESPESAS/RECEITAS Ñ RECORRENTE':           'nao_recorrente',
    'RESULTADO FINANCEIRO LÍQUIDO':             'financeiro',
    # 3.2.3 — apropria-se ao custo e se anula. Fica mapeada de propósito,
    # para o servlet poder afirmar que nenhuma conta ficou sem destino.
    'CUSTO':                                    'apropriacao',
}

CABECALHO = """# De/para entre conta contábil e linha do Demonstrativo do Resultado.
#
# GERADO por ferramentas/gerar-dre-indice.py a partir da aba "indice" de
# deparacontabil1.xlsx. Não edite à mão: rode o script de novo.
#
# Formato: conta;chave;descrição da conta;rótulo original.
#
# A chave é a mesma de webapp/dre-historico.js, para a linha do balancete e
# a linha da tela serem a mesma coisa nos dois lugares.
#
# Só contas ANALÍTICAS. As sintéticas são somatórios das filhas.
"""

def main(caminho):
    z = zipfile.ZipFile(caminho)
    ss = strings(z)
    idx = None
    for nome, alvo in abas(z):
        if nome.strip().lower() == 'indice':
            idx = linhas(z, alvo, ss)
    if idx is None:
        raise SystemExit('a planilha não tem uma aba chamada "indice"')

    saida, faltando = [], collections.Counter()
    for l in idx:
        if len(l) > 3 and l[2] and l[2] != 'S':
            rot = l[3].strip()
            ch = CHAVE.get(rot)
            if not ch:
                faltando[rot] += 1
                continue
            saida.append((l[0].strip(), ch, l[1].strip(), rot))

    if faltando:
        # Rótulo desconhecido = contas que sumiriam do demonstrativo sem aviso.
        # Melhor parar aqui do que gerar um mapa incompleto que parece certo.
        print('RÓTULOS SEM CHAVE — acrescente-os ao dicionário CHAVE:', file=sys.stderr)
        for r, n in faltando.most_common():
            print(f'  {r!r}  ({n} contas)', file=sys.stderr)
        raise SystemExit(1)

    destino = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                           '..', 'src', 'main', 'resources', 'dre-indice.csv')
    with open(destino, 'w', encoding='utf-8') as f:
        f.write(CABECALHO)
        for c, ch, desc, rot in saida:
            f.write(f'{c};{ch};{desc};{rot}\n')

    print(f'{len(saida)} contas gravadas em {os.path.normpath(destino)}')
    for ch, n in collections.Counter(x[1] for x in saida).most_common():
        print(f'  {ch:<16} {n:>3}')

if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'deparacontabil1.xlsx')
