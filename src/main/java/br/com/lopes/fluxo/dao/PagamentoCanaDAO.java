package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.model.FluxoRealizadoItem;
import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controle de Pagamento a Fornecedor de Cana.
 *
 * Duas fontes do Oracle, por cod_fornecedor:
 *
 *  1) Agrícola — os EVENTOS do fechamento (agricola.lancamento_cana), pela
 *     consulta oficial: só eventos que imprimem na folha (EVENTO.IMPRIMEFOLHA='T'),
 *     nome por material.vw_fornecedor + rh.vw_pessoa, e o vínculo de fazenda pela
 *     historico_fazenda (maior data_inicio até o lançamento). Os eventos são
 *     dinâmicos — cada evento (EVENTO.DESCRICAO) vira uma coluna, agrupada por
 *     natureza (P = proventos, D = descontos). A "Cana Entregue R$" é o líquido:
 *     SUM(P: +valor, D: -valor).
 *
 *  2) Financeiro — o Pagamento Realizado vem da MESMA consulta do Fluxo de
 *     Caixa Realizado ({@link FluxoRealizadoDAO}), somando o REALIZADO das
 *     linhas cuja conta do fluxo contém "CANA", por fornecedor, na janela de
 *     pagamento informada.
 *
 * Empresa fixada em 1/1/1. Não roda Oracle neste ambiente: a lógica é
 * verificável, mas os números só se confirmam no primeiro deploy real.
 */
public class PagamentoCanaDAO {

    private static final Logger LOG = Logger.getLogger(PagamentoCanaDAO.class.getName());

    private final FluxoRealizadoDAO fluxoDAO = new FluxoRealizadoDAO();

    /**
     * @param safra   cod_safra (ex.: 74)
     * @param entIni  início do lançamento, yyyy-MM-dd
     * @param entFim  fim do lançamento, yyyy-MM-dd
     * @param pagIni  início da janela de pagamentos realizados, yyyy-MM-dd
     * @param pagFim  fim da janela de pagamentos realizados, yyyy-MM-dd
     * @return mapa com "eventos" (metadados das colunas dinâmicas: cod, key,
     *         descricao, natureza) e "fornecedores" (uma linha por fornecedor,
     *         com um campo ev_&lt;cod&gt; por evento, mais cana_entregue,
     *         pagamento_realizado e saldo).
     */
    public Map<String, Object> resumo(int safra, String entIni, String entFim, String pagIni, String pagFim) {
        validarData(entIni); validarData(entFim); validarData(pagIni); validarData(pagFim);

        // 1) Eventos por fornecedor (linha por fornecedor + evento).
        List<Map<String, Object>> linhas = executar(sqlEventos(safra, entIni, entFim));

        // 2) Pagamento realizado por fornecedor (conta de cana).
        Map<Integer, BigDecimal> realizadoPorForn = realizadoCanaPorFornecedor(pagIni, pagFim);

        // 2b) ATR médio ponderado pela cana analisada, por fornecedor (consulta
        //     oficial de qualidade). Se essa consulta pesada falhar, segue sem ATR.
        Map<Integer, BigDecimal> atrPorForn = atrPorFornecedor(safra, entIni, entFim);

        // 3) Pivô: fornecedor x evento.
        Map<Integer, String[]> eventoMeta = new LinkedHashMap<>();      // cod_evento -> [descricao, natureza]
        Map<Integer, Map<String, Object>> forn = new LinkedHashMap<>(); // cod_fornecedor -> linha
        Map<Integer, BigDecimal> canaNet = new LinkedHashMap<>();       // cod_fornecedor -> líquido por natureza

        for (Map<String, Object> row : linhas) {
            Integer codForn = inteiroObj(row.get("cod_fornecedor"));
            if (codForn == null) continue;
            Integer codEv = inteiroObj(row.get("cod_evento"));
            String descEv = texto(row.get("desc_evento"), "Evento " + codEv);
            String nat = texto(row.get("natureza"), "O").toUpperCase();
            BigDecimal valor = numero(row.get("valor"));

            // Só proventos e descontos; o grupo "Outros" fica de fora (e soma 0 no líquido).
            if (!"P".equals(nat) && !"D".equals(nat)) continue;

            eventoMeta.putIfAbsent(codEv, new String[]{descEv, nat});

            Map<String, Object> f = forn.computeIfAbsent(codForn, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("cod_fornecedor", codForn);
                m.put("nome", texto(row.get("nome"), "Fornecedor " + codForn));
                return m;
            });
            String key = "ev_" + codEv;
            f.put(key, numero(f.get(key)).add(valor));

            BigDecimal assinado = "P".equals(nat) ? valor : "D".equals(nat) ? valor.negate() : BigDecimal.ZERO;
            canaNet.merge(codForn, assinado, BigDecimal::add);
        }

        // 4) Finaliza cada fornecedor: cana_entregue, realizado, saldo.
        List<Map<String, Object>> fornecedores = new ArrayList<>(forn.values());
        for (Map<String, Object> f : fornecedores) {
            Integer cod = inteiroObj(f.get("cod_fornecedor"));
            BigDecimal cana = canaNet.getOrDefault(cod, BigDecimal.ZERO);
            BigDecimal realizado = realizadoPorForn.getOrDefault(cod, BigDecimal.ZERO);
            f.put("atr", atrPorForn.getOrDefault(cod, BigDecimal.ZERO));
            f.put("cana_entregue", cana);
            f.put("pagamento_realizado", realizado);
            f.put("saldo", cana.subtract(realizado));
        }
        fornecedores.sort(Comparator.comparing(m -> texto(m.get("nome"), "")));

        // 5) Metadados dos eventos, ordenados: proventos, descontos, outros; e por descrição.
        List<Map<String, Object>> eventos = new ArrayList<>();
        eventoMeta.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<Integer, String[]> en) -> ordemNat(en.getValue()[1]))
                        .thenComparing(en -> en.getValue()[0]))
                .forEach(en -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("cod", en.getKey());
                    m.put("key", "ev_" + en.getKey());
                    m.put("descricao", en.getValue()[0]);
                    m.put("natureza", en.getValue()[1]);
                    eventos.add(m);
                });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("eventos", eventos);
        out.put("fornecedores", fornecedores);
        return out;
    }

    /**
     * ATR médio ponderado pela cana analisada (ANALISE_PCTS.PESOLIQUIDO), por
     * fornecedor — a consulta oficial de qualidade da entrada de cana. O
     * fornecedor vem da historico_fazenda vigente na data do movimento. Sem
     * filtro de cod_tipofazenda (todos os tipos). fn_autorizacao_empresa foi
     * removida (a intranet conecta com usuário de serviço, empresa 1/1/1).
     *
     * Consulta pesada; se falhar, devolve vazio para não derrubar a tela.
     */
    private Map<Integer, BigDecimal> atrPorFornecedor(int safra, String entIni, String entFim) {
        String s = String.valueOf(safra);
        String dIni = td(entIni);
        String dFim = td(entFim);
        String sql = ("""
            with historicofazenda as (
               select /*+MATERIALIZE*/
                      historico_fazenda.cod_fazenda
                     ,historico_fazenda.data_inicio
                     ,geral.fn_datanvl(historico_fazenda.data_fim) data_fim
                     ,historico_fazenda.cod_fornecedor
                     ,historico_fazenda.cod_tipofazenda
                     ,historico_fazenda.cod_regiaoagricola
                     ,historico_fazenda.id_setoragricola
               from   agricola.tipofazenda tipofazenda
                     ,agricola.regiao_agricola regiao_agricola
                     ,agricola.setor_agricola setor_agricola
                     ,agricola.historico_fazenda historico_fazenda
               where  tipofazenda.cod_tipofazenda = historico_fazenda.cod_tipofazenda
               and    setor_agricola.id_setoragricola = historico_fazenda.id_setoragricola
               and    regiao_agricola.cod_regiaoagricola = historico_fazenda.cod_regiaoagricola)
            SELECT historico_FAZENDA.COD_FORNECEDOR cod_fornecedor,
                   decode(sum(ANALISE_PCTS.PESOLIQUIDO),0,0,
                          nvl(sum(ANALISE_PCTS.ATR * ANALISE_PCTS.PESOLIQUIDO) / sum(ANALISE_PCTS.PESOLIQUIDO),0)) atr
            FROM RH.TURNO
                 , RH.PESSOA PESS_AGENCIADOR
                 , MATERIAL.FORNECEDOR FORN_AGENCIADOR
                 , RH.AGENCIADOR
                 , AGRICOLA.ANALISE_IMP_VEGETAL
                 , AGRICOLA.ANALISE_IMP_MINERAL
                 , AGRICOLA.ANALISE_PCTS
                 , AGRICOLA.TIPO_CORTE
                 , AGRICOLA.TIPOCANA
                 , AGRICOLA.IDADE_CANA
                 , AGRICOLA.VARIEDADE
                 , AGRICOLA.TIPOVARIEDADE
                 , historicofazenda HISTORICO_FAZENDA
                 , AGRICOLA.FAZENDA
                 , AGRICOLA.SAFRA
                 , AGRICOLA.TALHAO
                 , AGRICOLA.VW_DESTINOTALHAO DESTINO
                 , AGRICOLA.AMBIENTE_PRODUCAO AMBIENTE
                 , AGRICOLA.FRENTE
                 , agricola.liberacao_corte
                 , AGRICOLA.ORDEM_CORTE_UNICA
                 , agricola.motivonaoanalise
                 , agricola.itenscolheita
                 , AGRICOLA.ITENSENTRADACANA
                 , AGRICOLA.ENTRADACANA
                 , agricola.destinocana
                 , agricola.IRRIGACAOTIPO
             WHERE TURNO.COD_TURNO                 (+)= ANALISE_PCTS.COD_TURNO
               AND PESS_AGENCIADOR.COD_PESSOA      (+)= FORN_AGENCIADOR.COD_PESSOA
               AND FORN_AGENCIADOR.COD_FORNECEDOR  (+)= AGENCIADOR.COD_FORNECEDOR
               AND AGENCIADOR.COD_AGENCIADOR       (+)= ITENSENTRADACANA.COD_AGENCIADOR
               AND AGENCIADOR.COD_GRUPOEMPRESA     (+)= ITENSENTRADACANA.COD_GRUPOEMPRESA
               AND motivonaoanalise.cod_motivo     (+)= itenscolheita.cod_motivonaoanalise
               and itenscolheita.cod_grupoempresa     = itensentradacana.cod_grupoempresa
               and itenscolheita.cod_empresa          = itensentradacana.cod_empresa
               and itenscolheita.cod_filial           = itensentradacana.cod_filial
               and itenscolheita.cod_safra            = itensentradacana.cod_safra
               and itenscolheita.cod_entradacana      = itensentradacana.cod_entradacana
               and itenscolheita.seq_itensentradacana = itensentradacana.seq_itenscolheita
               AND ANALISE_IMP_VEGETAL.COD_GRUPOEMPRESA    (+) = ITENSENTRADACANA.COD_GRUPOEMPRESA
               AND ANALISE_IMP_VEGETAL.COD_EMPRESA         (+) = ITENSENTRADACANA.COD_EMPRESA
               AND ANALISE_IMP_VEGETAL.COD_FILIAL          (+) = ITENSENTRADACANA.COD_FILIAL
               AND ANALISE_IMP_VEGETAL.COD_SAFRA           (+) = ITENSENTRADACANA.COD_SAFRA
               AND ANALISE_IMP_VEGETAL.COD_ENTRADACANA     (+) = ITENSENTRADACANA.COD_ENTRADACANA
               AND ANALISE_IMP_VEGETAL.SEQ_ITENSENTRADACANA(+) = ITENSENTRADACANA.SEQ_ITENSENTRADACANA
               AND ANALISE_IMP_MINERAL.COD_GRUPOEMPRESA    (+) = ITENSENTRADACANA.COD_GRUPOEMPRESA
               AND ANALISE_IMP_MINERAL.COD_EMPRESA         (+) = ITENSENTRADACANA.COD_EMPRESA
               AND ANALISE_IMP_MINERAL.COD_FILIAL          (+) = ITENSENTRADACANA.COD_FILIAL
               AND ANALISE_IMP_MINERAL.COD_SAFRA           (+) = ITENSENTRADACANA.COD_SAFRA
               AND ANALISE_IMP_MINERAL.COD_ENTRADACANA     (+) = ITENSENTRADACANA.COD_ENTRADACANA
               AND ANALISE_IMP_MINERAL.SEQ_ITENSENTRADACANA(+) = ITENSENTRADACANA.SEQ_ITENSENTRADACANA
               AND ANALISE_PCTS.COD_GRUPOEMPRESA       (+) = ITENSENTRADACANA.COD_GRUPOEMPRESA
               AND ANALISE_PCTS.COD_EMPRESA            (+) = ITENSENTRADACANA.COD_EMPRESA
               AND ANALISE_PCTS.COD_FILIAL             (+) = ITENSENTRADACANA.COD_FILIAL
               AND ANALISE_PCTS.COD_SAFRA              (+) = ITENSENTRADACANA.COD_SAFRA
               AND ANALISE_PCTS.COD_ENTRADACANA        (+) = ITENSENTRADACANA.COD_ENTRADACANA
               AND ANALISE_PCTS.SEQ_ITENSENTRADACANA   (+) = ITENSENTRADACANA.SEQ_ITENSENTRADACANA
               and tipo_corte.cod_tipocorte           = ORDEM_CORTE_UNICA.cod_tipocorte
               AND TIPOCANA.COD_TIPOCANA              = ORDEM_CORTE_UNICA.COD_TIPOCANA
               AND IDADE_CANA.COD_IDADE_CANA          = TALHAO.NUMEROCORTE
               AND VARIEDADE.COD_VARIEDADE            = TALHAO.COD_VARIEDADE
               AND TIPOVARIEDADE.COD_TIPOVARIEDADE(+) = VARIEDADE.COD_TIPOVARIEDADE
               AND TALHAO.DESTINO_TALHAO              = DESTINO.DESTINO (+)
               AND TALHAO.COD_AMBIENTEPROD            = AMBIENTE.ID_AMBPRODUC (+)
               AND ENTRADACANA.DATAMOVIMENTO BETWEEN HISTORICO_FAZENDA.DATA_INICIO AND HISTORICO_FAZENDA.DATA_FIM
               AND HISTORICO_FAZENDA.COD_FAZENDA      = FAZENDA.COD_FAZENDA
               AND FAZENDA.COD_FAZENDA                = ITENSENTRADACANA.COD_FAZENDA
               and talhao.cod_safra = {SAFRA}
               AND SAFRA.COD_GRUPOEMPRESA             = ENTRADACANA.COD_GRUPOEMPRESA
               AND SAFRA.COD_EMPRESA                  = ENTRADACANA.COD_EMPRESA
               AND SAFRA.COD_FILIAL                   = ENTRADACANA.COD_FILIAL
               AND SAFRA.COD_SAFRA                    = ENTRADACANA.COD_SAFRA
               AND TALHAO.COD_FAZENDA                 = ITENSENTRADACANA.COD_FAZENDA
               AND TALHAO.COD_TALHAO                  = ITENSENTRADACANA.COD_TALHAO
               AND TALHAO.COD_SAFRADETALHE            = ITENSENTRADACANA.COD_SAFRADETALHE
               AND TALHAO.COD_SAFRA                   = ITENSENTRADACANA.COD_SAFRA
               AND TALHAO.ZONA                        = ITENSENTRADACANA.ZONA
               AND (nvl(TALHAO.MATURADOR,'N')         = 'T' OR 'T' = 'T')
               AND TALHAO.ENCERRADO                   = DECODE('T','T',TALHAO.ENCERRADO,'T')
               AND IRRIGACAOTIPO.COD_TIPOIRRIGACAO (+) = TALHAO.COD_TIPOIRRIGACAO
               AND FRENTE.COD_FRENTE                  = ORDEM_CORTE_UNICA.COD_FRENTE
               AND LIBERACAO_CORTE.COD_GRUPOEMPRESA   = ORDEM_CORTE_UNICA.COD_GRUPOEMPRESA
               AND LIBERACAO_CORTE.COD_EMPRESA        = ORDEM_CORTE_UNICA.COD_EMPRESA
               AND LIBERACAO_CORTE.COD_FILIAL         = ORDEM_CORTE_UNICA.COD_FILIAL
               AND LIBERACAO_CORTE.COD_SAFRADETALHE   = ORDEM_CORTE_UNICA.COD_SAFRADETALHE
               AND LIBERACAO_CORTE.COD_SAFRA          = ORDEM_CORTE_UNICA.COD_SAFRA
               AND LIBERACAO_CORTE.COD_FRENTE         = ORDEM_CORTE_UNICA.COD_FRENTE
               AND LIBERACAO_CORTE.COD_FAZENDA        = ORDEM_CORTE_UNICA.COD_FAZENDA
               AND LIBERACAO_CORTE.ZONA               = ORDEM_CORTE_UNICA.ZONA
               AND LIBERACAO_CORTE.COD_TALHAO         = ORDEM_CORTE_UNICA.COD_TALHAO
               AND LIBERACAO_CORTE.DATA_LIBERACAO     = ORDEM_CORTE_UNICA.DATA_LIBERACAO
               AND LIBERACAO_CORTE.NUMERO_LIBERACAO   = ORDEM_CORTE_UNICA.NUMERO_LIBERACAO
               AND ORDEM_CORTE_UNICA.COD_GRUPOEMPRESA = ITENSENTRADACANA.COD_GRUPOEMPRESA
               AND ORDEM_CORTE_UNICA.COD_EMPRESA      = ITENSENTRADACANA.COD_EMPRESA
               AND ORDEM_CORTE_UNICA.COD_FILIAL       = ITENSENTRADACANA.COD_FILIAL
               AND ORDEM_CORTE_UNICA.COD_SAFRA        = ITENSENTRADACANA.COD_SAFRA
               AND ORDEM_CORTE_UNICA.NUMERO_ORDEM     = ITENSENTRADACANA.NUMEROORDEMCORTE
               AND ITENSENTRADACANA.COD_GRUPOEMPRESA = ENTRADACANA.COD_GRUPOEMPRESA
               AND ITENSENTRADACANA.COD_EMPRESA      = ENTRADACANA.COD_EMPRESA
               AND ITENSENTRADACANA.COD_FILIAL       = ENTRADACANA.COD_FILIAL
               AND ITENSENTRADACANA.COD_SAFRA        = ENTRADACANA.COD_SAFRA
               AND ITENSENTRADACANA.COD_ENTRADACANA  = ENTRADACANA.COD_ENTRADACANA
               and itensentradacana.bituca           = DECODE('T','T',itensentradacana.bituca,'T')
               AND ITENSENTRADACANA.PESOLIQUIDO      > 0
               and entradacana.cod_filial = 1
               and entradacana.cod_empresa = 1
               and entradacana.cod_grupoempresa = 1
               and 1 = 1
               and agricola.entradacana.datamovimento between {DINI} and {DFIM}
               and entradacana.cod_destinocana  = destinocana.cod_destinocana (+)
             group by historico_FAZENDA.COD_FORNECEDOR
            """)
            .replace("{SAFRA}", s).replace("{DINI}", dIni).replace("{DFIM}", dFim);

        Map<Integer, BigDecimal> mapa = new LinkedHashMap<>();
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int cod = rs.getInt("cod_fornecedor");
                if (rs.wasNull()) continue;
                mapa.put(cod, rs.getBigDecimal("atr") == null ? BigDecimal.ZERO : rs.getBigDecimal("atr"));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "ATR por fornecedor indisponível (segue sem ATR): " + e.getMessage(), e);
        }
        return mapa;
    }

    /** Soma o REALIZADO do Fluxo de Caixa Realizado, conta contendo "CANA", por fornecedor. */
    private Map<Integer, BigDecimal> realizadoCanaPorFornecedor(String pagIni, String pagFim) {
        Map<Integer, BigDecimal> mapa = new LinkedHashMap<>();
        List<FluxoRealizadoItem> itens = fluxoDAO.buscar(LocalDate.parse(pagIni), LocalDate.parse(pagFim));
        for (FluxoRealizadoItem it : itens) {
            String conta = it.getDescricaoConta();
            if (conta == null || !conta.toUpperCase().contains("CANA")) continue;
            Integer cod = it.getCodFornecedor();
            if (cod == null) continue;
            BigDecimal v = it.getRealizado() == null ? BigDecimal.ZERO : it.getRealizado();
            mapa.merge(cod, v, BigDecimal::add);
        }
        return mapa;
    }

    // ── SQL dos eventos (a consulta oficial de fechamento, agregada por fornecedor+evento) ──

    private static String sqlEventos(int safra, String entIni, String entFim) {
        String eIni = td(entIni);
        String eFim = td(entFim);
        String s = String.valueOf(safra);
        return
            "SELECT l.cod_fornecedor cod_fornecedor, p.nome nome, " +
            "       e.cod_evento cod_evento, e.descricao desc_evento, e.natureza natureza, " +
            "       SUM(l.valor) valor " +
            "  FROM agricola.lancamento_cana l " +
            "  INNER JOIN material.vw_fornecedor fornic ON fornic.cod_fornecedor = l.cod_fornecedor " +
            "  INNER JOIN rh.vw_pessoa p ON p.cod_pessoa = fornic.cod_pessoa " +
            "  INNER JOIN rh.evento e ON e.cod_evento = l.cod_evento AND e.imprimefolha = 'T' " +
            "  INNER JOIN agricola.fazenda fz ON fz.cod_fazenda = l.cod_fazenda " +
            "  INNER JOIN agricola.historico_fazenda hf ON hf.cod_fazenda = fz.cod_fazenda " +
            "        AND hf.data_inicio = (SELECT MAX(h2.data_inicio) FROM agricola.historico_fazenda h2 " +
            "                               WHERE h2.cod_fazenda = fz.cod_fazenda AND h2.data_inicio <= l.data_lancamento) " +
            " WHERE l.cod_grupoempresa=1 AND l.cod_empresa=1 AND l.cod_filial=1 AND l.cod_safra=" + s +
            "   AND l.cod_tipoprocessamento=2 " +
            "   AND l.data_lancamento BETWEEN " + eIni + " AND " + eFim + " " +
            " GROUP BY l.cod_fornecedor, p.nome, e.cod_evento, e.descricao, e.natureza " +
            " ORDER BY p.nome, e.natureza, e.descricao";
    }

    // ── Execução / conversões ─────────────────────────────────────────────────

    private List<Map<String, Object>> executar(String sql) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return RowMapperUtil.toList(rs);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro no pagamento de cana: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de pagamento de cana: " + e.getMessage(), e);
        }
    }

    /** Proventos primeiro, depois descontos, depois outros. */
    private static int ordemNat(String nat) {
        return "P".equals(nat) ? 1 : "D".equals(nat) ? 2 : 3;
    }

    private static void validarData(String iso) {
        if (iso == null || !iso.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Data inválida (esperado yyyy-MM-dd): " + iso);
        }
    }

    /** yyyy-MM-dd -> TO_DATE('DD/MM/YYYY','DD/MM/YYYY'); a data já vem validada. */
    private static String td(String iso) {
        String[] p = iso.split("-");
        return "TO_DATE('" + p[2] + "/" + p[1] + "/" + p[0] + "','DD/MM/YYYY')";
    }

    private static Integer inteiroObj(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return (int) Double.parseDouble(v.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal numero(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString().trim()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static String texto(Object v, String padrao) {
        if (v == null) return padrao;
        String s = v.toString().trim();
        return s.isEmpty() ? padrao : s;
    }
}
