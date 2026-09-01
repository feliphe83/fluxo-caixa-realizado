-- Itens de um objeto de custo dentro de um empenho: o realizado, aberto até
-- material + fornecedor (compra por cotação) ou número do contrato.
--
-- COMO SE CHEGA AO ITEM. Cada linha de material.realizado (tipo 'R') guarda um
-- numero_integracao. A integração vai da ORIGEM (a compra ou a parcela de
-- contrato) para o DESTINO (o realizado): por isso
-- realizado.numero_integracao = ctb.tabelaintegracao.numero_integracao_destino.
-- Dali, pelo numero_integracao_origem, chega-se à fonte.
--
-- DUAS FONTES, e só essas (confirmado pela controladoria):
--  1. MATERIAL.APROVACAOPARACOMPRA — a compra por cotação, que dá material,
--     fornecedor, ordem de compra, cotação e solicitação;
--  2. FINANCEIRO.PARCELASCONTRATO (obj# 84) — a parcela de contrato, que dá o
--     número do contrato; dali chega-se a FINANCEIRO.CONTRATO (mesma junção
--     de ParcelaContratoAprovacaoDAO: numerocontrato + cod_grupoempresa) pra
--     trazer o resumo (descricaoresumida) e o fornecedor do contrato — sem
--     isso, um contrato aparecia na tela só com o número, sem dizer do quê
--     se trata nem com quem foi fechado.
-- Como cada linha resolve por uma das duas, o detalhe fecha com o realizado do
-- objeto — nada fica sem endereço. cod_fornecedor/nome_fornecedor vêm com
-- nvl() das duas fontes porque numa dada linha só uma delas está preenchida.
--
-- O valor de cada item é o valorintegracao da integração (a parte que entrou
-- naquele realizado), então a soma dos itens bate com o realizado do objeto.
--
-- QUANTIDADE/VALOR UNITÁRIO: vem de material.cotacaoitem (mesma tabela e
-- mesmo par de junção — nr_cotacao + cod_material — já usados por
-- VariacaoPrecoDAO para achar preço unitário de item de cotação). Traz as
-- duas colunas candidatas (quantidade pedida e qtde_aprovada, a que
-- efetivamente virou compra) porque não há como confirmar contra o Oracle
-- daqui qual delas está preenchida nesta base; quem decide é o servlet,
-- preferindo qtde_aprovada. O valor unitário em si (valor ÷ quantidade) é
-- calculado em Java, não aqui, para não arriscar divisão por zero no SQL.
--
-- Marcadores: %COLUNA_OBJETO% (a coluna de objeto de custo, a mesma do
-- dashboard), %FILTRO_ANOMES% (os meses escolhidos) e %FILTRO_OBJETO% (o objeto
-- clicado, ou "is null"). Bind: o código do empenho.
select r.anomes
     , ti.valorintegracao                              valor
     , case when upper(tb.nome) = 'MATERIAL.APROVACAOPARACOMPRA' then 'compra'
            when ti.obj# = 84                                    then 'contrato'
            else 'outro' end                            origem
     , ac.cod_material
     , substr(mt.descricao, 1, 80)                      descricao_material
     , nvl(ct.cod_fornecedor, fc.cod_fornecedor)         cod_fornecedor
     , nvl(p.nome, fcp.nome)                             nome_fornecedor
     , oc.nroc
     , ac.nr_cotacao
     , ac.nr_solicitacao
     , pc.numerocontrato
     , substr(fc.descricaoresumida, 1, 120)              contrato_resumo
     , ci.quantidade
     , ci.qtde_aprovada
from        material.realizado         r
       join ctb.tabelaintegracao       ti  on ti.numero_integracao_destino = r.numero_integracao
       join ctb.tabela                 tb  on tb.obj# = ti.obj#
  left join material.aprovacaoparacompra ac on ac.numero_integracao = ti.numero_integracao_origem
                                            and upper(tb.nome) = 'MATERIAL.APROVACAOPARACOMPRA'
  left join material.material          mt  on mt.cod_material = ac.cod_material
  left join material.cotacao           ct  on ct.nr_cotacao   = ac.nr_cotacao
  left join material.cotacaoitem       ci  on ci.nr_cotacao   = ac.nr_cotacao
                                          and ci.cod_material  = ac.cod_material
  left join material.ordemcompra       oc  on oc.nr_cotacao   = ac.nr_cotacao
                                          and oc.cod_plano     = ac.cod_plano
  left join material.fornecedor        forn on forn.cod_fornecedor = ct.cod_fornecedor
  left join rh.pessoa                  p   on p.cod_pessoa    = forn.cod_pessoa
  left join financeiro.parcelascontrato pc on pc.numero_integracao = ti.numero_integracao_origem
                                          and ti.obj# = 84
  left join financeiro.contrato        fc  on fc.numerocontrato   = pc.numerocontrato
                                          and fc.cod_grupoempresa  = pc.cod_grupoempresa
  left join material.fornecedor        fcforn on fcforn.cod_fornecedor = fc.cod_fornecedor
  left join rh.pessoa                  fcp on fcp.cod_pessoa    = fcforn.cod_pessoa
where  r.tipo             = 'R'
and    r.cod_empenho      = ?
and    r.cod_filial       = 1
and    r.cod_empresa      = 1
and    r.cod_grupoempresa = 1
%FILTRO_ANOMES%
%FILTRO_OBJETO%
order by ti.valorintegracao desc
