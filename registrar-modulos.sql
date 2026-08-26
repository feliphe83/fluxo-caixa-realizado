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
--    Aponta para a página-moldura da intranet (mantém o menu lateral à vista);
--    ela embute o Mapa Gerencial num iframe, que passa pela ponte de login.
-- Migra quem já foi cadastrado apontando direto para a ponte:
UPDATE intranet_modulo SET url_destino = '/fluxo-caixa/mapa-gerencial.html'
 WHERE url_destino = '/fluxo-caixa/ir-mapa-gerencial';
INSERT INTO intranet_modulo (id_categoria, nome, descricao, icone, url_destino, ordem, ativo)
SELECT c.id,
       'Mapa Gerencial',
       'Boletim mobile: entrada de cana, frota, indústria, chuva e mais (login unificado)',
       'map',
       '/fluxo-caixa/mapa-gerencial.html',
       COALESCE((SELECT MAX(m.ordem) + 1 FROM intranet_modulo m WHERE m.id_categoria = c.id), 1),
       1
FROM   intranet_categoria c
WHERE  (UPPER(c.nome) LIKE '%AGRICOLA%' OR UPPER(c.nome) LIKE '%AGRÍCOLA%')
  AND  NOT EXISTS (SELECT 1 FROM intranet_modulo m2 WHERE m2.url_destino = '/fluxo-caixa/mapa-gerencial.html')
LIMIT 1;

-- Conferir:
-- SELECT id, id_categoria, nome, url_destino, ordem, ativo
-- FROM intranet_modulo WHERE url_destino IN ('/fluxo-caixa/pagamento-cana.html','/fluxo-caixa/fechamento-frete.html');

-- (Opcional) Liberar para um usuário NÃO-admin: repita por usuário/módulo.
-- INSERT INTO intranet_permissao_modulo (id_usuario, id_modulo)
-- SELECT :ID_USUARIO, m.id FROM intranet_modulo m
-- WHERE m.url_destino IN ('/fluxo-caixa/pagamento-cana.html','/fluxo-caixa/fechamento-frete.html')
--   AND NOT EXISTS (SELECT 1 FROM intranet_permissao_modulo p
--                   WHERE p.id_usuario = :ID_USUARIO AND p.id_modulo = m.id);
