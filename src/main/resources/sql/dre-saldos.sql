-- Saldos contábeis por conta, do relatório de balancete do SoftCana.
--
-- Consulta ENTREGUE PELO USUÁRIO, reproduzida como veio. As únicas mudanças
-- são os três marcadores: %ANOMES% no lugar de 202605, e %DATA_INI% /
-- %DATA_FIM% no lugar de '01/05/2026' e '31/05/2026'. Não reescrevi nada
-- além disso: é SQL gerado pelo próprio ERP, e "melhorar" um balancete que
-- fecha é o caminho mais curto para ele deixar de fechar.
--
-- Uma coisa a saber: o saldo anterior usa (%ANOMES% - 1), aritmética que
-- funciona dentro do ano e quebra na virada (202601 - 1 = 202600, que não
-- existe). É assim no original e ficou assim. Para o DRE do ano corrente,
-- que é o uso aqui, não faz diferença.
select tmp2.tamanho
     , tmp2.class
     , tmp2.antconta
     , tmp2.cod_contacontabil
     , tmp2.descricao
     , tmp2.totaldebito
     , tmp2.totalcredito
     , tmp2.saldo
     , tmp2.saldoanterior
     , tmp2.saldomes
     , tmp2.totaldebitogeral
     , tmp2.totalcreditogeral
     , tmp2.debito_credito
     , tmp2.cod_contacontabil_formatado
     , tmp2.gef
from (select tabela.tamanho
     , tabela.class
     , lag(tabela.cod_contacontabil,1,0) over (order by  tabela.classificacao) antconta
     , tabela.cod_contacontabil
     , tabela.descricao
     , sum(tabela.totaldebito)   totaldebito
     , sum(tabela.totalcredito)  totalcredito
     , case when ('N' = 'N') and (sum(tabela.saldo) < 0) then sum(tabela.saldo)*(-1)
       else sum(tabela.saldo)
       end saldo
     , sum(tabela.saldoanterior) saldoanterior
     , sum(tabela.saldomes)      saldomes
     -- OS 50646, somar o total do grau 1 e mostrar no final do relatorio
     , sum(sum(case when tabela.tamanho = 1 then
                         tabela.totaldebito
                    else 0
               end)) over()   totaldebitogeral
     , sum(sum(case when tabela.tamanho = 1 then
                         tabela.totalcredito
                    else 0
               end)) over()  totalcreditogeral
     , case when sum(tabela.saldo) >= 0 then 'D' else 'C' end debito_credito
     , tabela.cod_contacontabil_formatado
     , tabela.GEF
from (
select to_number(length(planocontas.cod_contacontabil)) tamanho
     , case when 'S' = 'N' then ctb.fn_busca_desc_contahist(1,1,1
                                                                               ,planocontas.cod_planocontas,planocontas.cod_contacontabil,TO_DATE(%DATA_INI%, 'DD/MM/YYYY'),TO_DATE(%DATA_FIM%, 'DD/MM/YYYY'))
            else decode( length(planocontas.cod_contacontabil), 1
                       , ' '||planocontas.cod_contacontabil
                       , decode( length(planocontas.cod_contacontabil), 2
                               , replace(to_char(planocontas.cod_contacontabil,'9G9'),',','.')
                               , decode( length(planocontas.cod_contacontabil), 3
                                       , replace(to_char(planocontas.cod_contacontabil,'9G9G9'),',','.')
                                       , decode( length(planocontas.cod_contacontabil), 5
                                               , replace(to_char(planocontas.cod_contacontabil,'9G9G9G99'),',','.')
                                               , decode( length(planocontas.cod_contacontabil), 8
                                                       , replace(to_char(planocontas.cod_contacontabil,'9G9G9G99G999'),',','.'))))))
                 ||'                 '||ctb.fn_busca_desc_contahist(1,1,1
                                                                               ,planocontas.cod_planocontas,planocontas.cod_contacontabil,TO_DATE(%DATA_INI%, 'DD/MM/YYYY'),TO_DATE(%DATA_FIM%, 'DD/MM/YYYY'))
       end class
     , to_number(saldos.cod_contacontabil) cod_contacontabil
     , case when 'S' = 'N' then ' '
            else decode( length(planocontas.cod_contacontabil), 1
                       , ' '||planocontas.cod_contacontabil
                       , decode( length(planocontas.cod_contacontabil), 2
                               , replace(to_char(planocontas.cod_contacontabil,'9G9'),',','.')
                               , decode( length(planocontas.cod_contacontabil), 3
                                       , replace(to_char(planocontas.cod_contacontabil,'9G9G9'),',','.')
                                       , decode( length(planocontas.cod_contacontabil), 5
                                               , replace(to_char(planocontas.cod_contacontabil,'9G9G9G99'),',','.')
                                               , decode( length(planocontas.cod_contacontabil), 8
                                                       , replace(to_char(planocontas.cod_contacontabil,'9G9G9G99G999'),',','.'))))))
       end cod_contacontabil_formatado
     , saldos.totaldebito
     , saldos.totalcredito
     , saldos.saldo
     , saldos.saldoanterior
     , saldos.contas_saldo_ant
     , planocontas.classificacao
     , (saldos.totaldebito - saldos.totalcredito) saldomes
     , ctb.fn_busca_desc_contahist(1,1,1
                                                                               ,planocontas.cod_planocontas,planocontas.cod_contacontabil,TO_DATE(%DATA_INI%, 'DD/MM/YYYY'),TO_DATE(%DATA_FIM%, 'DD/MM/YYYY'))  descricao
     , case when ('N' = 'S') then
                     '1 - 1 - 1'
                 else
                  '0'
             end GEF
from ( --     nivel 1
       select to_number(substr(to_char(cod_contacontabil),1,1)) cod_contacontabil
            , sum(totaldebito) totaldebito
            , sum(totalcredito) totalcredito
            , sum(saldo) saldo
            , sum(saldoanterior) saldoanterior
            , sum(contas_saldo_ant) contas_saldo_ant
       from ( select s.cod_contacontabil
                   , sum(s.totaldebito)   totaldebito
                   , sum(s.totalcredito)  totalcredito
                   , sum(s.saldo)         saldo
                   , sum(s.saldoanterior) saldoanterior
                   , sum(abs(s.saldoanterior)) contas_saldo_ant
              from ( --     total de débito
                     select saldoconta.cod_contacontabil
                          , sum(saldoconta.totaldebito) totaldebito
                          , 0                           totalcredito
                          , 0                           saldo
                          , 0                           saldoanterior
                     from   ctb.saldoconta
                          , ctb.planocontas
                     where  saldoconta.anomes                  = %ANOMES%
                     and    saldoconta.cod_filial              = 1
                     and    saldoconta.cod_empresa             = 1
                     and    saldoconta.cod_grupoempresa        = 1
                     and    saldoconta.cod_planocontas         = 1
                     and    planocontas.cod_filial              = 1
                     and    planocontas.cod_empresa             = 1
                     and    planocontas.cod_grupoempresa        = 1
                     and    planocontas.cod_planocontas         = 1
                     and    saldoconta.cod_contacontabil       = planocontas.cod_contacontabil
                     and    planocontas.classificacao    between 0 and 99999999999
                     and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,1))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                     group  by saldoconta.cod_contacontabil

                     union  all

                     --     total de credito
                     select saldoconta.cod_contacontabil
                          , 0                            totaldebito
                          , sum(saldoconta.totalcredito) totalcredito
                          , 0                            saldo
                          , 0                            saldoanterior
                     from   ctb.saldoconta
                          , ctb.planocontas
                     where  saldoconta.anomes                  = %ANOMES%
                     and    saldoconta.cod_filial              = 1
                     and    saldoconta.cod_empresa             = 1
                     and    saldoconta.cod_grupoempresa        = 1
                     and    saldoconta.cod_planocontas         = 1
                     and    planocontas.cod_filial              = 1
                     and    planocontas.cod_empresa             = 1
                     and    planocontas.cod_grupoempresa        = 1
                     and    planocontas.cod_planocontas         = 1
                     and    saldoconta.cod_contacontabil       = planocontas.cod_contacontabil
                     and    planocontas.classificacao    between 0 and 99999999999
                     and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,1))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                     group  by saldoconta.cod_contacontabil

                     union  all

                     --     saldo
                     select saldoconta.cod_contacontabil
                          , 0                totaldebito
                          , 0                totalcredito
                          , saldoconta.saldo saldo
                          , 0                saldoanterior
                     from   ctb.saldoconta
                          , ( select sc1.cod_contacontabil
                                   , max(sc1.anomes) anomes
                              from   ctb.saldoconta sc1
                                   , ctb.planocontas
                              where  sc1.anomes                        <= %ANOMES%
                              and    sc1.cod_filial                     = 1
                              and    sc1.cod_empresa                    = 1
                              and    sc1.cod_grupoempresa               = 1
                              and    sc1.cod_planocontas                = 1
                              and    planocontas.cod_filial              = 1
                              and    planocontas.cod_empresa             = 1
                              and    planocontas.cod_grupoempresa        = 1
                              and    planocontas.cod_planocontas         = 1
                              and    sc1.cod_contacontabil              = planocontas.cod_contacontabil
                              and    planocontas.classificacao    between 0 and 99999999999
                              and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,1))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                              group by sc1.cod_contacontabil
                             ) saldoconta_aux
                     where  saldoconta.cod_contacontabil          = saldoconta_aux.cod_contacontabil
                     and    saldoconta.anomes                     = saldoconta_aux.anomes
                     and    saldoconta.cod_planocontas            = 1
                     and    saldoconta.cod_filial                 = 1
                     and    saldoconta.cod_empresa                = 1
                     and    saldoconta.cod_grupoempresa           = 1

                     union  all

                     --     saldo anterior
                     select saldoconta.cod_contacontabil
                          , 0                totaldebito
                          , 0                totalcredito
                          , 0                saldo
                          , saldoconta.saldo saldoanterior
                     from   ctb.saldoconta
                          , (select sc1.cod_contacontabil
                                  , max(sc1.anomes) anomes
                             from   ctb.saldoconta sc1
                                  , ctb.planocontas
                             where  sc1.anomes                        <= (%ANOMES% -1)
                             and    sc1.cod_filial                     = 1
                             and    sc1.cod_empresa                    = 1
                             and    sc1.cod_grupoempresa               = 1
                             and    sc1.cod_planocontas                = 1
                             and    planocontas.cod_filial              = 1
                             and    planocontas.cod_empresa             = 1
                             and    planocontas.cod_grupoempresa        = 1
                             and    planocontas.cod_planocontas         = 1
                             and    sc1.cod_contacontabil              = planocontas.cod_contacontabil
                             and    planocontas.classificacao    between 0 and 99999999999
                             and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,1))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                             group by sc1.cod_contacontabil
                            ) saldoconta_aux
                     where  saldoconta.anomes                     = saldoconta_aux.anomes
                     and    saldoconta.cod_planocontas            = 1
                     and    saldoconta.cod_contacontabil          = saldoconta_aux.cod_contacontabil
                     and    saldoconta.cod_filial                 = 1
                     and    saldoconta.cod_empresa                = 1
                     and    saldoconta.cod_grupoempresa           = 1
                   ) s
              group  by s.cod_contacontabil
            )
       where  1 <= 5
       group  by to_number(substr(to_char(cod_contacontabil),1,1))

       union  all

       --     nivel 2
       select to_number(substr(to_char(cod_contacontabil),1,2)) cod_contacontabil
            , sum(totaldebito) totaldebito
            , sum(totalcredito) totalcredito
            , sum(saldo) saldo
            , sum(saldoanterior) saldoanterior
            , sum(contas_saldo_ant) contas_saldo_ant
       from ( select s.cod_contacontabil
                   , sum(s.totaldebito)   totaldebito
                   , sum(s.totalcredito)  totalcredito
                   , sum(s.saldo)         saldo
                   , sum(s.saldoanterior) saldoanterior
                   , sum(abs(s.saldoanterior)) contas_saldo_ant
              from ( --     total de débito
                     select saldoconta.cod_contacontabil
                          , sum(saldoconta.totaldebito) totaldebito
                          , 0                           totalcredito
                          , 0                           saldo
                          , 0                           saldoanterior
                     from   ctb.saldoconta
                          , ctb.planocontas
                     where  saldoconta.anomes                  = %ANOMES%
                     and    saldoconta.cod_filial              = 1
                     and    saldoconta.cod_empresa             = 1
                     and    saldoconta.cod_grupoempresa        = 1
                     and    saldoconta.cod_planocontas         = 1
                     and    planocontas.cod_filial              = 1
                     and    planocontas.cod_empresa             = 1
                     and    planocontas.cod_grupoempresa        = 1
                     and    planocontas.cod_planocontas         = 1
                     and    saldoconta.cod_contacontabil       = planocontas.cod_contacontabil
                     and    planocontas.classificacao    between 0 and 99999999999
                     and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,2))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                     group  by saldoconta.cod_contacontabil

                     union  all

                     --     total de credito
                     select saldoconta.cod_contacontabil
                          , 0                            totaldebito
                          , sum(saldoconta.totalcredito) totalcredito
                          , 0                            saldo
                          , 0                            saldoanterior
                     from   ctb.saldoconta
                          , ctb.planocontas
                     where  saldoconta.anomes                  = %ANOMES%
                     and    saldoconta.cod_filial              = 1
                     and    saldoconta.cod_empresa             = 1
                     and    saldoconta.cod_grupoempresa        = 1
                     and    saldoconta.cod_planocontas         = 1
                     and    planocontas.cod_filial              = 1
                     and    planocontas.cod_empresa             = 1
                     and    planocontas.cod_grupoempresa        = 1
                     and    planocontas.cod_planocontas         = 1
                     and    saldoconta.cod_contacontabil       = planocontas.cod_contacontabil
                     and    planocontas.classificacao    between 0 and 99999999999
                     and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,2))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                     group  by saldoconta.cod_contacontabil

                     union  all

                     --     saldo
                     select saldoconta.cod_contacontabil
                          , 0                totaldebito
                          , 0                totalcredito
                          , saldoconta.saldo saldo
                          , 0                saldoanterior
                     from   ctb.saldoconta
                          , ( select  sc1.cod_contacontabil
                                    , max(sc1.anomes) anomes
                               from   ctb.saldoconta sc1
                                    , ctb.planocontas
                               where  sc1.anomes                        <= %ANOMES%
                               and    sc1.cod_filial                     = 1
                               and    sc1.cod_empresa                    = 1
                               and    sc1.cod_grupoempresa               = 1
                               and    sc1.cod_planocontas                = 1
                               and    planocontas.cod_filial              = 1
                               and    planocontas.cod_empresa             = 1
                               and    planocontas.cod_grupoempresa        = 1
                               and    planocontas.cod_planocontas         = 1
                               and    sc1.cod_contacontabil              = planocontas.cod_contacontabil
                               and    planocontas.classificacao    between 0 and 99999999999
                               and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,2))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                               group by  sc1.cod_contacontabil
                            ) saldoconta_auxiliar
                     where  saldoconta.anomes             = saldoconta_auxiliar.anomes
                     and    saldoconta.cod_contacontabil  = saldoconta_auxiliar.cod_contacontabil
                     and    saldoconta.cod_planocontas    = 1
                     and    saldoconta.cod_filial         = 1
                     and    saldoconta.cod_empresa        = 1
                     and    saldoconta.cod_grupoempresa   = 1

                     union  all

                     --     saldo anterior
                     select saldoconta.cod_contacontabil
                          , 0                totaldebito
                          , 0                totalcredito
                          , 0                saldo
                          , saldoconta.saldo saldoanterior
                     from   ctb.saldoconta
                          , ( select  sc1.cod_contacontabil
                                    , max(sc1.anomes) anomes
                               from   ctb.saldoconta sc1
                                    , ctb.planocontas
                               where  sc1.anomes                        <= (%ANOMES% -1)
                               and    sc1.cod_filial                     = 1
                               and    sc1.cod_empresa                    = 1
                               and    sc1.cod_grupoempresa               = 1
                               and    sc1.cod_planocontas                = 1
                               and    planocontas.cod_filial              = 1
                               and    planocontas.cod_empresa             = 1
                               and    planocontas.cod_grupoempresa        = 1
                               and    planocontas.cod_planocontas         = 1
                               and    sc1.cod_contacontabil              = planocontas.cod_contacontabil
                               and    planocontas.classificacao    between 0 and 99999999999
                               and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,2))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                               group by  sc1.cod_contacontabil
                             ) saldoconta_auxiliar
                     where  saldoconta.anomes              = saldoconta_auxiliar.anomes
                     and    saldoconta.cod_contacontabil   = saldoconta_auxiliar.cod_contacontabil
                     and    saldoconta.cod_planocontas     = 1
                     and    saldoconta.cod_filial          = 1
                     and    saldoconta.cod_empresa         = 1
                     and    saldoconta.cod_grupoempresa    = 1
                   ) s
              group  by s.cod_contacontabil
            )
       where  2 <= 5
       group  by to_number(substr(to_char(cod_contacontabil),1,2))

       union  all

       --     nivel 3
       select to_number(substr(to_char(cod_contacontabil),1,3)) cod_contacontabil
            , sum(totaldebito) totaldebito
            , sum(totalcredito) totalcredito
            , sum(saldo) saldo
            , sum(saldoanterior) saldoanterior
            , sum(contas_saldo_ant) contas_saldo_ant
       from ( select s.cod_contacontabil
                   , sum(s.totaldebito)   totaldebito
                   , sum(s.totalcredito)  totalcredito
                   , sum(s.saldo)         saldo
                   , sum(s.saldoanterior) saldoanterior
                   , sum(abs(s.saldoanterior)) contas_saldo_ant
              from ( --     total de débito
                     select saldoconta.cod_contacontabil
                          , sum(saldoconta.totaldebito) totaldebito
                          , 0                           totalcredito
                          , 0                           saldo
                          , 0                           saldoanterior
                     from   ctb.saldoconta
                          , ctb.planocontas
                     where  saldoconta.anomes                  = %ANOMES%
                     and    saldoconta.cod_filial              = 1
                     and    saldoconta.cod_empresa             = 1
                     and    saldoconta.cod_grupoempresa        = 1
                     and    saldoconta.cod_planocontas         = 1
                     and    planocontas.cod_filial              = 1
                     and    planocontas.cod_empresa             = 1
                     and    planocontas.cod_grupoempresa        = 1
                     and    planocontas.cod_planocontas         = 1
                     and    saldoconta.cod_contacontabil       = planocontas.cod_contacontabil
                     and    planocontas.classificacao    between 0 and 99999999999
                     and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,3))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                     group  by saldoconta.cod_contacontabil

                     union  all

                     --     total de credito
                     select saldoconta.cod_contacontabil
                          , 0                            totaldebito
                          , sum(saldoconta.totalcredito) totalcredito
                          , 0                            saldo
                          , 0                            saldoanterior
                     from   ctb.saldoconta
                          , ctb.planocontas
                     where  saldoconta.anomes                  = %ANOMES%
                     and    saldoconta.cod_filial              = 1
                     and    saldoconta.cod_empresa             = 1
                     and    saldoconta.cod_grupoempresa        = 1
                     and    saldoconta.cod_planocontas         = 1
                     and    planocontas.cod_filial              = 1
                     and    planocontas.cod_empresa             = 1
                     and    planocontas.cod_grupoempresa        = 1
                     and    planocontas.cod_planocontas         = 1
                     and    saldoconta.cod_contacontabil       = planocontas.cod_contacontabil
                     and    planocontas.classificacao    between 0 and 99999999999
                     and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,3))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                     group  by saldoconta.cod_contacontabil

                     union  all

                     --     saldo
                     select saldoconta.cod_contacontabil
                          , 0                totaldebito
                          , 0                totalcredito
                          , saldoconta.saldo saldo
                          , 0                saldoanterior
                     from   ctb.saldoconta
                          , ( select sc1.cod_contacontabil
                                   , max(sc1.anomes) anomes
                              from   ctb.saldoconta sc1
                                   , ctb.planocontas
                              where  sc1.anomes                        <= %ANOMES%
                              and    sc1.cod_filial                     = 1
                              and    sc1.cod_empresa                    = 1
                              and    sc1.cod_grupoempresa               = 1
                              and    sc1.cod_planocontas                = 1
                              and    planocontas.cod_filial              = 1
                              and    planocontas.cod_empresa             = 1
                              and    planocontas.cod_grupoempresa        = 1
                              and    planocontas.cod_planocontas         = 1
                              and    sc1.cod_contacontabil              = planocontas.cod_contacontabil
                              and    planocontas.classificacao    between 0 and 99999999999
                              and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,3))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                              group by sc1.cod_contacontabil
                            ) saldoconta_auxiliar
                     where  saldoconta.anomes                     =  saldoconta_auxiliar.anomes
                     and    saldoconta.cod_contacontabil          =  saldoconta_auxiliar.cod_contacontabil
                     and    saldoconta.cod_planocontas            =  1
                     and    saldoconta.cod_filial                 =  1
                     and    saldoconta.cod_empresa                =  1
                     and    saldoconta.cod_grupoempresa           =  1

                     union  all

                     --     saldo anterior
                     select saldoconta.cod_contacontabil
                          , 0                totaldebito
                          , 0                totalcredito
                          , 0                saldo
                          , saldoconta.saldo saldoanterior
                     from   ctb.saldoconta
                          , ( select sc1.cod_contacontabil
                                   , max(sc1.anomes) anomes
                              from   ctb.saldoconta sc1
                                   , ctb.planocontas
                              where  sc1.anomes                        <= (%ANOMES% -1)
                              and    sc1.cod_filial                     = 1
                              and    sc1.cod_empresa                    = 1
                              and    sc1.cod_grupoempresa               = 1
                              and    sc1.cod_planocontas                = 1
                              and    planocontas.cod_filial              = 1
                              and    planocontas.cod_empresa             = 1
                              and    planocontas.cod_grupoempresa        = 1
                              and    planocontas.cod_planocontas         = 1
                              and    sc1.cod_contacontabil              = planocontas.cod_contacontabil
                              and    planocontas.classificacao    between 0 and 99999999999
                              and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,3))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                              group by sc1.cod_contacontabil
                             ) saldoconta_auxiliar
                     where  saldoconta.anomes               = saldoconta_auxiliar.anomes
                     and    saldoconta.cod_planocontas      = 1
                     and    saldoconta.cod_contacontabil    = saldoconta_auxiliar.cod_contacontabil
                     and    saldoconta.cod_filial           = 1
                     and    saldoconta.cod_empresa          = 1
                     and    saldoconta.cod_grupoempresa     = 1
                   ) s
              group  by s.cod_contacontabil
            )
       where  3 <= 5
       group  by to_number(substr(to_char(cod_contacontabil),1,3))

       union  all

       --     nivel 4
       select to_number(substr(to_char(cod_contacontabil),1,5)) cod_contacontabil
            , sum(totaldebito) totaldebito
            , sum(totalcredito) totalcredito
            , sum(saldo) saldo
            , sum(saldoanterior) saldoanterior
            , sum(contas_saldo_ant) contas_saldo_ant
       from ( select s.cod_contacontabil
                   , sum(s.totaldebito)   totaldebito
                   , sum(s.totalcredito)  totalcredito
                   , sum(s.saldo)         saldo
                   , sum(s.saldoanterior) saldoanterior
                   , sum(abs(s.saldoanterior)) contas_saldo_ant
              from ( --     total de débito
                     select saldoconta.cod_contacontabil
                          , sum(saldoconta.totaldebito) totaldebito
                          , 0                           totalcredito
                          , 0                           saldo
                          , 0                           saldoanterior
                     from   ctb.saldoconta
                          , ctb.planocontas
                     where  saldoconta.anomes                  = %ANOMES%
                     and    saldoconta.cod_filial              = 1
                     and    saldoconta.cod_empresa             = 1
                     and    saldoconta.cod_grupoempresa        = 1
                     and    saldoconta.cod_planocontas         = 1
                     and    planocontas.cod_filial              = 1
                     and    planocontas.cod_empresa             = 1
                     and    planocontas.cod_grupoempresa        = 1
                     and    planocontas.cod_planocontas         = 1
                     and    saldoconta.cod_contacontabil       = planocontas.cod_contacontabil
                     and    planocontas.classificacao    between 0 and 99999999999
                     and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,5))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                     group  by saldoconta.cod_contacontabil

                     union  all

                     --     total de credito
                     select saldoconta.cod_contacontabil
                          , 0                            totaldebito
                          , sum(saldoconta.totalcredito) totalcredito
                          , 0                            saldo
                          , 0                            saldoanterior
                     from   ctb.saldoconta
                          , ctb.planocontas
                     where  saldoconta.anomes                  = %ANOMES%
                     and    saldoconta.cod_filial              = 1
                     and    saldoconta.cod_empresa             = 1
                     and    saldoconta.cod_grupoempresa        = 1
                     and    saldoconta.cod_planocontas         = 1
                     and    planocontas.cod_filial              = 1
                     and    planocontas.cod_empresa             = 1
                     and    planocontas.cod_grupoempresa        = 1
                     and    planocontas.cod_planocontas         = 1
                     and    saldoconta.cod_contacontabil       = planocontas.cod_contacontabil
                     and    planocontas.classificacao    between 0 and 99999999999
                     and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,5))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                     group  by saldoconta.cod_contacontabil

                     union  all

                     --     saldo
                     select saldoconta.cod_contacontabil
                          , 0                totaldebito
                          , 0                totalcredito
                          , saldoconta.saldo saldo
                          , 0                saldoanterior
                     from   ctb.saldoconta
                          , ( select sc1.cod_contacontabil
                                   , max(sc1.anomes) anomes
                              from   ctb.saldoconta sc1
                                   , ctb.planocontas
                              where  sc1.anomes                        <= %ANOMES%
                              and    sc1.cod_filial                     = 1
                              and    sc1.cod_empresa                    = 1
                              and    sc1.cod_grupoempresa               = 1
                              and    sc1.cod_planocontas                = 1
                              and    planocontas.cod_filial              = 1
                              and    planocontas.cod_empresa             = 1
                              and    planocontas.cod_grupoempresa        = 1
                              and    planocontas.cod_planocontas         = 1
                              and    sc1.cod_contacontabil              = planocontas.cod_contacontabil
                              and    planocontas.classificacao    between 0 and 99999999999
                              and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,5))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                              group  by sc1.cod_contacontabil
                            ) saldoconta_auxiliar
                     where  saldoconta.anomes             = saldoconta_auxiliar.anomes
                     and    saldoconta.cod_contacontabil  = saldoconta_auxiliar.cod_contacontabil
                     and    saldoconta.cod_planocontas    = 1
                     and    saldoconta.cod_filial         = 1
                     and    saldoconta.cod_empresa        = 1
                     and    saldoconta.cod_grupoempresa   = 1

                     union  all

                     --     saldo anterior
                     select saldoconta.cod_contacontabil
                          , 0                totaldebito
                          , 0                totalcredito
                          , 0                saldo
                          , saldoconta.saldo saldoanterior
                     from   ctb.saldoconta
                          , ( select sc1.cod_contacontabil
                                   , max(sc1.anomes) anomes
                              from   ctb.saldoconta sc1
                                   , ctb.planocontas
                              where  sc1.anomes                        <= (%ANOMES% -1)
                              and    sc1.cod_filial                     = 1
                              and    sc1.cod_empresa                    = 1
                              and    sc1.cod_grupoempresa               = 1
                              and    sc1.cod_planocontas                = 1
                              and    planocontas.cod_filial              = 1
                              and    planocontas.cod_empresa             = 1
                              and    planocontas.cod_grupoempresa        = 1
                              and    planocontas.cod_planocontas         = 1
                              and    sc1.cod_contacontabil              = planocontas.cod_contacontabil
                              and    planocontas.classificacao    between 0 and 99999999999
                              and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,5))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                              group by sc1.cod_contacontabil
                            ) saldoconta_auxiliar
                     where  saldoconta.anomes             = saldoconta_auxiliar.anomes
                     and    saldoconta.cod_contacontabil  = saldoconta_auxiliar.cod_contacontabil
                     and    saldoconta.cod_planocontas    = 1
                     and    saldoconta.cod_filial         = 1
                     and    saldoconta.cod_empresa        = 1
                     and    saldoconta.cod_grupoempresa   = 1
                   ) s
              group  by s.cod_contacontabil
            )
       where  4 <= 5
       group  by to_number(substr(to_char(cod_contacontabil),1,5))

       union all

       --     nivel 5
       select s.cod_contacontabil
            , sum(s.totaldebito)   totaldebito
            , sum(s.totalcredito)  totalcredito
            , sum(s.saldo)         saldo
            , sum(s.saldoanterior) saldoanterior
            , sum(abs(s.saldoanterior)) contas_saldo_ant
       from ( --     total de débito e crédito do mês
              select saldoconta.cod_contacontabil
                   , sum(saldoconta.totaldebito) totaldebito
                   , sum(saldoconta.totalcredito) totalcredito
                   , 0                           saldo
                   , 0                           saldoanterior
              from   ctb.saldoconta
                   , ctb.planocontas
              where  saldoconta.anomes                  = %ANOMES%
              and    saldoconta.cod_filial              = 1
              and    saldoconta.cod_empresa             = 1
              and    saldoconta.cod_grupoempresa        = 1
              and    saldoconta.cod_planocontas         = 1
              and    planocontas.cod_filial              = 1
              and    planocontas.cod_empresa             = 1
              and    planocontas.cod_grupoempresa        = 1
              and    planocontas.cod_planocontas         = 1
              and    saldoconta.cod_contacontabil       = planocontas.cod_contacontabil
              and    planocontas.classificacao    between 0 and 99999999999
              and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,8))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
              group  by saldoconta.cod_contacontabil

              union  all

              --     saldo
              select saldoconta.cod_contacontabil
                   , 0                totaldebito
                   , 0                totalcredito
                   , saldoconta.saldo saldo
                   , 0                saldoanterior
              from   ctb.saldoconta
                   , ( select sc1.cod_contacontabil
                            , max(sc1.anomes) anomes
                       from   ctb.saldoconta sc1
                            , ctb.planocontas
                       where  sc1.anomes                        <= %ANOMES%
                       and    sc1.cod_filial                     = 1
                       and    sc1.cod_empresa                    = 1
                       and    sc1.cod_grupoempresa               = 1
                       and    sc1.cod_planocontas                = 1
                       and    planocontas.cod_filial              = 1
                       and    planocontas.cod_empresa             = 1
                       and    planocontas.cod_grupoempresa        = 1
                       and    planocontas.cod_planocontas         = 1
                       and    sc1.cod_contacontabil              = planocontas.cod_contacontabil
                       and    planocontas.classificacao    between 0 and 99999999999
                       and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,8))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                       group by sc1.cod_contacontabil
                     ) saldoconta_auxiliar
              where  saldoconta.anomes             = saldoconta_auxiliar.anomes
              and    saldoconta.cod_contacontabil  = saldoconta_auxiliar.cod_contacontabil
              and    saldoconta.cod_planocontas    = 1
              and    saldoconta.cod_filial         = 1
              and    saldoconta.cod_empresa        = 1
              and    saldoconta.cod_grupoempresa   = 1

              union  all

              --     saldo anterior
              select saldoconta.cod_contacontabil
                   , 0                totaldebito
                   , 0                totalcredito
                   , 0                saldo
                   , saldoconta.saldo saldoanterior
              from   ctb.saldoconta
                   , ( select sc1.cod_contacontabil
                            , max(sc1.anomes) anomes
                       from   ctb.saldoconta sc1
                            , ctb.planocontas
                       where  sc1.anomes                        <= (%ANOMES% -1)
                       and    sc1.cod_filial                     = 1
                       and    sc1.cod_empresa                    = 1
                       and    sc1.cod_grupoempresa               = 1
                       and    sc1.cod_planocontas                = 1
                       and    planocontas.cod_filial              = 1
                       and    planocontas.cod_empresa             = 1
                       and    planocontas.cod_grupoempresa        = 1
                       and    planocontas.cod_planocontas         = 1
                       and    sc1.cod_contacontabil              = planocontas.cod_contacontabil
                       and    planocontas.classificacao    between 0 and 99999999999
                       and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,8))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                       group by sc1.cod_contacontabil
                     ) saldoconta_auxiliar
              where  saldoconta.anomes            = saldoconta_auxiliar.anomes
              and    saldoconta.cod_contacontabil = saldoconta_auxiliar.cod_contacontabil
              and    saldoconta.cod_planocontas   = 1
              and    saldoconta.cod_filial        = 1
              and    saldoconta.cod_empresa       = 1
              and    saldoconta.cod_grupoempresa  = 1
            ) s
       group  by s.cod_contacontabil

       union all

       -- ----------------------------------------------------------------------------------
       -- Trazer contas sem movimentação
       -- ----------------------------------------------------------------------------------
       --     nivel 5
       select *
       from   (select cod_contacontabil
                    , 0 totaldebito
                    , 0 totalcredito
                    , 0 saldo
                    , 0 saldoanterior
                    , 0 contas_saldo_ant
               from (  select planocontas.cod_contacontabil
                            , 0                totaldebito
                            , 0                totalcredito
                            , 0                saldo
                            , 0                saldoanterior
                       from  ctb.planocontas
                       where  not exists ( select 1
                                           from   ctb.saldoconta
                                           where  saldoconta.anomes           <= %ANOMES%
                                           and    saldoconta.cod_grupoempresa  = 1
                                           and    saldoconta.cod_empresa       = 1
                                           and    saldoconta.cod_filial        = 1
                                           and    saldoconta.cod_planocontas   = 1
                                           and    saldoconta.cod_contacontabil = planocontas.cod_contacontabil)
                       and    planocontas.cod_filial              = 1
                       and    planocontas.cod_empresa             = 1
                       and    planocontas.cod_grupoempresa        = 1
                       and    planocontas.cod_planocontas         = 1
                       and    planocontas.classificacao     between 0 and 99999999999
                       and    decode(length(to_number(substr(to_char(planocontas.cod_contacontabil),1,8))),1,1,2,2,3,3,5,4,8,5,11,6,0) <= 5
                       group  by planocontas.cod_contacontabil
                    ) tmp
               where  5 <= 5
               group  by cod_contacontabil) tmp
       where 'N' = 'S'
     ) saldos
    ,  ctb.planocontas
where  planocontas.cod_grupoempresa       = 1
and    planocontas.cod_empresa            = 1
and    planocontas.cod_filial             = 1
and    planocontas.cod_planocontas        = 1
and    saldos.cod_contacontabil      = planocontas.cod_contacontabil
order  by planocontas.classificacao
) tabela
where 1=1
and not(
        (tabela.saldoanterior    = 0 and tabela.totalcredito = 0) and
        (tabela.saldo            = 0 and tabela.totaldebito  = 0) and
        (tabela.contas_saldo_ant = 0)
        )
group by tabela.tamanho
       , tabela.class
       , tabela.cod_contacontabil
       , tabela.descricao
       , tabela.classificacao
       , tabela.cod_contacontabil_formatado
       , tabela.GEF
order by tabela.GEF
             , tabela.classificacao
             , tabela.cod_contacontabil
             , tabela.descricao
             , tabela.tamanho
             , tabela.cod_contacontabil_formatado

) tmp2
