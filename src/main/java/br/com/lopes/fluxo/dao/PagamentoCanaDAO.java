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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controle de Pagamento a Fornecedor de Cana.
 *
 * Junta duas fontes do Oracle, por cod_fornecedor:
 *
 *  1) Agrícola — a consulta de fechamento por fornecedor (lancamento_cana +
 *     entradacanaparceria + analise_pcts + parametros_cana): cana do período
 *     (peso líquido), ATR médio ponderado, índice Consecana (ATR/R$), os
 *     eventos (CCT, frete, diversos, serviço, melaço) e proventos/descontos/
 *     líquido. Safra, janela de entrega e data do índice Consecana são
 *     parâmetros (padrão = os valores da consulta original: safra 74,
 *     01/09/2025–01/03/2026, Consecana 28/02/2026).
 *
 *  2) Financeiro — o Pagamento Realizado vem da MESMA consulta do Fluxo de
 *     Caixa Realizado ({@link FluxoRealizadoDAO}), somando o REALIZADO das
 *     linhas cuja conta do fluxo contém "CANA" (a conta de fornecedores de
 *     cana), por fornecedor, na janela de pagamento informada.
 *
 * Empresa fixada em 1/1/1, como nas demais consultas da intranet.
 *
 * Não roda Oracle neste ambiente: a lógica é verificável, mas os números só se
 * confirmam no primeiro deploy real.
 */
public class PagamentoCanaDAO {

    private static final Logger LOG = Logger.getLogger(PagamentoCanaDAO.class.getName());
    /** peso está em kg e o ATR em kg/ton — divide por mil para chegar em ton × ATR × R$. */
    private static final BigDecimal MIL = BigDecimal.valueOf(1000);

    private final FluxoRealizadoDAO fluxoDAO = new FluxoRealizadoDAO();

    /**
     * @param safra      cod_safra (ex.: 74)
     * @param entIni     início da entrega, yyyy-MM-dd (data do movimento / lançamento)
     * @param entFim     fim da entrega (exclusivo), yyyy-MM-dd
     * @param consecana  data do índice Consecana (ATR/R$), yyyy-MM-dd
     * @param pagIni     início da janela de pagamentos realizados, yyyy-MM-dd
     * @param pagFim     fim da janela de pagamentos realizados, yyyy-MM-dd
     * @param precoConsecana  opcional; se informado, sobrepõe o ATR/R$ buscado no
     *                        ERP (útil quando a busca_indicefinanceiro não tem
     *                        índice para a data e retorna 0).
     */
    public List<Map<String, Object>> resumo(int safra, String entIni, String entFim, String consecana,
                                            String pagIni, String pagFim, BigDecimal precoConsecana) {
        validarData(entIni); validarData(entFim); validarData(consecana);
        validarData(pagIni); validarData(pagFim);

        // 1) Agrícola por fornecedor.
        List<Map<String, Object>> linhas = executar(sqlAgricola(safra, entIni, entFim, consecana));

        // 2) Pagamento realizado por fornecedor (conta de cana), reaproveitando o
        //    Fluxo de Caixa Realizado.
        Map<Integer, BigDecimal> realizadoPorForn = realizadoCanaPorFornecedor(pagIni, pagFim);

        // 3) Junta e calcula o líquido pela conta do fechamento de cana:
        //    Líquido = Cana Entregue − CCT + Frete − Diversos − Serviço − Melaço.
        //    O lancamento_cana traz só os EVENTOS (descontos negativos, ajuda de
        //    frete positiva); o valor da cana entregue não está lá, é
        //    peso × ATR × preço Consecana ÷ 1000 (peso em kg, ATR em kg/ton).
        //    Por isso somar l.valor dava o líquido sem a cana.
        for (Map<String, Object> l : linhas) {
            Integer cod = inteiroObj(l.get("cod_fornecedor"));

            // ATR/R$: usa o preço informado (se houver); senão o buscado no ERP.
            BigDecimal atrRs = (precoConsecana != null) ? precoConsecana : numero(l.get("atr_rs"));
            if (precoConsecana != null) l.put("atr_rs", precoConsecana);

            BigDecimal canaEntregue = numero(l.get("cana_periodo"))
                    .multiply(numero(l.get("atr")))
                    .multiply(atrRs)
                    .divide(MIL, 2, java.math.RoundingMode.HALF_UP);

            BigDecimal eventos = numero(l.get("desc_cct"))
                    .add(numero(l.get("ajuda_frete")))
                    .add(numero(l.get("desc_diversos")))
                    .add(numero(l.get("desc_servico")))
                    .add(numero(l.get("desc_melaco")));

            BigDecimal liquido = canaEntregue.add(eventos);
            BigDecimal realizado = cod == null ? null : realizadoPorForn.get(cod);
            if (realizado == null) realizado = BigDecimal.ZERO;

            l.put("cana_entregue", canaEntregue);
            l.put("liquido", liquido);
            l.put("pagamento_realizado", realizado);
            l.put("saldo", liquido.subtract(realizado));
        }
        return linhas;
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

    // ── SQL do agrícola (a consulta fornecida, parametrizada e com aliases snake_case) ──

    private static String sqlAgricola(int safra, String entIni, String entFim, String consecana) {
        String eIni = td(entIni);        // TO_DATE do início da entrega
        String eFim = td(entFim);        // TO_DATE do fim (exclusivo)
        String dCon = td(consecana);     // TO_DATE da data Consecana
        String s = String.valueOf(safra);

        return
        "SELECT l.cod_fornecedor cod_fornecedor, " +
        "       NVL(p.nome, '*** SEM CADASTRO ***') nome, " +
        "       COUNT(DISTINCT l.cod_fazenda) qtd_fazendas, " +
        "       (SELECT SUM(NVL(ecp.pesoliquido,0)) " +
        "          FROM agricola.entradacanaparceria ecp " +
        "          JOIN agricola.entradacana ec ON ecp.cod_entradacana=ec.cod_entradacana " +
        "               AND ecp.cod_grupoempresa=ec.cod_grupoempresa AND ecp.cod_empresa=ec.cod_empresa AND ecp.cod_filial=ec.cod_filial " +
        "         WHERE ecp.cod_grupoempresa=1 AND ecp.cod_empresa=1 AND ecp.cod_filial=1 AND ecp.cod_safra=" + s +
        "           AND ecp.cod_fazenda IN (SELECT lx.cod_fazenda FROM agricola.lancamento_cana lx " +
        "                 WHERE lx.cod_grupoempresa=1 AND lx.cod_empresa=1 AND lx.cod_filial=1 AND lx.cod_safra=" + s +
        "                   AND lx.cod_tipoprocessamento=2 AND lx.cod_fornecedor=l.cod_fornecedor) " +
        "           AND ec.datamovimento BETWEEN " + eIni + " AND " + eFim + ") cana_periodo, " +
        // ATR médio PONDERADO PELA CANA ANALISADA (analise_pcts.pesoliquido) —
        // a mesma fórmula do cálculo oficial de produtividade (AgroProdutividadeDAO),
        // e não a média simples/ponderada pelo peso da parceria. O EXISTS prende as
        // análises às entradas das fazendas do fornecedor sem duplicar a análise
        // quando a entrada tem mais de uma parceria.
        "       (SELECT ROUND(NVL(SUM(ap.atr * ap.pesoliquido) / NULLIF(SUM(ap.pesoliquido), 0), 0), 4) " +
        "          FROM agricola.analise_pcts ap " +
        "          JOIN agricola.entradacana ec2 ON ap.cod_entradacana=ec2.cod_entradacana " +
        "               AND ap.cod_grupoempresa=ec2.cod_grupoempresa AND ap.cod_empresa=ec2.cod_empresa AND ap.cod_filial=ec2.cod_filial " +
        "         WHERE ap.cod_grupoempresa=1 AND ap.cod_empresa=1 AND ap.cod_filial=1 AND ap.cod_safra=" + s +
        "           AND ec2.datamovimento BETWEEN " + eIni + " AND " + eFim + " " +
        "           AND EXISTS (SELECT 1 FROM agricola.entradacanaparceria ecp " +
        "                        WHERE ecp.cod_entradacana=ap.cod_entradacana AND ecp.cod_grupoempresa=ap.cod_grupoempresa " +
        "                          AND ecp.cod_empresa=ap.cod_empresa AND ecp.cod_filial=ap.cod_filial AND ecp.cod_safra=ap.cod_safra " +
        "                          AND ecp.cod_fazenda IN (SELECT lx.cod_fazenda FROM agricola.lancamento_cana lx " +
        "                                WHERE lx.cod_grupoempresa=1 AND lx.cod_empresa=1 AND lx.cod_filial=1 AND lx.cod_safra=" + s +
        "                                  AND lx.cod_tipoprocessamento=2 AND lx.cod_fornecedor=l.cod_fornecedor))) atr, " +
        "       (SELECT financeiro.busca_indicefinanceiro(a.cod_indice_consecana, " + dCon + ") " +
        "          FROM agricola.parametros_cana a " +
        "         WHERE a.cod_grupoempresa=1 AND a.cod_empresa=1 AND a.cod_filial=1 " +
        "           AND " + dCon + " BETWEEN a.data_inicio AND a.data_termino AND ROWNUM=1) atr_rs, " +
        "       SUM(CASE WHEN UPPER(e.descricao) LIKE '%CCT%'      THEN l.valor ELSE 0 END) desc_cct, " +
        "       SUM(CASE WHEN UPPER(e.descricao) LIKE '%FRETE%'    THEN l.valor ELSE 0 END) ajuda_frete, " +
        "       SUM(CASE WHEN UPPER(e.descricao) LIKE '%DIVERSOS%' THEN l.valor ELSE 0 END) desc_diversos, " +
        "       SUM(CASE WHEN UPPER(e.descricao) LIKE '%SERVICO%' OR UPPER(e.descricao) LIKE '%SERVIÇO%' THEN l.valor ELSE 0 END) desc_servico, " +
        "       SUM(CASE WHEN UPPER(e.descricao) LIKE '%MELACO%'  OR UPPER(e.descricao) LIKE '%MELAÇO%'  THEN l.valor ELSE 0 END) desc_melaco " +
        // Valores dos eventos vêm da consulta oficial de fechamento: só eventos que
        // imprimem na folha (EVENTO.IMPRIMEFOLHA='T'), nome pela VW_FORNECEDOR/VW_PESSOA,
        // no período de lançamento (BETWEEN).
        "  FROM agricola.lancamento_cana l " +
        "  INNER JOIN material.vw_fornecedor fornic ON fornic.cod_fornecedor = l.cod_fornecedor " +
        "  INNER JOIN rh.vw_pessoa p ON p.cod_pessoa = fornic.cod_pessoa " +
        "  INNER JOIN rh.evento e ON e.cod_evento = l.cod_evento AND e.imprimefolha = 'T' " +
        " WHERE l.cod_grupoempresa=1 AND l.cod_empresa=1 AND l.cod_filial=1 AND l.cod_safra=" + s +
        "   AND l.cod_tipoprocessamento=2 " +
        "   AND l.data_lancamento BETWEEN " + eIni + " AND " + eFim + " " +
        " GROUP BY l.cod_fornecedor, p.nome " +
        " ORDER BY p.nome";
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
}
