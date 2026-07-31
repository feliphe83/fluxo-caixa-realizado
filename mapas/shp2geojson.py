"""Converte o shapefile dos talhões (SIRGAS 2000 / UTM 25S) em GeoJSON WGS84.

Sem GDAL: o .shp de polígonos é um formato simples e a inversa da projeção
Transversa de Mercator tem fórmula fechada. SIRGAS 2000 usa o elipsoide GRS80,
que para efeito de mapa é o mesmo do WGS84 (diferença abaixo de 1 m).
"""
import struct, json, math, sys, os

BASE = sys.argv[1]
SAIDA = sys.argv[2]
TOLERANCIA_M = float(sys.argv[3]) if len(sys.argv) > 3 else 1.0

# ── UTM (GRS80) -> lat/lon ────────────────────────────────────────────────
A = 6378137.0
F = 1 / 298.257222101
E2 = F * (2 - F)
K0 = 0.9996
LON0 = math.radians(-33.0)          # meridiano central da zona 25
FALSE_E, FALSE_N = 500000.0, 10000000.0

E1 = (1 - math.sqrt(1 - E2)) / (1 + math.sqrt(1 - E2))
EP2 = E2 / (1 - E2)

def utm_para_latlon(x, y):
    x -= FALSE_E
    y -= FALSE_N                     # hemisfério sul
    m = y / K0
    mu = m / (A * (1 - E2/4 - 3*E2**2/64 - 5*E2**3/256))
    phi1 = (mu
            + (3*E1/2 - 27*E1**3/32) * math.sin(2*mu)
            + (21*E1**2/16 - 55*E1**4/32) * math.sin(4*mu)
            + (151*E1**3/96) * math.sin(6*mu)
            + (1097*E1**4/512) * math.sin(8*mu))
    s, c, t = math.sin(phi1), math.cos(phi1), math.tan(phi1)
    c1 = EP2 * c**2
    t1 = t**2
    n1 = A / math.sqrt(1 - E2 * s**2)
    r1 = A * (1 - E2) / (1 - E2 * s**2)**1.5
    d = x / (n1 * K0)
    lat = phi1 - (n1 * t / r1) * (d**2/2
            - (5 + 3*t1 + 10*c1 - 4*c1**2 - 9*EP2) * d**4/24
            + (61 + 90*t1 + 298*c1 + 45*t1**2 - 252*EP2 - 3*c1**2) * d**6/720)
    lon = LON0 + (d
            - (1 + 2*t1 + c1) * d**3/6
            + (5 - 2*c1 + 28*t1 - 3*c1**2 + 8*EP2 + 24*t1**2) * d**5/120) / c
    return math.degrees(lon), math.degrees(lat)

# ── .dbf ──────────────────────────────────────────────────────────────────
def ler_dbf(caminho):
    with open(caminho, 'rb') as f:
        h = f.read(32)
        nrec, hlen, rlen = struct.unpack('<I H H', h[4:12])
        campos = []
        while True:
            d = f.read(32)
            if d[0:1] in (b'\r', b''): break
            campos.append((d[0:11].split(b'\x00')[0].decode('utf-8','replace').strip(),
                           d[11:12].decode(), d[16]))
        f.seek(hlen)
        linhas = []
        for _ in range(nrec):
            raw = f.read(rlen)
            if len(raw) < rlen: break
            pos, reg = 1, {}
            for nome, tipo, tam in campos:
                v = raw[pos:pos+tam].decode('utf-8','replace').strip()
                if tipo == 'N' and v:
                    try: v = float(v) if '.' in v else int(v)
                    except ValueError: pass
                reg[nome] = v
                pos += tam
            linhas.append(reg)
        return linhas

# ── .shp ──────────────────────────────────────────────────────────────────
def ler_shp(caminho):
    with open(caminho, 'rb') as f:
        dados = f.read()
    pos, feicoes = 100, []
    while pos < len(dados):
        _num, tam = struct.unpack('>II', dados[pos:pos+8])
        pos += 8
        fim = pos + tam * 2
        tipo = struct.unpack('<I', dados[pos:pos+4])[0]
        if tipo != 5:                       # 5 = Polygon; 0 = nulo
            feicoes.append([]); pos = fim; continue
        nparts, npoints = struct.unpack('<II', dados[pos+36:pos+44])
        p = pos + 44
        partes = list(struct.unpack('<%dI' % nparts, dados[p:p+4*nparts])); p += 4*nparts
        coords = struct.unpack('<%dd' % (2*npoints), dados[p:p+16*npoints])
        aneis = []
        for i, ini in enumerate(partes):
            f_ = partes[i+1] if i+1 < len(partes) else npoints
            aneis.append([(coords[2*j], coords[2*j+1]) for j in range(ini, f_)])
        feicoes.append(aneis)
        pos = fim
    return feicoes

# ── Simplificação (Douglas-Peucker) ───────────────────────────────────────
def dp(pts, tol):
    if len(pts) < 3: return pts
    dmax, idx = 0.0, 0
    x1, y1 = pts[0]; x2, y2 = pts[-1]
    dx, dy = x2-x1, y2-y1
    norma = math.hypot(dx, dy)
    for i in range(1, len(pts)-1):
        x0, y0 = pts[i]
        d = abs(dy*x0 - dx*y0 + x2*y1 - y2*x1)/norma if norma else math.hypot(x0-x1, y0-y1)
        if d > dmax: dmax, idx = d, i
    if dmax > tol:
        return dp(pts[:idx+1], tol)[:-1] + dp(pts[idx:], tol)
    return [pts[0], pts[-1]]

def area_assinada(anel):
    s = 0.0
    for i in range(len(anel)-1):
        s += anel[i][0]*anel[i+1][1] - anel[i+1][0]*anel[i][1]
    return s/2

# ── Conversão ─────────────────────────────────────────────────────────────
atributos = ler_dbf(BASE + '.dbf')
geometrias = ler_shp(BASE + '.shp')
assert len(atributos) == len(geometrias), "dbf e shp com contagens diferentes"

por_talhao = {}
descartadas = 0
for attr, aneis in zip(atributos, geometrias):
    if not aneis: descartadas += 1; continue
    faz, zona, tal = attr.get('FAZENDA'), attr.get('ZONA'), attr.get('TALHAO')
    if faz in ('', None) or tal in ('', None): descartadas += 1; continue
    chave = '%s-%s-%s' % (faz, zona, tal)

    poligonos, atual = [], None
    for anel in aneis:
        simples = dp(anel, TOLERANCIA_M)          # simplifica em metros (UTM)
        if len(simples) < 4: continue
        externo = area_assinada(anel) < 0          # shapefile: externo é horário
        wgs = [utm_para_latlon(x, y) for x, y in simples]
        if wgs[0] != wgs[-1]: wgs.append(wgs[0])
        wgs = [[round(x, 6), round(y, 6)] for x, y in wgs]
        if externo or atual is None:
            atual = [wgs]; poligonos.append(atual)
        else:
            atual.append(wgs)                      # buraco no polígono corrente
    if not poligonos: descartadas += 1; continue

    alvo = por_talhao.setdefault(chave, {
        'chave': chave, 'fazenda': faz, 'zona': zona, 'talhao': tal,
        'nomeFazenda': attr.get('NOME_FAZ') or '', 'areaShape': 0.0, 'poligonos': []})
    alvo['areaShape'] += float(attr.get('AREA_TOTAL') or 0)
    alvo['poligonos'].extend(poligonos)

feats = []
for t in por_talhao.values():
    geom = ({'type': 'Polygon', 'coordinates': t['poligonos'][0]}
            if len(t['poligonos']) == 1
            else {'type': 'MultiPolygon', 'coordinates': t['poligonos']})
    feats.append({'type': 'Feature',
                  'properties': {'chave': t['chave'], 'fazenda': t['fazenda'],
                                 'zona': t['zona'], 'talhao': t['talhao'],
                                 'nomeFazenda': t['nomeFazenda'],
                                 'areaShape': round(t['areaShape'], 2)},
                  'geometry': geom})

fc = {'type': 'FeatureCollection', 'features': feats}
os.makedirs(os.path.dirname(SAIDA), exist_ok=True)
with open(SAIDA, 'w', encoding='utf-8') as f:
    json.dump(fc, f, ensure_ascii=False, separators=(',', ':'))

xs = [c[0] for ft in feats for pol in ([ft['geometry']['coordinates']] if ft['geometry']['type']=='Polygon' else ft['geometry']['coordinates']) for anel in pol for c in anel]
ys = [c[1] for ft in feats for pol in ([ft['geometry']['coordinates']] if ft['geometry']['type']=='Polygon' else ft['geometry']['coordinates']) for anel in pol for c in anel]
print("feições no shapefile : %d" % len(geometrias))
print("talhões no GeoJSON   : %d  (descartadas: %d)" % (len(feats), descartadas))
print("tamanho              : %.1f KB" % (os.path.getsize(SAIDA)/1024))
print("longitude            : %.5f a %.5f" % (min(xs), max(xs)))
print("latitude             : %.5f a %.5f" % (min(ys), max(ys)))
