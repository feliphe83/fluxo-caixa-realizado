package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Alerta de Estoque Parado — materiais com estoque, sem movimentação há mais
 * de {@code diasLimite} dias (padrão 90).
 *
 * REESCRITO a partir da consulta de referência REAL do relatório gerencial de
 * "material não movimentado" (a mesma família de relatório que o ERP já usa
 * para isso), depois que a primeira versão — construída em cima de uma
 * consulta genérica de movimentação, com {@code FN_VLR_INVENTARIO_MATERIAL}
 * pra achar o custo — voltou com o valor muito maior do que o esperado.
 *
 * Duas diferenças que corrigem o valor:
 *
 *  1) "Dias sem movimento" agora vem de {@code MATERIAL.FN_VERIFICA_MATNAOMOVIMENTADO}
 *     — a função que o próprio ERP usa pra isso — em vez de eu calcular a
 *     data da última entrada na mão a partir de uma janela de movimentação.
 *     Mais simples e mais confiável.
 *  2) O valor do estoque agora é {@code saldo × custo médio}, os dois lidos
 *     direto de {@code material.estoque} e {@code material.customedio} (o
 *     mês mais recente até hoje) — sem passar por
 *     {@code FN_VLR_INVENTARIO_MATERIAL}, que aparentemente não devolve o
 *     custo unitário na mesma base que a consulta original assumia (daí o
 *     valor inflado).
 *
 * Como consequência boa: não precisa mais rodar
 * {@code MATERIAL.PR_POPULAR_MOVIMENTOMATERIAL} (a parte mais pesada da
 * versão anterior) — a extração inteira é uma única consulta.
 *
 * Parâmetros nomeados da consulta original (grupo/empresa/filial, família,
 * subgrupo, tipo de material, almoxarifado) viraram literais fixos ou foram
 * removidos quando eram sempre "todos" (os DECODE(x,x,coluna,x) e o
 * CASE ... WHEN 0=0 THEN 1 da consulta original são tautologias — sempre
 * verdadeiras, não filtram nada).
 */
public class EstoqueParadoDAO {

    private static final Logger LOG = Logger.getLogger(EstoqueParadoDAO.class.getName());

    /** Quantidade de dias parado padrão a considerar como candidatos ao alerta. */
    public static final int DIAS_LIMITE_PADRAO = 90;

    /**
     * @param diasLimite abaixo disso o material não entra no alerta (padrão 90)
     * @return itens com estoque parado há mais de diasLimite dias, ordenados
     *         por valor em estoque decrescente (dentro de cada almoxarifado).
     */
    public List<Map<String, Object>> buscar(int diasLimite) {
        LocalDate hoje = LocalDate.now();
        List<Map<String, Object>> bruto = executar(sqlExtracao(diasLimite, hoje.getYear(), hoje.getMonthValue()));

        List<Map<String, Object>> saida = new ArrayList<>();
        for (Map<String, Object> r : bruto) {
            int diasParado = inteiro(r.get("dias"));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("codMaterial", inteiro(r.get("cod_material")));
            item.put("codAlmoxarifado", inteiro(r.get("cod_almoxarifado")));
            item.put("descricao", texto(r.get("descricao")));
            item.put("descFamilia", texto(r.get("descricaofamiliamaterial")));
            item.put("descGrupoMaterial", texto(r.get("descricaogrupomaterial")));
            item.put("localizacao", texto(r.get("localizacao")));
            item.put("qtdeEstoque", numero(r.get("sdo_material")));
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
     * Adaptação da consulta de referência do relatório de material não
     * movimentado: grupoempresa/empresa/filial fixados em 1 (como no resto
     * da intranet), família/grupo/tipo de material/almoxarifado abertos
     * ("todos" — os filtros correspondentes na consulta original eram
     * tautologias) e ano/mês de referência = hoje (a consulta original
     * rodava com um ano/mês fixo, escolhido na hora de gerar o relatório).
     */
    private static String sqlExtracao(int diasLimite, int ano, int mes) {
        return "select tmp.cod_familia\n" +
        "     , tmp.descricaofamiliamaterial\n" +
        "     , tmp.cod_grupomaterial\n" +
        "     , tmp.descricaogrupomaterial\n" +
        "     , tmp.cod_almoxarifado\n" +
        "     , tmp.descricaoalmoxarifado\n" +
        "     , tmp.cod_material\n" +
        "     , tmp.descricao\n" +
        "     , tmp.localizacao\n" +
        "     , tmp.dias\n" +
        "     , tmp.sdo_material\n" +
        "     , tmp.cm_material\n" +
        "     , round(tmp.sdo_material * tmp.cm_material, 2) vlr_total\n" +
        "     , tmp.curva_xyz\n" +
        "from (\n" +
        "    select tmp1.*\n" +
        "         , material.fn_verifica_matnaomovimentado(tmp1.cod_grupoempresa, tmp1.cod_empresa, tmp1.cod_filial,\n" +
        "                                                   tmp1.cod_material, " + diasLimite + ", " + ano + ", " + mes + ",\n" +
        "                                                   tmp1.cod_almoxarifado, 'S') dias\n" +
        "    from (\n" +
        "        select histfamgru.cod_familia\n" +
        "             , familiamaterial.descricao descricaofamiliamaterial\n" +
        "             , histfamgru.cod_grupomaterial\n" +
        "             , grupomaterial.descricao descricaogrupomaterial\n" +
        "             , localizacaomaterial.cod_filial\n" +
        "             , localizacaomaterial.cod_empresa\n" +
        "             , localizacaomaterial.cod_grupoempresa\n" +
        "             , material.cod_material\n" +
        "             , material.descricao\n" +
        "             , localizacaomaterial.cod_almoxarifado\n" +
        "             , almoxarifado.descricaoalmoxarifado\n" +
        "             , case when instr(localizacaopornivel.divisoes, '. ', 2) > 0 then\n" +
        "                         substr(localizacaopornivel.divisoes, 1, instr(localizacaopornivel.divisoes, '. ', 2) - 1)\n" +
        "                    else localizacaopornivel.divisoes\n" +
        "               end localizacao\n" +
        "             , (select sum(nvl(estoque.quantidade, 0))\n" +
        "                from   material.estoque\n" +
        "                where  estoque.cod_grupoempresa = localizacaomaterial.cod_grupoempresa\n" +
        "                and    estoque.cod_empresa      = localizacaomaterial.cod_empresa\n" +
        "                and    estoque.cod_filial        = localizacaomaterial.cod_filial\n" +
        "                and    estoque.cod_material      = localizacaomaterial.cod_material\n" +
        "                and    ltrim(to_char(estoque.ano)) || ltrim(to_char(estoque.mes,'00')) =\n" +
        "                       (select max(ltrim(to_char(e.ano)) || ltrim(to_char(e.mes,'00')))\n" +
        "                        from   material.estoque e\n" +
        "                        where  ltrim(to_char(e.ano)) || ltrim(to_char(e.mes,'00')) <= ltrim(to_char(" + ano + ",'0000')) || ltrim(to_char(" + mes + ",'00'))\n" +
        "                        and    e.cod_almoxarifado = localizacaomaterial.cod_almoxarifado\n" +
        "                        and    e.cod_material     = localizacaomaterial.cod_material\n" +
        "                        and    e.cod_filial       = localizacaomaterial.cod_filial\n" +
        "                        and    e.cod_empresa      = localizacaomaterial.cod_empresa\n" +
        "                        and    e.cod_grupoempresa = localizacaomaterial.cod_grupoempresa)\n" +
        "                and    estoque.cod_almoxarifado  = localizacaomaterial.cod_almoxarifado) sdo_material\n" +
        "             , (select sum(nvl(customedio.custo_medio, 0))\n" +
        "                from   material.customedio\n" +
        "                where  customedio.cod_grupoempresa = localizacaomaterial.cod_grupoempresa\n" +
        "                and    customedio.cod_empresa      = localizacaomaterial.cod_empresa\n" +
        "                and    customedio.cod_filial        = localizacaomaterial.cod_filial\n" +
        "                and    customedio.cod_material      = localizacaomaterial.cod_material\n" +
        "                and    ltrim(to_char(customedio.ano)) || ltrim(to_char(customedio.mes,'00')) =\n" +
        "                       (select max(ltrim(to_char(c.ano)) || ltrim(to_char(c.mes,'00')))\n" +
        "                        from   material.customedio c\n" +
        "                        where  ltrim(to_char(c.ano)) || ltrim(to_char(c.mes,'00')) <= ltrim(to_char(" + ano + ",'0000')) || ltrim(to_char(" + mes + ",'00'))\n" +
        "                        and    c.cod_material     = localizacaomaterial.cod_material\n" +
        "                        and    c.cod_filial       = localizacaomaterial.cod_filial\n" +
        "                        and    c.cod_empresa      = localizacaomaterial.cod_empresa\n" +
        "                        and    c.cod_grupoempresa = localizacaomaterial.cod_grupoempresa)\n" +
        "               ) cm_material\n" +
        "             , materialporfilial.curva_xyz\n" +
        "        from   material.historicofamiliagrupo_material histfamgru\n" +
        "             , material.material\n" +
        "             , material.materialporfilial\n" +
        "             , material.localizacaomaterial\n" +
        "             , material.localizacaopornivel\n" +
        "             , material.almoxarifado\n" +
        "             , material.grupomaterial\n" +
        "             , material.familiamaterial\n" +
        "        where  familiamaterial.cod_familia             = histfamgru.cod_familia\n" +
        "        and    grupomaterial.cod_familia               = histfamgru.cod_familia\n" +
        "        and    grupomaterial.cod_grupomaterial         = histfamgru.cod_grupomaterial\n" +
        "        and    almoxarifado.cod_almoxarifado           = localizacaomaterial.cod_almoxarifado\n" +
        "        and    almoxarifado.cod_filial                 = localizacaomaterial.cod_filial\n" +
        "        and    almoxarifado.cod_empresa                = localizacaomaterial.cod_empresa\n" +
        "        and    almoxarifado.cod_grupoempresa           = localizacaomaterial.cod_grupoempresa\n" +
        "        and    localizacaopornivel.cod_localizacao  (+)= localizacaomaterial.cod_localizacao\n" +
        "        and    localizacaopornivel.cod_almoxarifado (+)= localizacaomaterial.cod_almoxarifado\n" +
        "        and    localizacaopornivel.cod_filial       (+)= localizacaomaterial.cod_filial\n" +
        "        and    localizacaopornivel.cod_empresa      (+)= localizacaomaterial.cod_empresa\n" +
        "        and    localizacaopornivel.cod_grupoempresa (+)= localizacaomaterial.cod_grupoempresa\n" +
        "        and    localizacaomaterial.cod_material        = materialporfilial.cod_material\n" +
        "        and    localizacaomaterial.cod_filial          = materialporfilial.cod_filial\n" +
        "        and    localizacaomaterial.cod_empresa         = materialporfilial.cod_empresa\n" +
        "        and    localizacaomaterial.cod_grupoempresa    = materialporfilial.cod_grupoempresa\n" +
        "        and    materialporfilial.situacao              = 'A'\n" +
        "        and    trunc(sysdate) between materialporfilial.datainicio and nvl(materialporfilial.datatermino, trunc(sysdate))\n" +
        "        and    materialporfilial.cod_material          = material.cod_material\n" +
        "        and    materialporfilial.cod_filial            = 1\n" +
        "        and    materialporfilial.cod_empresa           = 1\n" +
        "        and    materialporfilial.cod_grupoempresa      = 1\n" +
        "        and    trunc(sysdate) between histfamgru.data_inicio and geral.fn_datanvl(histfamgru.data_termino, 'F')\n" +
        "        and    histfamgru.cod_material                 = material.cod_material\n" +
        "    ) tmp1\n" +
        ") tmp\n" +
        "where  tmp.dias >= " + diasLimite + "\n" +
        "and    tmp.sdo_material > 0\n" +
        "order by tmp.cod_almoxarifado, tmp.descricaogrupomaterial, tmp.descricao";
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
