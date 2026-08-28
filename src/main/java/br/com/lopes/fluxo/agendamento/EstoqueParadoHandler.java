package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.EstoqueParadoDAO;
import br.com.lopes.fluxo.dao.EstoqueParadoSnapshotDAO;
import br.com.lopes.fluxo.util.ChromiumPdfUtil;
import br.com.lopes.fluxo.util.EstoqueParadoCache;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
import br.com.lopes.fluxo.util.PlanilhaSimplesUtil;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * tipo_relatorio = "estoque_parado" — materiais com estoque parado há mais de
 * N dias sem entrada (padrão 90). Gera um PDF executivo (mesmo relatório de
 * estoque-parado-relatorio.html, que é quem de fato consulta o Oracle, monta
 * a comparação com a semana anterior e grava o snapshot — ver
 * {@link br.com.lopes.fluxo.servlet.EstoqueParadoServlet}) e um Excel
 * completo com a lista de itens, e manda os dois por WhatsApp.
 *
 * parametros esperado: {"diasLimite": 90} — opcional, usa o padrão se ausente.
 *
 * A consulta ao Oracle (pesada — roda MATERIAL.PR_POPULAR_MOVIMENTOMATERIAL
 * numa janela de 2 anos) é feita uma única vez aqui em Java, tanto para o
 * Excel quanto para o PDF: o resultado é guardado em
 * {@link br.com.lopes.fluxo.util.EstoqueParadoCache} antes de abrir a página
 * do relatório no Chromium, que buscaria os mesmos dados de novo (via
 * {@link br.com.lopes.fluxo.servlet.EstoqueParadoServlet}) se não achasse o
 * cache fresco.
 */
public class EstoqueParadoHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(EstoqueParadoHandler.class.getName());
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EstoqueParadoDAO dao = new EstoqueParadoDAO();
    private final EstoqueParadoSnapshotDAO snapshotDAO = new EstoqueParadoSnapshotDAO();

    private static String baseUrlInterno() {
        String v = System.getenv("APP_BASE_URL_INTERNO");
        return (v == null || v.isBlank()) ? "http://127.0.0.1:8080/fluxo-caixa" : v;
    }

    /** Orçamento generoso pro Chromium: a extração roda MATERIAL.PR_POPULAR_MOVIMENTOMATERIAL
     *  numa janela de 2 anos para todos os materiais — pode levar minutos, como no
     *  relatório de combustível. Fica abaixo do timeout de 8min do ChromiumPdfUtil. */
    private static final int TEMPO_VIRTUAL_MS = 6 * 60 * 1000;

    @Override
    public String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        int diasLimite = parametros.has("diasLimite") ? parametros.get("diasLimite").getAsInt() : EstoqueParadoDAO.DIAS_LIMITE_PADRAO;
        LocalDate hoje = LocalDate.now();

        // Consulta uma única vez aqui e guarda no cache: a página do PDF (aberta
        // pelo Chromium logo abaixo) busca os dados chamando o mesmo servlet, e
        // sem esse cache a extração pesada rodaria duas vezes — ver EstoqueParadoCache.
        List<Map<String, Object>> itens = dao.buscar(diasLimite);
        List<Map<String, Object>> anterior = snapshotDAO.buscarAnterior(hoje);
        Map<String, Object> comparacao = snapshotDAO.comparar(itens, anterior);
        snapshotDAO.salvarSnapshot(itens, hoje);
        EstoqueParadoCache.preencher(hoje, itens, comparacao);

        String destino = "estoque-parado-relatorio.html?diasLimite=" + diasLimite;
        String url = baseUrlInterno() + "/api/interno/sessao-relatorio?idUsuario=" + idUsuarioCriacao
                + "&redirect=" + URLEncoder.encode(destino, StandardCharsets.UTF_8);

        LOG.info("Gerando PDF do alerta de estoque parado: " + url);
        byte[] pdf = ChromiumPdfUtil.gerarPdf(url, TEMPO_VIRTUAL_MS);

        byte[] excel = montarExcel(itens, comparacao);

        BigDecimal totalAtual = (BigDecimal) comparacao.get("totalAtual");
        String legenda = "Alerta de Estoque Parado — " + itens.size() + " item(ns) sem entrada há mais de " + diasLimite
                + " dias, totalizando R$ " + totalAtual.setScale(2, java.math.RoundingMode.HALF_UP)
                + " (" + hoje.format(BR) + "), gerado automaticamente.";

        Exception ultimaFalha = null;
        List<String> falhas = new ArrayList<>();
        for (Map<String, Object> destinatario : destinatarios) {
            String telefone = String.valueOf(destinatario.get("telefone"));
            try {
                EvolutionApiUtil.enviarDocumento(telefone, pdf, "estoque-parado.pdf", legenda);
                EvolutionApiUtil.enviarDocumento(telefone, excel, "estoque-parado.xls",
                        "Planilha completa do alerta de estoque parado.", "application/vnd.ms-excel");
            } catch (Exception e) {
                ultimaFalha = e;
                falhas.add(descreverFalha(destinatario, telefone, e));
                LOG.log(Level.SEVERE, "Falha ao enviar alerta de estoque parado pro destinatário "
                        + destinatario.get("nome") + " (" + telefone + ")", e);
            }
        }
        if (!falhas.isEmpty()) {
            String quem = String.join(" | ", falhas);
            throw new RuntimeException(falhas.size() == destinatarios.size()
                    ? "Falha ao enviar para todos os " + falhas.size() + " destinatários — " + quem
                    : falhas.size() + " de " + destinatarios.size()
                      + " envios falharam (os demais foram entregues) — " + quem,
                    ultimaFalha);
        }
        return itens.size() + " item(ns) parado(s), total R$ " + totalAtual.setScale(2, java.math.RoundingMode.HALF_UP)
                + " — enviado para " + destinatarios.size() + " destinatário(s).";
    }

    @SuppressWarnings("unchecked")
    private static byte[] montarExcel(List<Map<String, Object>> itens, Map<String, Object> comparacao) {
        List<String> cabItens = List.of("Cód. Material", "Descrição", "Grupo", "Almoxarifado", "Localização",
                "Faixa", "Dias Parado", "Qtde Estoque", "Valor Total (R$)");
        List<List<Object>> linhasItens = new ArrayList<>();
        for (Map<String, Object> it : itens) {
            linhasItens.add(List.of(
                    n(it.get("codMaterial")), s(it.get("descricao")), s(it.get("descGrupoMaterial")),
                    n(it.get("codAlmoxarifado")), s(it.get("localizacao")), s(nomeFaixa((String) it.get("faixa"))),
                    n(it.get("diasParado")), n(it.get("qtdeEstoque")), n(it.get("valorTotal"))));
        }

        List<String> cabResumo = List.of("Indicador", "Valor");
        List<List<Object>> linhasResumo = new ArrayList<>();
        linhasResumo.add(List.of("Itens parados", itens.size()));
        linhasResumo.add(List.of("Valor total parado (R$)", n(comparacao.get("totalAtual"))));
        if (Boolean.TRUE.equals(comparacao.get("temAnterior"))) {
            linhasResumo.add(List.of("Valor total na execução anterior (R$)", n(comparacao.get("totalAnterior"))));
            linhasResumo.add(List.of("Variação de valor (R$)", n(comparacao.get("variacaoValor"))));
            linhasResumo.add(List.of("Variação (%)", n(comparacao.get("variacaoPercentual"))));
        }
        linhasResumo.add(List.of("Itens novos na lista", ((List<Object>) comparacao.get("entraram")).size()));
        linhasResumo.add(List.of("Itens que saíram da lista", ((List<Object>) comparacao.get("sairam")).size()));

        return PlanilhaSimplesUtil.gerar(List.of(
                new PlanilhaSimplesUtil.Aba("Resumo", cabResumo, linhasResumo),
                new PlanilhaSimplesUtil.Aba("Itens Parados", cabItens, linhasItens)
        ));
    }

    private static String nomeFaixa(String faixa) {
        if (faixa == null) return "";
        return switch (faixa) {
            case "91-180" -> "91 a 180 dias";
            case "181-365" -> "181 a 365 dias";
            default -> "Acima de 365 dias";
        };
    }

    private static Object n(Object v) { return v instanceof Number ? v : (v == null ? 0 : v); }
    private static String s(Object v) { return v == null ? "" : v.toString(); }

    private static String descreverFalha(Map<String, Object> destinatario, String telefone, Exception e) {
        Object nome = destinatario.get("nome");
        String motivo = e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName() : e.getMessage().trim();
        return (nome == null ? "sem nome" : String.valueOf(nome).trim()) + " (" + telefone + "): " + motivo;
    }
}
