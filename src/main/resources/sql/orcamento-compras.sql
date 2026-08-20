-- Orçamento de compras: planejado x realizado, por grupo de empenho e empenho.
--
-- Base: a consulta ENTREGUE PELO USUÁRIO, que devolvia só os três totais
-- (orçado, realizado, diferença). O que mudou aqui, e nada além disso:
--
--  1. cada perna deixou de agregar sozinha (nvl(sum(lc.valor),0)) e passou a
--     devolver a linha crua. Quem soma é o SELECT de fora, agora agrupando
--     por grupo de empenho e empenho — é isso que permite a tela abrir o
--     grupo e mostrar os empenhos por dentro. A soma total continua a mesma:
--     somar por partes e somar tudo dá no mesmo;
--  2. entraram as colunas de descrição do grupo e do empenho, e o negócio;
--  3. o anomes e o filtro de negócio viraram marcadores.
--
-- As quatro pernas são as do original e continuam do jeito que estavam:
-- orçamento em custo.lancamento_custo (para as filiais que ainda não têm
-- atualização de início de produção), orçamento em material.realizado para as
-- que têm, orçamento lançado à mão (geradomanual = 'M') e o realizado.
select tmp.cod_negocio
     , tmp.negocio
     , tmp.cod_grupoempenho
     , tmp.grupo
     , tmp.cod_empenho
     , tmp.empenho
     , sum(tmp.vlr_orcado)                        orcado
     , sum(tmp.vlr_realizado)                     realizado
     , sum(tmp.vlr_orcado - tmp.vlr_realizado)    diferenca
from ( select %NEG_COD%                           cod_negocio
            , %NEG_DESC%                          negocio
            , grupoempenho.cod_grupoempenho
            , grupoempenho.descricao              grupo
            , empenho.cod_empenho
            , empenho.descricao                   empenho
            , nvl(lc.valor,0)                     vlr_orcado
            , 0                                   vlr_realizado
       from   custo.lancamento_custo lc
            , custo.empenho
            , custo.grupoempenho
       where  lc.tipo                        = 'O'
       and   (select count(*)
              from   geral.atualizacaoinicioproducao
              where  to_char(atualizacaoinicioproducao.datainicio,'YYYYMM')  <= %ANOMES_FIM%
              and    atualizacaoinicioproducao.cod_grupoempresa               = lc.cod_grupoempresa
              and    atualizacaoinicioproducao.cod_empresa                    = lc.cod_empresa
              and    atualizacaoinicioproducao.cod_filial                     = lc.cod_filial
              and    atualizacaoinicioproducao.cod_atualizacao                = 16 ) = 0
       and    lc.anomes                     >= %ANOMES_INI%
       and    lc.anomes                     <= %ANOMES_FIM%
       and    lc.cod_filial                  = 1
       and    lc.cod_empresa                 = 1
       and    lc.cod_grupoempresa            = 1
       and    grupoempenho.cod_grupoempenho  = empenho.cod_grupoempenho
       and    empenho.cod_empenho            = lc.cod_empenho
       and    empenho.cod_tipoempenho        in (1, 2, 6, 8, 9, 11, 14)
       and    empenho.orcamento_material     = 'S'

       union  all

       select %NEG_COD%                           cod_negocio
            , %NEG_DESC%                          negocio
            , grupoempenho.cod_grupoempenho
            , grupoempenho.descricao              grupo
            , empenho.cod_empenho
            , empenho.descricao                   empenho
            , nvl(lc.valor,0)                     vlr_orcado
            , 0                                   vlr_realizado
       from   geral.atualizacaoinicioproducao
            , material.realizado lc
            , custo.empenho
            , custo.grupoempenho
       where  to_char(atualizacaoinicioproducao.datainicio,'YYYYMM') <= %ANOMES_FIM%
       and    atualizacaoinicioproducao.cod_grupoempresa              = lc.cod_grupoempresa
       and    atualizacaoinicioproducao.cod_empresa                   = lc.cod_empresa
       and    atualizacaoinicioproducao.cod_filial                    = lc.cod_filial
       and    atualizacaoinicioproducao.cod_atualizacao               = 16
       and    lc.tipo                                                 = 'O'
       and    nvl(lc.geradomanual, 'G')                               = 'G'
       and    lc.anomes                                              >= %ANOMES_INI%
       and    lc.anomes                                              <= %ANOMES_FIM%
       and    lc.cod_filial                                           = 1
       and    lc.cod_empresa                                          = 1
       and    lc.cod_grupoempresa                                     = 1
       and    grupoempenho.cod_grupoempenho                           = empenho.cod_grupoempenho
       and    empenho.cod_empenho                                     = lc.cod_empenho
       and    empenho.cod_tipoempenho        in (1, 2, 6, 8, 9, 11, 14)
       and    empenho.orcamento_material                              = 'S'

       union  all

       select %NEG_COD%                           cod_negocio
            , %NEG_DESC%                          negocio
            , grupoempenho.cod_grupoempenho
            , grupoempenho.descricao              grupo
            , empenho.cod_empenho
            , empenho.descricao                   empenho
            , nvl(lc.valor,0)                     vlr_orcado
            , 0                                   vlr_realizado
       from   material.realizado lc
            , custo.empenho
            , custo.grupoempenho
       where  grupoempenho.cod_grupoempenho = empenho.cod_grupoempenho
       and    empenho.cod_tipoempenho        in (1, 2, 6, 8, 9, 11, 14)
       and    empenho.orcamento_material    = 'S'
       and    empenho.cod_empenho           = lc.cod_empenho
       and    lc.tipo                       = 'O'
       and    lc.geradomanual               = 'M'
       and    lc.anomes                    >= %ANOMES_INI%
       and    lc.anomes                    <= %ANOMES_FIM%
       and    lc.cod_filial                 = 1
       and    lc.cod_empresa                = 1
       and    lc.cod_grupoempresa           = 1

       union  all

       select %NEG_COD%                           cod_negocio
            , %NEG_DESC%                          negocio
            , grupoempenho.cod_grupoempenho
            , grupoempenho.descricao              grupo
            , empenho.cod_empenho
            , empenho.descricao                   empenho
            , 0                                   vlr_orcado
            , nvl(lc.valor,0)                     vlr_realizado
       from   material.realizado lc
            , custo.empenho
            , custo.grupoempenho
       where  grupoempenho.cod_grupoempenho = empenho.cod_grupoempenho
       and    empenho.cod_tipoempenho        in (1, 2, 6, 8, 9, 11, 14)
       and    empenho.orcamento_material    = 'S'
       and    empenho.cod_empenho           = lc.cod_empenho
       and    lc.tipo                       = 'R'
       and    lc.anomes                    >= %ANOMES_INI%
       and    lc.anomes                    <= %ANOMES_FIM%
       and    lc.cod_filial                 = 1
       and    lc.cod_empresa                = 1
       and    lc.cod_grupoempresa           = 1
     ) tmp
where 1 = 1
%FILTRO_NEGOCIO%
group by tmp.cod_negocio, tmp.negocio, tmp.cod_grupoempenho, tmp.grupo
       , tmp.cod_empenho, tmp.empenho
order by tmp.grupo, tmp.empenho
