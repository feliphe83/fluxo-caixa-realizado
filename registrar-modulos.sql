-- Registra os módulos novos no menu (MySQL intranet, no servidor).
-- Admin vê todos os módulos ativos automaticamente; para usuários comuns,
-- ver o bloco de permissões no final.
-- Rode no banco `intranet`. Seguro para rodar de novo (NOT EXISTS evita duplicar).

-- 1) Pagamento de Cana  -> categoria Agrícola
INSERT INTO intranet_modulo (id_categoria, nome, descricao, icone, url_destino, ordem, ativo)
SELECT c.id,
       'Pagamento de Cana',
       'Controle de pagamento a fornecedor de cana — líquido do fechamento x realizado, com levantamento por tonelada',
       'sprout',
       '/fluxo-caixa/pagamento-cana.html',
       COALESCE((SELECT MAX(m.ordem) + 1 FROM intranet_modulo m WHERE m.id_categoria = c.id), 1),
       1
FROM   intranet_categoria c
WHERE  (UPPER(c.nome) LIKE '%AGRICOLA%' OR UPPER(c.nome) LIKE '%AGRÍCOLA%')
  AND  NOT EXISTS (SELECT 1 FROM intranet_modulo m2 WHERE m2.url_destino = '/fluxo-caixa/pagamento-cana.html')
LIMIT 1;

-- 2) Fechamento de Fretes -> categoria Agrícola
INSERT INTO intranet_modulo (id_categoria, nome, descricao, icone, url_destino, ordem, ativo)
SELECT c.id,
       'Fechamento de Fretes',
       'Fechamento de fretes de transporte de pessoal (Depto. Agrícola)',
       'truck',
       '/fluxo-caixa/fechamento-frete.html',
       COALESCE((SELECT MAX(m.ordem) + 1 FROM intranet_modulo m WHERE m.id_categoria = c.id), 1),
       1
FROM   intranet_categoria c
WHERE  (UPPER(c.nome) LIKE '%AGRICOLA%' OR UPPER(c.nome) LIKE '%AGRÍCOLA%')
  AND  NOT EXISTS (SELECT 1 FROM intranet_modulo m2 WHERE m2.url_destino = '/fluxo-caixa/fechamento-frete.html')
LIMIT 1;

-- 3) Mapa Gerencial (JSP em contexto próprio) -> Agrícola
--    Abre em tela cheia via ponte de login (/ir-mapa-gerencial). O próprio menu
--    do Mapa Gerencial tem um botão "< Intranet" para voltar ao Hub.
-- Migra quem foi cadastrado apontando para a página-moldura (iframe):
UPDATE intranet_modulo SET url_destino = '/fluxo-caixa/ir-mapa-gerencial'
 WHERE url_destino = '/fluxo-caixa/mapa-gerencial.html';
INSERT INTO intranet_modulo (id_categoria, nome, descricao, icone, url_destino, ordem, ativo)
SELECT c.id,
       'Mapa Gerencial',
       'Boletim mobile: entrada de cana, frota, indústria, chuva e mais (login unificado)',
       'map',
       '/fluxo-caixa/ir-mapa-gerencial',
       COALESCE((SELECT MAX(m.ordem) + 1 FROM intranet_modulo m WHERE m.id_categoria = c.id), 1),
       1
FROM   intranet_categoria c
WHERE  (UPPER(c.nome) LIKE '%AGRICOLA%' OR UPPER(c.nome) LIKE '%AGRÍCOLA%')
  AND  NOT EXISTS (SELECT 1 FROM intranet_modulo m2 WHERE m2.url_destino = '/fluxo-caixa/ir-mapa-gerencial')
LIMIT 1;

-- 4) Estoque Parado -> categoria de Suprimentos/Compras/Almoxarifado, com
--    fallback pra Administração e, na falta de tudo isso, qualquer categoria
--    (pega a de menor id) — não há como conferir o nome exato das categorias
--    já cadastradas nesta base a partir daqui. Se cair na categoria errada,
--    é só mover em Administração → Módulos (Hub).
INSERT INTO intranet_modulo (id_categoria, nome, descricao, icone, url_destino, ordem, ativo)
SELECT c.id,
       'Estoque Parado',
       'Materiais com estoque sem entrada há mais de 90 dias — gera e baixa PDF/Excel na hora',
       'package',
       '/fluxo-caixa/estoque-parado-relatorio.html',
       COALESCE((SELECT MAX(m.ordem) + 1 FROM intranet_modulo m WHERE m.id_categoria = c.id), 1),
       1
FROM   intranet_categoria c
WHERE  NOT EXISTS (SELECT 1 FROM intranet_modulo m2 WHERE m2.url_destino = '/fluxo-caixa/estoque-parado-relatorio.html')
ORDER BY
       CASE
         WHEN UPPER(c.nome) LIKE '%SUPRIMENTO%'   THEN 0
         WHEN UPPER(c.nome) LIKE '%COMPRA%'       THEN 1
         WHEN UPPER(c.nome) LIKE '%ALMOXARIFADO%' THEN 2
         WHEN UPPER(c.nome) LIKE '%MATERIAL%'     THEN 3
         WHEN UPPER(c.nome) LIKE '%ESTOQUE%'      THEN 4
         WHEN UPPER(c.nome) LIKE '%ADMINISTRA%'   THEN 5
         ELSE 9
       END,
       c.id
LIMIT 1;

-- Conferir:
-- SELECT id, id_categoria, nome, url_destino, ordem, ativo
-- FROM intranet_modulo WHERE url_destino IN ('/fluxo-caixa/pagamento-cana.html','/fluxo-caixa/fechamento-frete.html','/fluxo-caixa/estoque-parado-relatorio.html');

-- (Opcional) Liberar para um usuário NÃO-admin: repita por usuário/módulo.
-- INSERT INTO intranet_permissao_modulo (id_usuario, id_modulo)
-- SELECT :ID_USUARIO, m.id FROM intranet_modulo m
-- WHERE m.url_destino IN ('/fluxo-caixa/pagamento-cana.html','/fluxo-caixa/fechamento-frete.html')
--   AND NOT EXISTS (SELECT 1 FROM intranet_permissao_modulo p
--                   WHERE p.id_usuario = :ID_USUARIO AND p.id_modulo = m.id);
