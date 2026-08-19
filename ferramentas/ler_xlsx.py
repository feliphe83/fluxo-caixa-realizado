#!/usr/bin/env python3
"""
Leitor de .xlsx com a biblioteca padrão — este Mac não tem openpyxl, pandas
nem LibreOffice, e instalar coisa na máquina de alguém para ler um arquivo
não é troca justa. Um .xlsx é um zip de XML, então dá para abrir sem nada.

Trata o que costuma quebrar leitor caseiro:
  - strings compartilhadas (sharedStrings.xml), com <t> dentro de <r> (texto
    formatado em pedaços) que precisam ser concatenados;
  - células vazias, que simplesmente não aparecem no XML — a posição vem da
    referência (A1, B7), não da ordem;
  - booleanos e fórmulas, onde o que interessa é <v>, o valor calculado.
"""
import sys, zipfile, re
import xml.etree.ElementTree as ET

NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
NSR = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'

def coluna_para_indice(ref):
    """'BC7' -> 54 (zero-based). A posição está na referência, não na ordem."""
    letras = re.match(r'([A-Z]+)', ref).group(1)
    n = 0
    for c in letras:
        n = n * 26 + (ord(c) - 64)
    return n - 1

def strings(z):
    if 'xl/sharedStrings.xml' not in z.namelist():
        return []
    raiz = ET.fromstring(z.read('xl/sharedStrings.xml'))
    out = []
    for si in raiz.findall(f'{NS}si'):
        # Texto formatado vem quebrado em vários <r><t>; junta tudo.
        out.append(''.join(t.text or '' for t in si.iter(f'{NS}t')))
    return out

def abas(z):
    raiz = ET.fromstring(z.read('xl/workbook.xml'))
    rels = ET.fromstring(z.read('xl/_rels/workbook.xml.rels'))
    alvo = {r.get('Id'): r.get('Target') for r in rels}
    out = []
    for s in raiz.find(f'{NS}sheets'):
        t = alvo.get(s.get(f'{NSR}id'), '')
        if t.startswith('/xl/'): t = t[1:]
        elif not t.startswith('xl/'): t = 'xl/' + t.lstrip('/')
        out.append((s.get('name'), t))
    return out

def linhas(z, caminho, ss):
    raiz = ET.fromstring(z.read(caminho))
    dados = raiz.find(f'{NS}sheetData')
    if dados is None: return []
    out = []
    for row in dados.findall(f'{NS}row'):
        celulas = {}
        for c in row.findall(f'{NS}c'):
            ref = c.get('r') or ''
            if not ref: continue
            i = coluna_para_indice(ref)
            tipo = c.get('t')
            if tipo == 'inlineStr':
                el = c.find(f'{NS}is')
                v = ''.join(t.text or '' for t in el.iter(f'{NS}t')) if el is not None else ''
            else:
                el = c.find(f'{NS}v')
                v = el.text if el is not None else ''
                if tipo == 's' and v is not None:
                    v = ss[int(v)] if int(v) < len(ss) else ''
            celulas[i] = (v or '').strip()
        if celulas:
            largura = max(celulas) + 1
            out.append([celulas.get(i, '') for i in range(largura)])
        else:
            out.append([])
    return out

def main(caminho, limite=None):
    with zipfile.ZipFile(caminho) as z:
        ss = strings(z)
        for nome, alvo in abas(z):
            ls = linhas(z, alvo, ss)
            print(f'=== ABA "{nome}" — {len(ls)} linhas ===')
            for i, l in enumerate(ls):
                if limite and i >= limite: 
                    print(f'   … mais {len(ls)-limite} linhas'); break
                print('  ' + ' | '.join(l))

if __name__ == '__main__':
    main(sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else None)
