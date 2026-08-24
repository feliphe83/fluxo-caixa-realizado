package br.com.lopes.fluxo.dao;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import br.com.lopes.fluxo.util.RowMapperUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fechamento de Fretes de Transporte de Pessoal (Depto. Agrícola).
 *
 * Reproduz, por prestador de serviço, o que a planilha "Fechamento Fretes.xlsx"
 * preenchia à mão nas células amarelas — mas puxando do ERP o que já existe lá:
 *
 *  - Valor Bruto R$  = soma do valor_total dos apontamentos de terceiro
 *                      (automotivo.apontamentoterceiro), a mesma base que o
 *                      Controle de Serviços usa no bloco "TRANSPORTE DE PESSOAL".
 *  - Diárias         = soma da quantidade apontada (it.quantidade).
 *  - Kms rodados     = soma de (kmhs_final - kmhs_inicial) dos itens.
 *  - Colaboradores   = soma de nr_pessoas (pessoas transportadas) dos itens.
 *  - Nº Equipamentos = equipamentos distintos do prestador no período.
 *  - Litros / Valor Combustível = do abastecimento, por fornecedor, reaproveitando
 *                      {@link AgroCombustivelDAO#buscarPorFornecedor} (diesel).
 *
 * O prestador é o fornecedor do apontamento (a.cod_fornecedor) — a transportadora,
 * não o proprietário da fazenda. O nome sai de material.fornecedor -> rh.pessoa,
 * e é por cod_fornecedor que o combustível é casado com o frete.
 *
 * Os derivados (R$/km, R$/diária, kms/litro, %, valor líquido = bruto - combustível)
 * são calculados no navegador, sobre estes campos. Kms e colaboradores chegam
 * preenchidos, mas seguem editáveis na tela para ajuste do fechamento.
 *
 * Empresa fixada em 1/1/1, como nas demais consultas da intranet (usuário de
 * serviço próprio, sem a geral.fn_autorizacao_empresa do ERP).
 */
public class FechamentoFreteDAO {

    private static final Logger LOG = Logger.getLogger(FechamentoFreteDAO.class.getName());

    private final AgroCombustivelDAO combustivelDAO = new AgroCombustivelDAO();

    /**
     * @param dataIni yyyy-MM-dd (obrigatório)
     * @param dataFim yyyy-MM-dd (obrigatório)
     * @param contrato opcional; quando informado, filtra os apontamentos por
     *                 numerocontrato (a coluna Contrato passa a listar só ele).
     * @return uma linha por prestador com os campos vindos do Oracle já casados
     *         com o combustível; ordenado por valor bruto desc.
     */
    public List<Map<String, Object>> resumoPorPrestador(String dataIni, String dataFim, String contrato) {
        validarData(dataIni);
        validarData(dataFim);
        String filtroContrato = sanitizarContrato(contrato);

        // 1) Frete de transporte de pessoal por prestador.
        List<Map<String, Object>> frete = executar(sqlTransporte(dataIni, dataFim, filtroContrato));

        // 2) Combustível (diesel) por fornecedor, indexado por cod_fornecedor.
        Map<Long, Map<String, Object>> combPorForn = new LinkedHashMap<>();
        for (Map<String, Object> c : combustivelDAO.buscarPorFornecedor(dataIni, dataFim, "diesel")) {
            Long cod = paraLong(c.get("cod_fornecedor"));
            if (cod != null) combPorForn.put(cod, c);
        }

        // 3) Casa um no outro por cod_fornecedor.
        List<Map<String, Object>> saida = new ArrayList<>();
        for (Map<String, Object> f : frete) {
            Long cod = paraLong(f.get("cod_fornecedor"));

            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("codFornecedor", cod);
            linha.put("contrato", texto(f.get("contratos"), "—"));
            linha.put("prestador", texto(f.get("prestador"), "Fornecedor " + (cod == null ? "?" : cod)));
            linha.put("nEquip", inteiro(f.get("n_equip")));
            linha.put("diarias", numero(f.get("diarias")));
            linha.put("kms", numero(f.get("kms")));
            linha.put("colab", numero(f.get("colab")));
            linha.put("valorBruto", numero(f.get("valor_bruto")));

            Map<String, Object> c = cod == null ? null : combPorForn.get(cod);
            linha.put("litros", c == null ? BigDecimal.ZERO : numero(c.get("total_litros")));
            linha.put("valorCombustivel", c == null ? BigDecimal.ZERO : numero(c.get("valor_total")));

            saida.add(linha);
        }
        return saida;
    }

    // ── SQL ───────────────────────────────────────────────────────────────────

    /**
     * Datas inline como 'DD/MM/YYYY' (validadas antes) — é como o ERP compara
     * a.dt_apontamento no bloco de transporte de pessoal já em produção.
     *
     * @param contrato já sanitizado (só letras/dígitos/-/./); "" = sem filtro.
     *        A coluna "contratos" lista os contratos distintos do prestador no
     *        período (respeitando o mesmo filtro), sem LISTAGG DISTINCT para
     *        rodar em qualquer versão do Oracle.
     */
    private static String sqlTransporte(String dataIni, String dataFim, String contrato) {
        String dIni = "'" + isoParaDDMMYYYY(dataIni) + "'";
        String dFim = "'" + isoParaDDMMYYYY(dataFim) + "'";
        String fA  = contrato.isEmpty() ? "" : " and upper(to_char(a.numerocontrato))  = upper('" + contrato + "')";
        String fA2 = contrato.isEmpty() ? "" : " and upper(to_char(a2.numerocontrato)) = upper('" + contrato + "')";
        return
            "select a.cod_fornecedor, " +
            "       (select max(p.nome) from material.fornecedor f, rh.pessoa p " +
            "          where f.cod_fornecedor = a.cod_fornecedor and p.cod_pessoa = f.cod_pessoa) prestador, " +
            "       (select listagg(c.nc, ', ') within group (order by c.nc) from ( " +
            "          select distinct a2.numerocontrato nc from automotivo.apontamentoterceiro a2 " +
            "           where a2.cod_grupoempresa = 1 and a2.cod_empresa = 1 and a2.cod_filial = 1 " +
            "             and a2.cod_fornecedor = a.cod_fornecedor " +
            "             and a2.dt_apontamento between " + dIni + " and " + dFim + fA2 +
            "             and a2.numerocontrato is not null) c) contratos, " +
            "       count(distinct it.cod_equipamento) n_equip, " +
            "       sum(it.quantidade)  diarias, " +
            "       sum(nvl(it.kmhs_final, 0) - nvl(it.kmhs_inicial, 0)) kms, " +
            "       sum(nvl(it.nr_pessoas, 0)) colab, " +
            "       sum(it.valor_total) valor_bruto " +
            "  from automotivo.apontamentoterceiro a, automotivo.itens_apontamentoterceiro it " +
            " where a.cod_grupoempresa = 1 and a.cod_empresa = 1 and a.cod_filial = 1 " +
            "   and a.ano_apontamento    = it.ano_apontamento " +
            "   and a.numero_apontamento = it.numero_apontamento " +
            "   and a.dt_apontamento between " + dIni + " and " + dFim + " " + fA +
            " group by a.cod_fornecedor " +
            " order by valor_bruto desc";
    }

    /** Mantém só o que é seguro num número de contrato; evita injeção no inline. */
    private static String sanitizarContrato(String contrato) {
        if (contrato == null) return "";
        return contrato.trim().replaceAll("[^A-Za-z0-9/.\\-]", "");
    }

    // ── Execução / conversões ─────────────────────────────────────────────────

    private List<Map<String, Object>> executar(String sql) {
        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return RowMapperUtil.toList(rs);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro no fechamento de fretes: " + e.getMessage(), e);
            throw new RuntimeException("Falha na consulta de fretes: " + e.getMessage(), e);
        }
    }

    private static void validarData(String iso) {
        if (iso == null || !iso.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Data inválida (esperado yyyy-MM-dd): " + iso);
        }
    }

    /** yyyy-MM-dd -> DD/MM/YYYY. A data já vem validada por regex. */
    private static String isoParaDDMMYYYY(String iso) {
        String[] p = iso.split("-");
        return p[2] + "/" + p[1] + "/" + p[0];
    }

    private static Long paraLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

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

    private static String texto(Object v, String padrao) {
        if (v == null) return padrao;
        String s = v.toString().trim();
        return s.isEmpty() ? padrao : s;
    }
}
