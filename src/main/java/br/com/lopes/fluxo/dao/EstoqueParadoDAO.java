package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Alerta de Estoque Parado — materiais com estoque, sem entrada há mais de
 * {@code diasLimite} dias (padrão 90).
 *
 * A extração reaproveita o mesmo procedimento/consulta usado no relatório
 * gerencial de movimentação de material (MATERIAL.PR_POPULAR_MOVIMENTOMATERIAL
 * + MATERIAL.TMP_MOVIMENTACAOMATERIAL), mas com dois ajustes:
 *
 *  1) Os parâmetros nomeados (:p_cod_grupoempresa etc.) da consulta original
 *     viram literais fixos — grupoempresa/empresa/filial = 1, como em toda a
 *     intranet (ver outras DAOs), e os demais filtros (família, subgrupo,
 *     funcionário, compartimento, prateleira, região) ficam abertos ("todos"),
 *     porque este alerta é uma varredura geral do almoxarifado, não uma
 *     consulta filtrada por tela.
 *
 *  2) Foi adicionada a coluna DATA_ULTIMA_ENTRADA — a consulta original só
 *     trazia totais de entrada/saída dentro da janela pedida, sem a data da
 *     última entrada em si. Para achar "dias parado" com precisão, o
 *     procedimento é rodado com uma janela ampla (2 anos até ontem) e a
 *     consulta ganha MAX(CASE WHEN uentradasaida='E' THEN udata END) dentro do
 *     mesmo GROUP BY que já soma total_entrada/total_saida. Quando não há
 *     nenhuma entrada nesses 2 anos (branch de itens sem qualquer movimento no
 *     período, ou material só com saída), a data vem nula — tratada como
 *     "parado há mais de 2 anos", que de qualquer forma cai na faixa mais
 *     alta (> 365 dias).
 *
 * Não testado contra o Oracle de produção (sem acesso a partir daqui, por
 * regra do projeto) — a função/pacote MATERIAL.PR_POPULAR_MOVIMENTOMATERIAL e
 * as demais chamadas (fn_existe_inventario_aberto, fn_obter_qtdestoque_movto,
 * fn_vlr_inventario_material, fn_buscaclassificacaofiscal) vieram da consulta
 * de referência fornecida; validar no primeiro envio real e ajustar aqui os
 * nomes/assinaturas que o Oracle reclamar.
 */
public class EstoqueParadoDAO {

    private static final Logger LOG = Logger.getLogger(EstoqueParadoDAO.class.getName());
    private static final DateTimeFormatter ORA_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Sentinela de dias parado quando não há nenhuma entrada dentro da janela pesquisada. */
    private static final int SEM_ENTRADA_NA_JANELA = 9999;

    /** Quantidade de material padrão a considerar como candidatos ao alerta. */
    public static final int DIAS_LIMITE_PADRAO = 90;

    /**
     * @param diasLimite abaixo disso o material não entra no alerta (padrão 90)
     * @return itens com estoque parado há mais de diasLimite dias, ordenados
     *         por valor em estoque decrescente (dentro de cada almoxarifado).
     */
    public List<Map<String, Object>> buscar(int diasLimite) {
        LocalDate dataFim = LocalDate.now().minusDays(1);
        LocalDate dataInicio = dataFim.minusYears(2);
        LocalDate hoje = LocalDate.now();

        popularMovimentacao(dataInicio, dataFim);
        List<Map<String, Object>> bruto = executar(sqlExtracao(dataInicio, dataFim));

        List<Map<String, Object>> saida = new ArrayList<>();
        for (Map<String, Object> r : bruto) {
            Integer diasParado = calcularDiasParado(r.get("data_ultima_entrada"), hoje);
            if (diasParado < diasLimite) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("codMaterial", inteiro(r.get("ucod_material")));
            item.put("codAlmoxarifado", inteiro(r.get("ucod_almoxarifado")));
            item.put("descricao", texto(r.get("descricao")));
            item.put("descFamilia", texto(r.get("desc_familia")));
            item.put("descGrupoMaterial", texto(r.get("desc_grupomaterial")));
            item.put("localizacao", texto(r.get("localizacao")));
            item.put("qtdeEstoque", numero(r.get("qtde_estoque")));
            item.put("valorTotal", numero(r.get("vlr_total")));
            item.put("diasParado", diasParado);
            item.put("faixa", faixaDe(diasParado));
            saida.add(item);
        }

        saida.sort(Comparator
                .comparing((Map<String, Object> m) -> (Integer) m.get("codAlmoxarifado"))
                .thenComparing(m -> ordemFaixa((String) m.get("faixa")))
                .thenComparing((Map<String, Object> m) -> (BigDecimal) m.get("valorTotal"), Comparator.reverseOrder()));

        return saida;
    }

    // ── Cálculo em Java ──────────────────────────────────────────────────

    private static Integer calcularDiasParado(Object dataUltimaEntrada, LocalDate hoje) {
        if (dataUltimaEntrada == null) return SEM_ENTRADA_NA_JANELA;
        try {
            LocalDate d = LocalDate.parse(dataUltimaEntrada.toString().substring(0, 10));
            long dias = ChronoUnit.DAYS.between(d, hoje);
            return (int) Math.max(dias, 0);
        } catch (Exception e) {
            return SEM_ENTRADA_NA_JANELA;
        }
    }

    private static String faixaDe(int diasParado) {
        if (diasParado <= 180) return "91-180";
        if (diasParado <= 365) return "181-365";
        return "acima-365";
    }

    private static int ordemFaixa(String faixa) {
        return switch (faixa) {
            case "91-180" -> 0;
            case "181-365" -> 1;
            default -> 2;
        };
    }

    // ── Oracle ───────────────────────────────────────────────────────────

    /**
     * Popula MATERIAL.TMP_MOVIMENTACAOMATERIAL para a janela [dataInicio,
     * dataFim] — precisa rodar antes da extração, que lê dessa tabela
     * temporária. pv_apaga='S' limpa o que estava lá antes desta chamada.
     */
    private void popularMovimentacao(LocalDate dataInicio, LocalDate dataFim) {
        String pl = "BEGIN MATERIAL.PR_POPULAR_MOVIMENTOMATERIAL(" +
                "pn_cod_grupoempresa => 1, " +
                "pn_cod_empresa => 1, " +
                "pn_cod_filial => 1, " +
                "pn_cod_material => 0, " +
                "pd_data => TO_DATE('" + dataInicio.format(ORA_DATA) + "','DD/MM/YYYY'), " +
                "pv_tipo => 'P', " +
                "pd_datatermino => TO_DATE('" + dataFim.format(ORA_DATA) + "','DD/MM/YYYY'), " +
                "pv_apaga => 'S'); END;";
        try (Connection conn = OracleConnectionUtil.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(pl);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao popular MATERIAL.TMP_MOVIMENTACAOMATERIAL: " + e.getMessage(), e);
            throw new RuntimeException("Falha ao popular movimentação de material: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> executar(String sql) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return RowMapperUtil.toList(rs);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro na extração de estoque parado: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de estoque parado: " + e.getMessage(), e);
        }
    }

    /**
     * Adaptação da consulta de referência (relatório gerencial de
     * movimentação de material): parâmetros nomeados trocados por literais
     * (grupoempresa/empresa/filial=1, demais filtros abertos) e adicionada a
     * coluna DATA_ULTIMA_ENTRADA (ver javadoc da classe).
     */
    private static String sqlExtracao(LocalDate dataInicio, LocalDate dataFim) {
        String dIni = "TO_DATE('" + dataInicio.format(ORA_DATA) + "','DD/MM/YYYY')";
        String dFim = "TO_DATE('" + dataFim.format(ORA_DATA) + "','DD/MM/YYYY')";

        return "SELECT tmp.*\n" +
        "FROM (\n" +
        "    SELECT mov_material.cod_unidade\n" +
        "         , TO_NUMBER(mov_material.ucod_material)     AS ucod_material\n" +
        "         , TO_NUMBER(mov_material.ucod_grupoempresa) AS ucod_grupoempresa\n" +
        "         , TO_NUMBER(mov_material.ucod_empresa)      AS ucod_empresa\n" +
        "         , TO_NUMBER(mov_material.ucod_filial)       AS ucod_filial\n" +
        "         , TO_NUMBER(mov_material.ucod_almoxarifado) AS ucod_almoxarifado\n" +
        "         , mov_material.descricao\n" +
        "         , mov_material.total_entrada\n" +
        "         , mov_material.total_saida\n" +
        "         , mov_material.data_ultima_entrada\n" +
        "         , ( SELECT (CASE WHEN INSTR(l.divisoes, '. ', 2) > 0 THEN\n" +
        "                               SUBSTR(l.divisoes, 1, INSTR(l.divisoes, '. ', 2) - 1)\n" +
        "                          ELSE l.divisoes\n" +
        "                     END)\n" +
        "             FROM   material.localizacaopornivel l\n" +
        "             WHERE  l.cod_almoxarifado = TO_NUMBER(mov_material.ucod_almoxarifado)\n" +
        "             AND    l.cod_grupoempresa = mov_material.ucod_grupoempresa\n" +
        "             AND    l.cod_empresa      = mov_material.ucod_empresa\n" +
        "             AND    l.cod_filial       = mov_material.ucod_filial\n" +
        "             AND    l.cod_localizacao  = mov_material.cod_localizacao ) AS localizacao\n" +
        "         , DECODE(material.fn_existe_inventario_aberto(mov_material.ucod_grupoempresa,\n" +
        "                                                       mov_material.ucod_empresa,\n" +
        "                                                       mov_material.ucod_filial,\n" +
        "                                                       mov_material.ucod_material), 'N',\n" +
        "                  NVL(mov_material.qtde_estoque_atual, 0), '') AS qtde_estoque\n" +
        "         , material.FN_VLR_INVENTARIO_MATERIAL(mov_material.ucod_grupoempresa,\n" +
        "                                               mov_material.ucod_empresa,\n" +
        "                                               mov_material.ucod_filial,\n" +
        "                                               mov_material.ucod_material,\n" +
        "                                               0,\n" +
        "                                               TO_CHAR(" + dFim + ", 'YYYY'),\n" +
        "                                               TO_CHAR(" + dFim + ", 'MM'),\n" +
        "                                               'C') AS customedio\n" +
        "         , (mov_material.qtde_estoque_atual * material.FN_VLR_INVENTARIO_MATERIAL(mov_material.ucod_grupoempresa,\n" +
        "                                                                                mov_material.ucod_empresa,\n" +
        "                                                                                mov_material.ucod_filial,\n" +
        "                                                                                mov_material.ucod_material,\n" +
        "                                                                                0,\n" +
        "                                                                                TO_CHAR(" + dFim + ", 'YYYY'),\n" +
        "                                                                                TO_CHAR(" + dFim + ", 'MM'),\n" +
        "                                                                                'C') ) AS vlr_total\n" +
        "         , mov_material.cod_familia\n" +
        "         , (SELECT f.descricao FROM material.familiamaterial f WHERE f.cod_familia = mov_material.cod_familia) AS desc_familia\n" +
        "         , mov_material.cod_grupomaterial\n" +
        "         , (SELECT g.descricao FROM material.grupomaterial g WHERE g.cod_familia = mov_material.cod_familia AND g.cod_grupomaterial = mov_material.cod_grupomaterial) AS desc_grupomaterial\n" +
        "    FROM (\n" +
        "         SELECT unidade.cod_unidade\n" +
        "              , tmp_01.ucod_material\n" +
        "              , tmp_01.ucod_grupoempresa\n" +
        "              , tmp_01.ucod_empresa\n" +
        "              , tmp_01.ucod_filial\n" +
        "              , tmp_01.ucod_almoxarifado\n" +
        "              , material.descricao\n" +
        "              , tmp_01.total_entrada\n" +
        "              , tmp_01.total_saida\n" +
        "              , tmp_01.data_ultima_entrada\n" +
        "              , tmp_01.cod_localizacao\n" +
        "              , (SELECT material.fn_obter_qtdestoque_movto(tmp_01.ucod_grupoempresa,\n" +
        "                                                           tmp_01.ucod_empresa,\n" +
        "                                                           tmp_01.ucod_filial,\n" +
        "                                                           tmp_01.ucod_almoxarifado,\n" +
        "                                                           tmp_01.ucod_material,\n" +
        "                                                           " + dFim + " + 1,\n" +
        "                                                           0, 'N', 'N') FROM DUAL) AS qtde_estoque_atual\n" +
        "              , tmp_01.cod_familia\n" +
        "              , tmp_01.cod_grupomaterial\n" +
        "         FROM   material.unidade\n" +
        "              , material.material\n" +
        "              , ( SELECT tmp.ucod_material\n" +
        "                       , tmp.ucod_grupoempresa\n" +
        "                       , tmp.ucod_empresa\n" +
        "                       , tmp.ucod_filial\n" +
        "                       , tmp.ucod_almoxarifado\n" +
        "                       , TRUNC(SUM(DECODE(tmp.uentradasaida, 'E', tmp.uqtde, 0)), 8) AS total_entrada\n" +
        "                       , TRUNC(SUM(DECODE(tmp.uentradasaida, 'S', tmp.uqtde, 0)), 8) AS total_saida\n" +
        "                       , MAX(CASE WHEN tmp.uentradasaida = 'E' THEN tmp.udata END) AS data_ultima_entrada\n" +
        "                       , lm.cod_localizacao\n" +
        "                       , h.cod_familia\n" +
        "                       , h.cod_grupomaterial\n" +
        "                  FROM   material.tmp_movimentacaomaterial tmp\n" +
        "                  ,      material.historicofamiliagrupo_material h\n" +
        "                  ,      material.materialporfilial mpf\n" +
        "                  ,      material.localizacaomaterial lm\n" +
        "                  WHERE  tmp.udata                          BETWEEN h.data_inicio AND geral.fn_datanvl(h.data_termino, 'F')\n" +
        "                  AND    h.cod_material                     = tmp.ucod_material\n" +
        "                  AND    tmp.ucod_grupoempresa              = lm.cod_grupoempresa\n" +
        "                  AND    tmp.utipo IN ('IVA','IVD','RQ','RT','TRE','TRS','EN','NFS','IND','RC','VIC','CIC','TR','AJE','AJS','OS')\n" +
        "                  AND    tmp.ucod_empresa                   = lm.cod_empresa\n" +
        "                  AND    tmp.ucod_filial                    = lm.cod_filial\n" +
        "                  AND    tmp.ucod_almoxarifado              = lm.cod_almoxarifado\n" +
        "                  AND    tmp.ucod_material                  = lm.cod_material\n" +
        "                  AND    NOT EXISTS (SELECT 1 FROM material.notafiscalcomplementar nfc WHERE nfc.sequencia_nf = tmp.udocumento AND tmp.utipo = 'EN')\n" +
        "                  AND    tmp.udata                          >= " + dIni + "\n" +
        "                  AND    tmp.udata                          <= " + dFim + "\n" +
        "                  AND    mpf.cod_material                   = lm.cod_material\n" +
        "                  AND    mpf.cod_filial                     = lm.cod_filial\n" +
        "                  AND    mpf.cod_empresa                    = lm.cod_empresa\n" +
        "                  AND    mpf.cod_grupoempresa               = lm.cod_grupoempresa\n" +
        "                  AND    tmp.udata                          BETWEEN mpf.datainicio AND NVL(mpf.datatermino, TO_DATE('31/12/2999','DD/MM/YYYY'))\n" +
        "                  AND    lm.cod_filial                      = 1\n" +
        "                  AND    lm.cod_empresa                     = 1\n" +
        "                  AND    lm.cod_grupoempresa                = 1\n" +
        "                  GROUP BY tmp.ucod_material\n" +
        "                         , tmp.ucod_grupoempresa\n" +
        "                         , tmp.ucod_empresa\n" +
        "                         , tmp.ucod_filial\n" +
        "                         , tmp.ucod_almoxarifado\n" +
        "                         , lm.cod_localizacao\n" +
        "                         , h.cod_familia\n" +
        "                         , h.cod_grupomaterial\n" +
        "                ) tmp_01\n" +
        "         WHERE  unidade.cod_unidade          = material.cod_unidade\n" +
        "         AND    material.cod_material        = tmp_01.ucod_material\n" +
        "\n" +
        "         UNION ALL\n" +
        "\n" +
        "         SELECT tmp1.cod_unidade\n" +
        "              , tmp1.ucod_material\n" +
        "              , tmp1.ucod_grupoempresa\n" +
        "              , tmp1.ucod_empresa\n" +
        "              , tmp1.ucod_filial\n" +
        "              , tmp1.ucod_almoxarifado\n" +
        "              , tmp1.descricao\n" +
        "              , tmp1.total_entrada\n" +
        "              , tmp1.total_saida\n" +
        "              , tmp1.data_ultima_entrada\n" +
        "              , tmp1.cod_localizacao\n" +
        "              , tmp1.qtde_estoque_atual\n" +
        "              , tmp1.cod_familia\n" +
        "              , tmp1.cod_grupomaterial\n" +
        "         FROM  (\n" +
        "                 SELECT unidade.cod_unidade\n" +
        "                      , lm2.cod_material AS ucod_material\n" +
        "                      , lm2.cod_grupoempresa AS ucod_grupoempresa\n" +
        "                      , lm2.cod_empresa AS ucod_empresa\n" +
        "                      , lm2.cod_filial AS ucod_filial\n" +
        "                      , lm2.cod_almoxarifado AS ucod_almoxarifado\n" +
        "                      , material.descricao\n" +
        "                      , 0 AS total_entrada\n" +
        "                      , 0 AS total_saida\n" +
        "                      , CAST(NULL AS DATE) AS data_ultima_entrada\n" +
        "                      , lm2.cod_localizacao\n" +
        "                      , NVL((SELECT SUM(e.quantidade)\n" +
        "                             FROM   material.estoque e\n" +
        "                             WHERE  e.ano||TRIM(TO_CHAR(e.mes,'00')) = (SELECT MAX(e2.ano||TRIM(TO_CHAR(e2.mes,'00')))\n" +
        "                                                                        FROM   material.estoque e2\n" +
        "                                                                        WHERE  e2.ano||TRIM(TO_CHAR(e2.mes,'00')) <= TO_CHAR(" + dIni + ", 'YYYYMM')\n" +
        "                                                                        AND    e2.cod_almoxarifado                 = e.cod_almoxarifado\n" +
        "                                                                        AND    e2.cod_filial                       = e.cod_filial\n" +
        "                                                                        AND    e2.cod_empresa                      = e.cod_empresa\n" +
        "                                                                        AND    e2.cod_grupoempresa                 = e.cod_grupoempresa\n" +
        "                                                                        AND    e2.cod_material                     = e.cod_material)\n" +
        "                             AND    e.cod_almoxarifado = lm2.cod_almoxarifado\n" +
        "                             AND    e.cod_filial       = lm2.cod_filial\n" +
        "                             AND    e.cod_empresa      = lm2.cod_empresa\n" +
        "                             AND    e.cod_grupoempresa = lm2.cod_grupoempresa\n" +
        "                             AND    e.cod_material     = lm2.cod_material), 0) AS qtde_estoque_atual\n" +
        "                      , h2.cod_familia\n" +
        "                      , h2.cod_grupomaterial\n" +
        "                 FROM   material.unidade\n" +
        "                      , material.historicofamiliagrupo_material h2\n" +
        "                      , material.material\n" +
        "                      , material.materialporfilial mpf2\n" +
        "                      , material.localizacaomaterial lm2\n" +
        "                 WHERE  (SELECT COUNT(*)\n" +
        "                         FROM   material.estoque e3\n" +
        "                         WHERE  e3.cod_grupoempresa             = lm2.cod_grupoempresa\n" +
        "                         AND    e3.cod_empresa                  = lm2.cod_empresa\n" +
        "                         AND    e3.cod_filial                   = lm2.cod_filial\n" +
        "                         AND    e3.cod_material                 = lm2.cod_material\n" +
        "                         AND    e3.cod_almoxarifado             = lm2.cod_almoxarifado) > 0\n" +
        "                 AND    unidade.cod_unidade                         = material.cod_unidade\n" +
        "                 AND    mpf2.cod_material                           = lm2.cod_material\n" +
        "                 AND    mpf2.cod_filial                             = lm2.cod_filial\n" +
        "                 AND    mpf2.cod_empresa                            = lm2.cod_empresa\n" +
        "                 AND    mpf2.cod_grupoempresa                       = lm2.cod_grupoempresa\n" +
        "                 AND    " + dIni + "                              BETWEEN mpf2.datainicio AND NVL(mpf2.datatermino, TO_DATE('31/12/2999','DD/MM/YYYY'))\n" +
        "                 AND    lm2.cod_material                            = material.cod_material\n" +
        "                 AND    lm2.cod_filial                              = 1\n" +
        "                 AND    lm2.cod_empresa                             = 1\n" +
        "                 AND    lm2.cod_grupoempresa                        = 1\n" +
        "                 AND    " + dIni + "                              BETWEEN h2.data_inicio AND geral.fn_datanvl(h2.data_termino, 'F')\n" +
        "                 AND    h2.cod_material                             = material.cod_material\n" +
        "                 AND    (SELECT COUNT(*)\n" +
        "                         FROM   material.tmp_movimentacaomaterial t2\n" +
        "                         WHERE  t2.ucod_grupoempresa  = lm2.cod_grupoempresa\n" +
        "                         AND    t2.ucod_empresa       = lm2.cod_empresa\n" +
        "                         AND    t2.ucod_filial        = lm2.cod_filial\n" +
        "                         AND    t2.ucod_almoxarifado  = lm2.cod_almoxarifado\n" +
        "                         AND    t2.ucod_material      = lm2.cod_material\n" +
        "                         AND    t2.udata             >= " + dIni + "\n" +
        "                         AND    t2.udata             <= " + dFim + ") = 0\n" +
        "        ) tmp1\n" +
        "        WHERE  tmp1.qtde_estoque_atual    > 0\n" +
        "    ) mov_material\n" +
        ") tmp\n" +
        "ORDER BY tmp.ucod_material, tmp.descricao, tmp.ucod_grupoempresa, tmp.ucod_empresa, tmp.ucod_filial, tmp.ucod_almoxarifado";
    }

    // ── conversões ───────────────────────────────────────────────────────

    private static int inteiro(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return v == null ? 0 : (int) Double.parseDouble(v.toString().trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static BigDecimal numero(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString().trim()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static String texto(Object v) {
        return v == null ? "" : v.toString().trim();
    }
}
