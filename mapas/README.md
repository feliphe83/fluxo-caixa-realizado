# Mapa de talhões — geometria

O ERP não guarda coordenada nenhuma. O desenho dos talhões vem do shapefile
da topografia (`TALHÕES.shp`), em SIRGAS 2000 / UTM 25S.

`shp2geojson.py` converte para GeoJSON em WGS84, que é o que a tela
`mapa-talhoes.html` consome. Sem GDAL: o .shp de polígonos é um formato
simples e a inversa da Transversa de Mercator tem fórmula fechada.

Para regenerar depois de receber um shapefile novo:

    python3 mapas/shp2geojson.py "mapas/TALHÕES" "src/main/webapp/mapas/talhoes.geojson" 1.0

O último argumento é a tolerância de simplificação em metros (1 m gera ~1,2 MB,
293 KB comprimido; 3 m gera ~780 KB). O script agrupa as feições por
`FAZENDA-ZONA-TALHAO`, que é a mesma chave usada pelo ERP — é ela que liga o
desenho aos dados de situação, variedade, idade e TCH.
