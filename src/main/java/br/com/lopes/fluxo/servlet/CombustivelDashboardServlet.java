package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AgroCombustivelDAO;
import br.com.lopes.fluxo.util.ClasseOperativaCache;
import br.com.lopes.fluxo.util.DataParamUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API do dashboard de consumo de combustível (combustivel-dashboard.html) —
 * front-end web autenticado por sessão (AuthFilter), diferente da rota
 * /api/agricola/combustivel, que é a ferramenta do Dr. Alfredo via chave de
 * API. Ambas usam a mesma consulta base (AgroCombustivelDAO).
 *
 * GET /api/combustivel-dashboard?dataIni=yyyy-MM-dd&dataFim=yyyy-MM-dd
 *        [&combustivel=trecho] [&codEquipamento=N] [&codTipoCliente=N] [&codFazenda=N]
 *
 * Layout no formato do "Relatório Executivo · Consumo de Diesel" do
 * Departamento Agrícola: resumo executivo, evolução semanal do preço,
 * distribuição por área de negócio/atividade, consumo semanal por classe
 * operativa e rankings de equipamentos próprios/terceiros.
 *
 * Próprio x Terceiro: pelo proprietário histórico do equipamento
 * (automotivo.histproprietarioequip, já presente na consulta como
 * cod_proprietario) — sem registro de proprietário (LEFT JOIN nulo) é
 * equipamento sempre da usina (Próprio); com um fornecedor registrado como
 * dono naquele período, é Terceiro — exceto quando esse "dono" é a própria
 * "USINA SANTA CLOTILDE S/A" (às vezes registrada em
 * histproprietarioequip), que conta como Próprio em todo o dashboard.
 *
 * Classe Operativa (só na matriz semanal, seção 4): de-para administrado
 * (fc_depara_classeoperativa, tela "De-Para Classe Operativa") de
 * cod_equipamento (unidade física, não o modelo abstrato) → categoria ampla
 * (Trator, Caminhão Apoio, Ônibus…) — equipamento sem de-para cadastrado cai
 * em "Não Classificado". O de-para já distingue próprio de terceiro no nome
 * da própria classe quando é o caso (ex.: "Trator" x "Trator Terceiro"); não
 * há sufixo automático somado por cima.
 *
 * "Grupo Equipamento" (Top 10 Próprios) e "Atividade Principal" (Top 10
 * Terceiros): direto de desc_atividade (Objeto de Custo do Oracle, mesmo
 * campo usado em "Top 6 Atividades") — não dependem do de-para acima.
 *
 * "Semana operacional": blocos de 7 dias a partir de dataIni (S1, S2, …),
 * a última podendo ser parcial se o período não fechar em múltiplo de 7.
 *
 * Preço médio semanal ("Evolução do Preço"): vem de
 * material.itensrequisicaomaterial.vrcustounitario na data real de
 * retirada — não do valor_unitario da consulta principal
 * (posto.f_preco_combustivel), que sempre devolve o preço vigente hoje
 * aplicado retroativamente a qualquer abastecimento antigo.
 *
 * A agregação é feita aqui (e não no Oracle) porque o dashboard precisa de
 * vários cortes do mesmo resultado — uma única execução da consulta pesada
 * alimenta todos.
 */
@WebServlet("/api/combustivel-dashboard")
public class CombustivelDashboardServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(CombustivelDashboardServlet.class.getName());
    private static final DateTimeFormatter FMT_CURTO = DateTimeFormatter.ofPattern("dd/MM");

    /** Máximo de linhas de detalhe devolvidas para a tabela/export da tela. */
    private static final int MAX_DETALHE = 2000;
    private static final int TOP_ATIVIDADES = 6;
    private static final int TOP_RANKING = 10;

    private final Gson gson = new Gson();
    private final AgroCombustivelDAO dao = new AgroCombustivelDAO();

    private static final class Acc {
        double litros = 0;
        double valor = 0;
        Set<Object> equipamentos = new HashSet<>();
        void add(double l, double v, Object equip) {
            litros += l; valor += v;
            if (equip != null) equipamentos.add(equip);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            String dataIni = DataParamUtil.normalizar(req.getParameter("dataIni"));
            String dataFim = DataParamUtil.normalizar(req.getParameter("dataFim"));
            if (dataIni == null || dataFim == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"ok\":false,\"erro\":\"Informe o período (dataIni e dataFim)\"}");
                return;
            }

            Integer codEquipamento = lerInteiro(req.getParameter("codEquipamento"));
            Integer codTipoCliente = lerInteiro(req.getParameter("codTipoCliente"));
            Integer codFazenda = lerInteiro(req.getParameter("codFazenda"));
            String combustivel = req.getParameter("combustivel");

            List<Map<String, Object>> linhas = dao.buscarDetalhadoDashboard(
                    dataIni, dataFim, codEquipamento, codTipoCliente, combustivel, codFazenda);

            // Abastecimento sem equipamento vinculado (retirada por placa/pessoa,
            // sem veículo) não entra no dashboard — desconsiderado de tudo:
            // KPIs, séries, rankings e detalhamento.
            linhas.removeIf(l -> !preenchido(l.get("cod_equipamento")));

            List<LocalDate[]> semanas = montarSemanas(LocalDate.parse(dataIni), LocalDate.parse(dataFim));
            double[] precoHistoricoPorSemana = buscarPrecoHistoricoPorSemana(linhas, dataIni, dataFim, semanas);

            JsonObject resultado = new JsonObject();
            resultado.addProperty("ok", true);
            resultado.addProperty("totalLinhas", linhas.size());
            resultado.add("semanas", jsonSemanas(semanas));
            resultado.add("serieSemanal", montarSerieSemanal(linhas, semanas, precoHistoricoPorSemana));
            resultado.add("kpis", montarKpis(linhas, semanas));
            resultado.add("porAreaNegocio", montarPorAreaNegocio(linhas, semanas));
            resultado.add("porAtividade", montarPorAtividade(linhas));
            resultado.add("porClasseOperativa", montarPorClasseOperativa(linhas, semanas));
            resultado.add("topProprios", montarTopProprios(linhas));
            resultado.add("frotaPropriaReal", montarFrotaPropriaReal(linhas));
            resultado.add("topTerceiros", montarTopTerceiros(linhas));
            resultado.add("opcoes", montarOpcoes(linhas));
            resultado.addProperty("truncadoDetalhe", linhas.size() > MAX_DETALHE);
            resultado.add("detalhe", gson.toJsonTree(montarDetalhe(linhas)));

            out.print(gson.toJson(resultado));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no dashboard de combustível", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    // ── Semanas operacionais ─────────────────────────────────────────────

    private static List<LocalDate[]> montarSemanas(LocalDate ini, LocalDate fim) {
        List<LocalDate[]> semanas = new ArrayList<>();
        LocalDate cursor = ini;
        while (!cursor.isAfter(fim)) {
            LocalDate fimSemana = cursor.plusDays(6);
            if (fimSemana.isAfter(fim)) fimSemana = fim;
            semanas.add(new LocalDate[]{cursor, fimSemana});
            cursor = cursor.plusDays(7);
        }
        if (semanas.isEmpty()) semanas.add(new LocalDate[]{ini, fim});
        return semanas;
    }

    private static int semanaDe(String dataStr, LocalDate ini, int totalSemanas) {
        if (dataStr == null || dataStr.length() < 10) return -1;
        LocalDate data;
        try {
            data = LocalDate.parse(dataStr.substring(0, 10));
        } catch (Exception e) {
            return -1;
        }
        long dias = ChronoUnit.DAYS.between(ini, data);
        if (dias < 0) return 0;
        int idx = (int) (dias / 7);
        return Math.min(idx, totalSemanas - 1);
    }

    private JsonArray jsonSemanas(List<LocalDate[]> semanas) {
        JsonArray arr = new JsonArray();
        for (int i = 0; i < semanas.size(); i++) {
            JsonObject o = new JsonObject();
            o.addProperty("numero", i + 1);
            o.addProperty("inicio", semanas.get(i)[0].toString());
            o.addProperty("fim", semanas.get(i)[1].toString());
            o.addProperty("label", semanas.get(i)[0].format(FMT_CURTO));
            arr.add(o);
        }
        return arr;
    }

    // ── Série semanal (volume, custo, preço médio) ──────────────────────

    private JsonArray montarSerieSemanal(List<Map<String, Object>> linhas, List<LocalDate[]> semanas,
                                          double[] precoHistoricoPorSemana) {
        LocalDate ini = semanas.get(0)[0];
        int n = semanas.size();
        double[] litrosPorSemana = new double[n];
        double[] valorPorSemana = new double[n];

        for (Map<String, Object> l : linhas) {
            int idx = semanaDe(strOf(l.get("data")), ini, n);
            if (idx < 0) continue;
            litrosPorSemana[idx] += num(l.get("qtde_litros"));
            valorPorSemana[idx] += num(l.get("valor_total"));
        }

        JsonArray arr = new JsonArray();
        for (int i = 0; i < n; i++) {
            JsonObject o = new JsonObject();
            o.addProperty("numero", i + 1);
            o.addProperty("label", semanas.get(i)[0].format(FMT_CURTO));
            o.addProperty("volume", arred(litrosPorSemana[i]));
            o.addProperty("custo", arred(valorPorSemana[i]));
            o.addProperty("precoMedio", arred(precoHistoricoPorSemana[i]));
            arr.add(o);
        }
        return arr;
    }

    /**
     * Preço médio semanal real (material.itensrequisicaomaterial.vrcustounitario
     * na data de retirada) — usado em "Evolução do Preço". Diferente do
     * valor_unitario da consulta principal (posto.f_preco_combustivel), que
     * sempre devolve o preço vigente hoje, não o preço da época de cada
     * abastecimento.
     */
    private double[] buscarPrecoHistoricoPorSemana(List<Map<String, Object>> linhas, String dataIni, String dataFim,
                                                    List<LocalDate[]> semanas) {
        int n = semanas.size();
        double[] soma = new double[n];
        int[] qtde = new int[n];

        Set<Integer> codMateriais = new HashSet<>();
        for (Map<String, Object> l : linhas) {
            Object cod = l.get("cod_combustivel");
            if (cod instanceof Number num) codMateriais.add(num.intValue());
        }
        if (codMateriais.isEmpty()) return soma;

        LocalDate ini = semanas.get(0)[0];
        List<Map<String, Object>> custos = dao.buscarCustoHistorico(dataIni, dataFim, codMateriais);
        for (Map<String, Object> c : custos) {
            int idx = semanaDe(strOf(c.get("dataretirada")), ini, n);
            if (idx < 0) continue;
            double custo = num(c.get("vrcustounitario"));
            if (custo <= 0) continue;
            soma[idx] += custo;
            qtde[idx]++;
        }

        double[] media = new double[n];
        for (int i = 0; i < n; i++) {
            media[i] = qtde[i] == 0 ? 0 : soma[i] / qtde[i];
        }
        return media;
    }

    // ── KPIs (geral + Próprio x Terceiro) ────────────────────────────────

    private JsonObject montarKpis(List<Map<String, Object>> linhas, List<LocalDate[]> semanas) {
        Acc geral = new Acc(), propria = new Acc(), terceiro = new Acc();

        Set<String> proprietariosTerceiro = new HashSet<>();
        for (Map<String, Object> l : linhas) {
            double litros = num(l.get("qtde_litros"));
            double valor = num(l.get("valor_total"));
            Object equip = l.get("cod_equipamento");
            geral.add(litros, valor, equip);
            if (ehTerceiro(l)) {
                terceiro.add(litros, valor, equip);
                String prop = strOf(l.get("nome_proprietario"));
                if (!prop.isBlank()) proprietariosTerceiro.add(prop);
            } else {
                propria.add(litros, valor, equip);
            }
        }

        int numSemanas = semanas.size();

        JsonObject kpis = new JsonObject();
        kpis.addProperty("volumeTotal", arred(geral.litros));
        kpis.addProperty("custoTotal", arred(geral.valor));
        kpis.addProperty("precoMedioPonderado", geral.litros == 0 ? 0 : arred(geral.valor / geral.litros));
        kpis.addProperty("mediaSemanalVolume", arred(geral.litros / numSemanas));
        kpis.addProperty("mediaSemanalCusto", arred(geral.valor / numSemanas));
        kpis.addProperty("equipamentosMonitorados", propria.equipamentos.size() + terceiro.equipamentos.size());
        kpis.add("propria", blocoKpi(propria, geral.litros, numSemanas));
        JsonObject terceiroJson = blocoKpi(terceiro, geral.litros, numSemanas);
        terceiroJson.addProperty("proprietariosAtivos", proprietariosTerceiro.size());
        kpis.add("terceiro", terceiroJson);
        return kpis;
    }

    private JsonObject blocoKpi(Acc a, double volumeGeral, int numSemanas) {
        JsonObject o = new JsonObject();
        o.addProperty("equipamentos", a.equipamentos.size());
        o.addProperty("volume", arred(a.litros));
        o.addProperty("custo", arred(a.valor));
        o.addProperty("precoMedio", a.litros == 0 ? 0 : arred(a.valor / a.litros));
        o.addProperty("mediaSemanalVolume", arred(a.litros / numSemanas));
        o.addProperty("participacaoVolume", volumeGeral == 0 ? 0 : arred(a.litros / volumeGeral * 100));
        return o;
    }

    /**
     * Terceiro = existe proprietário histórico registrado para o equipamento
     * (cod_proprietario preenchido) E esse proprietário não é a própria
     * usina — alguns equipamentos têm "USINA SANTA CLOTILDE S/A" registrada
     * em automotivo.histproprietarioequip, o que não é um terceiro de
     * verdade; esses contam como Próprio em todo o dashboard.
     */
    private static boolean ehTerceiro(Map<String, Object> l) {
        if (!preenchido(l.get("cod_proprietario"))) return false;
        return !ehAPropriaUsina(strOf(l.get("nome_proprietario")));
    }

    // ── Por Área de Negócio (totais + série semanal, p/ o gráfico empilhado) ──

    private JsonArray montarPorAreaNegocio(List<Map<String, Object>> linhas, List<LocalDate[]> semanas) {
        LocalDate ini = semanas.get(0)[0];
        int n = semanas.size();

        Map<String, double[]> litrosPorSemana = new LinkedHashMap<>(); // área -> litros[semana]
        Map<String, Double> valorTotal = new LinkedHashMap<>();
        double totalLitros = 0;

        for (Map<String, Object> l : linhas) {
            String area = rotulo(l, "cod_negocio", "desc_negocio");
            if (area == null) area = "Não Classificado";
            double litros = num(l.get("qtde_litros"));
            double valor = num(l.get("valor_total"));
            int idx = semanaDe(strOf(l.get("data")), ini, n);

            double[] serie = litrosPorSemana.computeIfAbsent(area, k -> new double[n]);
            if (idx >= 0) serie[idx] += litros;
            valorTotal.merge(area, valor, Double::sum);
            totalLitros += litros;
        }

        double totalLitrosFinal = totalLitros;
        List<Map.Entry<String, double[]>> ordenado = new ArrayList<>(litrosPorSemana.entrySet());
        ordenado.sort((a, b) -> Double.compare(soma(b.getValue()), soma(a.getValue())));

        JsonArray arr = new JsonArray();
        for (Map.Entry<String, double[]> e : ordenado) {
            double volume = soma(e.getValue());
            JsonObject o = new JsonObject();
            o.addProperty("nome", e.getKey());
            o.addProperty("volume", arred(volume));
            o.addProperty("custo", arred(valorTotal.getOrDefault(e.getKey(), 0.0)));
            o.addProperty("participacao", totalLitrosFinal == 0 ? 0 : arred(volume / totalLitrosFinal * 100));
            JsonArray serieArr = new JsonArray();
            for (double v : e.getValue()) serieArr.add(arred(v));
            o.add("porSemana", serieArr);
            arr.add(o);
        }
        return arr;
    }

    // ── Top Atividades ────────────────────────────────────────────────────

    private JsonArray montarPorAtividade(List<Map<String, Object>> linhas) {
        Map<String, double[]> mapa = new LinkedHashMap<>();
        double totalLitros = 0;
        for (Map<String, Object> l : linhas) {
            String atividade = atividadeDe(l);
            double litros = num(l.get("qtde_litros"));
            double valor = num(l.get("valor_total"));
            mapa.computeIfAbsent(atividade, k -> new double[2]);
            mapa.get(atividade)[0] += litros;
            mapa.get(atividade)[1] += valor;
            totalLitros += litros;
        }

        double totalLitrosFinal = totalLitros;
        List<Map.Entry<String, double[]>> ordenado = new ArrayList<>(mapa.entrySet());
        ordenado.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));

        JsonArray arr = new JsonArray();
        int limite = Math.min(TOP_ATIVIDADES, ordenado.size());
        for (int i = 0; i < limite; i++) {
            Map.Entry<String, double[]> e = ordenado.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("nome", e.getKey());
            o.addProperty("volume", arred(e.getValue()[0]));
            o.addProperty("custo", arred(e.getValue()[1]));
            o.addProperty("participacao", totalLitrosFinal == 0 ? 0 : arred(e.getValue()[0] / totalLitrosFinal * 100));
            arr.add(o);
        }
        return arr;
    }

    /** Atividade Principal: desc_atividade (mais fina); sem atividade, cai no subprocesso. */
    private static String atividadeDe(Map<String, Object> l) {
        String at = strOf(l.get("desc_atividade"));
        if (!at.isBlank() && !"Não possui".equalsIgnoreCase(at)) return at;
        String sp = strOf(l.get("desc_subprocesso"));
        if (!sp.isBlank() && !"Não possui".equalsIgnoreCase(sp)) return sp;
        return "Não Classificado";
    }

    /**
     * Caminhões (canavieiros ou não-canavieiros) têm kmhs_rodados em KM
     * rodados de verdade, então faz sentido Km/L. Os demais grupos (tratores,
     * carregadeiras etc.) medem em horas de motor, por isso o consumo médio
     * é invertido (L/h) — litros por hora rodada.
     */
    private static boolean ehCaminhao(String grupo) {
        return grupo != null && grupo.toLowerCase(Locale.forLanguageTag("pt-BR")).startsWith("caminh");
    }

    // ── Classe Operativa (matriz semanal) ────────────────────────────────

    private JsonObject montarPorClasseOperativa(List<Map<String, Object>> linhas, List<LocalDate[]> semanas) {
        LocalDate ini = semanas.get(0)[0];
        int n = semanas.size();

        Map<String, double[]> porClasse = new LinkedHashMap<>(); // classe -> litros[semana]
        double[] totalPorSemana = new double[n];
        double totalGeral = 0;

        for (Map<String, Object> l : linhas) {
            String classe = classeOperativaDe(l);
            int idx = semanaDe(strOf(l.get("data")), ini, n);
            double litros = num(l.get("qtde_litros"));

            double[] serie = porClasse.computeIfAbsent(classe, k -> new double[n]);
            if (idx >= 0) {
                serie[idx] += litros;
                totalPorSemana[idx] += litros;
            }
            totalGeral += litros;
        }

        List<Map.Entry<String, double[]>> ordenado = new ArrayList<>(porClasse.entrySet());
        ordenado.sort((a, b) -> Double.compare(soma(b.getValue()), soma(a.getValue())));

        JsonArray linhasJson = new JsonArray();
        for (Map.Entry<String, double[]> e : ordenado) {
            JsonObject o = new JsonObject();
            o.addProperty("nome", e.getKey());
            JsonArray serieArr = new JsonArray();
            for (double v : e.getValue()) serieArr.add(arred(v));
            o.add("porSemana", serieArr);
            o.addProperty("total", arred(soma(e.getValue())));
            linhasJson.add(o);
        }

        JsonArray totalSemanaArr = new JsonArray();
        for (double v : totalPorSemana) totalSemanaArr.add(arred(v));

        JsonObject resultado = new JsonObject();
        resultado.add("linhas", linhasJson);
        resultado.add("totalPorSemana", totalSemanaArr);
        resultado.addProperty("totalGeral", arred(totalGeral));
        return resultado;
    }

    private static double soma(double[] v) {
        double s = 0;
        for (double x : v) s += x;
        return s;
    }

    /**
     * O de-para (fc_depara_classeoperativa) é por EQUIPAMENTO (cod_equipamento
     * de automotivo.equipamento — cada unidade física, não o modelo abstrato:
     * o mesmo modelo, ex. "JOHN DEERE 6190J", tem várias unidades com códigos
     * de equipamento diferentes, cada uma podendo cair numa classe distinta).
     * A coluna no MySQL ainda se chama cod_modelo por compatibilidade com o
     * de-para já importado, mas guarda cod_equipamento.
     *
     * Já distingue próprio de terceiro no próprio nome da classe quando é o
     * caso (ex.: "Trator" x "Trator Terceiro", "Ônibus Terceiro") — sem
     * sufixo automático somado por cima daqui. Toda linha aqui já tem
     * cod_equipamento (abastecimento sem equipamento é descartado antes,
     * em doGet).
     */
    private static String classeOperativaDe(Map<String, Object> l) {
        String codEquipamento = strOf(l.get("cod_equipamento"));
        String base = ClasseOperativaCache.buscar(codEquipamento);
        return (base == null || base.isBlank()) ? "Não Classificado" : base;
    }

    // ── Top 10 Equipamentos Próprios ─────────────────────────────────────

    /**
     * Só entram abastecimentos com equipamento de verdade vinculado
     * (cod_equipamento preenchido) — sem equipamento (alguém retirou
     * combustível direto, por placa/pessoa) não é "frota", mesmo sendo
     * Próprio; senão o nome de uma pessoa aparece como se fosse máquina.
     */
    private JsonArray montarTopProprios(List<Map<String, Object>> linhas) {
        Map<String, double[]> mapa = new LinkedHashMap<>(); // chave equipamento -> [litros, valor, kmhs_rodados]
        Map<String, String> nomes = new LinkedHashMap<>();
        Map<String, String> modelos = new LinkedHashMap<>();
        Map<String, String> grupos = new LinkedHashMap<>();
        double totalLitros = 0;

        for (Map<String, Object> l : linhas) {
            if (ehTerceiro(l)) continue;
            if (!preenchido(l.get("cod_equipamento"))) continue;
            String chave = rotuloEquipamento(l);
            double litros = num(l.get("qtde_litros"));
            double valor = num(l.get("valor_total"));
            double kmhs = num(l.get("kmhs_rodados"));
            mapa.computeIfAbsent(chave, k -> new double[3]);
            mapa.get(chave)[0] += litros;
            mapa.get(chave)[1] += valor;
            mapa.get(chave)[2] += kmhs;
            nomes.putIfAbsent(chave, chave);
            modelos.putIfAbsent(chave, strOf(l.get("descricaomodelo")));
            grupos.putIfAbsent(chave, atividadeDe(l));
            totalLitros += litros;
        }

        double totalLitrosFinal = totalLitros;
        List<Map.Entry<String, double[]>> ordenado = new ArrayList<>(mapa.entrySet());
        ordenado.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));

        JsonArray arr = new JsonArray();
        int limite = Math.min(TOP_RANKING, ordenado.size());
        for (int i = 0; i < limite; i++) {
            Map.Entry<String, double[]> e = ordenado.get(i);
            double litros = e.getValue()[0];
            double kmhs = e.getValue()[2];
            String grupo = grupos.get(e.getKey());
            boolean caminhao = ehCaminhao(grupo);
            double consumoMedio = caminhao
                    ? (litros == 0 ? 0 : kmhs / litros)
                    : (kmhs == 0 ? 0 : litros / kmhs);
            JsonObject o = new JsonObject();
            o.addProperty("posicao", i + 1);
            o.addProperty("equipamento", nomes.get(e.getKey()));
            o.addProperty("modelo", modelos.get(e.getKey()));
            o.addProperty("grupo", grupo);
            o.addProperty("volume", arred(litros));
            o.addProperty("custo", arred(e.getValue()[1]));
            o.addProperty("consumoMedio", arred(consumoMedio));
            o.addProperty("unidadeConsumo", caminhao ? "Km/L" : "L/h");
            o.addProperty("participacao", totalLitrosFinal == 0 ? 0 : arred(litros / totalLitrosFinal * 100));
            arr.add(o);
        }
        return arr;
    }

    /**
     * Volume/equipamentos reais da frota própria (só abastecimentos com
     * cod_equipamento vinculado) — usado no KPI da seção de ranking, que
     * precisa bater com o que a própria tabela mostra (kpis.propria inclui
     * também retiradas sem equipamento, por placa/pessoa).
     */
    private JsonObject montarFrotaPropriaReal(List<Map<String, Object>> linhas) {
        double litros = 0;
        Set<Object> equipamentos = new HashSet<>();
        for (Map<String, Object> l : linhas) {
            if (ehTerceiro(l)) continue;
            Object equip = l.get("cod_equipamento");
            if (!preenchido(equip)) continue;
            litros += num(l.get("qtde_litros"));
            equipamentos.add(equip);
        }
        JsonObject o = new JsonObject();
        o.addProperty("volume", arred(litros));
        o.addProperty("equipamentos", equipamentos.size());
        return o;
    }

    // ── Top 10 Terceiros (por proprietário do equipamento) ───────────────

    /** "USINA SANTA CLOTILDE S/A" às vezes aparece como proprietário histórico do equipamento — não é terceiro de verdade. */
    private static boolean ehAPropriaUsina(String proprietario) {
        return proprietario.toUpperCase(Locale.forLanguageTag("pt-BR")).contains("USINA SANTA CLOTILDE");
    }

    private JsonArray montarTopTerceiros(List<Map<String, Object>> linhas) {
        Map<String, double[]> mapa = new LinkedHashMap<>();
        Map<String, String> atividades = new LinkedHashMap<>();
        double totalLitros = 0;

        for (Map<String, Object> l : linhas) {
            if (!ehTerceiro(l)) continue;
            String proprietario = strOf(l.get("nome_proprietario"));
            if (proprietario.isBlank()) proprietario = "Proprietário Não Identificado";
            double litros = num(l.get("qtde_litros"));
            double valor = num(l.get("valor_total"));
            mapa.computeIfAbsent(proprietario, k -> new double[2]);
            mapa.get(proprietario)[0] += litros;
            mapa.get(proprietario)[1] += valor;
            // Mantém a atividade de maior volume já vista para esse proprietário
            atividades.putIfAbsent(proprietario, atividadeDe(l));
            totalLitros += litros;
        }

        double totalLitrosFinal = totalLitros;
        List<Map.Entry<String, double[]>> ordenado = new ArrayList<>(mapa.entrySet());
        ordenado.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));

        JsonArray arr = new JsonArray();
        int limite = Math.min(TOP_RANKING, ordenado.size());
        for (int i = 0; i < limite; i++) {
            Map.Entry<String, double[]> e = ordenado.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("posicao", i + 1);
            o.addProperty("proprietario", e.getKey());
            o.addProperty("atividade", atividades.get(e.getKey()));
            o.addProperty("volume", arred(e.getValue()[0]));
            o.addProperty("custo", arred(e.getValue()[1]));
            o.addProperty("participacao", totalLitrosFinal == 0 ? 0 : arred(e.getValue()[0] / totalLitrosFinal * 100));
            arr.add(o);
        }
        return arr;
    }

    // ── Identificação do equipamento (reaproveitada do dashboard anterior) ──

    private static String rotuloEquipamento(Map<String, Object> l) {
        Object v = l.get("des_equipamento");
        if (preenchido(v)) return String.valueOf(v);
        v = l.get("prefixo");
        if (preenchido(v)) return "Prefixo " + String.valueOf(v).trim();
        v = l.get("placa");
        if (preenchido(v)) return "Placa " + String.valueOf(v).trim();
        v = l.get("des_terceiro");
        if (preenchido(v)) return String.valueOf(v);
        v = l.get("des_pessoa");
        if (preenchido(v)) return String.valueOf(v);
        return "Sem identificação";
    }

    private static boolean preenchido(Object v) {
        return v != null && !String.valueOf(v).isBlank();
    }

    private static String rotulo(Map<String, Object> l, String colCod, String colDesc) {
        Object desc = l.get(colDesc);
        if (preenchido(desc)) return String.valueOf(desc);
        Object cod = l.get(colCod);
        return cod == null ? null : String.valueOf(cod);
    }

    // ── Opções de filtro (derivadas do resultado do período) ────────────

    private JsonObject montarOpcoes(List<Map<String, Object>> linhas) {
        JsonObject opcoes = new JsonObject();
        opcoes.add("combustiveis", distintos(linhas, "cod_combustivel", "des_combustivel"));
        return opcoes;
    }

    private JsonArray distintos(List<Map<String, Object>> linhas, String colCod, String colDesc) {
        Map<String, Map<String, Object>> mapa = new TreeMap<>();
        for (Map<String, Object> l : linhas) {
            Object cod = l.get(colCod);
            Object desc = l.get(colDesc);
            if (cod == null) continue;
            String descricao = desc == null || String.valueOf(desc).isBlank()
                    ? String.valueOf(cod) : String.valueOf(desc);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("cod", cod);
            item.put("descricao", descricao);
            mapa.put(descricao + "|" + cod, item);
        }
        JsonArray arr = new JsonArray();
        mapa.values().forEach(v -> arr.add(gson.toJsonTree(v)));
        return arr;
    }

    // ── Detalhe para tabela/export ──────────────────────────────────────

    private static final String[] COLUNAS_DETALHE = {
            "data", "hora_ini", "prefixo", "des_equipamento", "placa",
            "des_pessoa", "des_terceiro", "des_combustivel",
            "qtde_litros", "valor_unitario", "valor_total", "des_fazenda",
            "tipocliente", "des_frentista", "des_funcionario", "desc_negocio",
            "des_almoxarifado", "km_ho", "kmhs_rodados"
    };

    private List<Map<String, Object>> montarDetalhe(List<Map<String, Object>> linhas) {
        List<Map<String, Object>> ordenadas = new ArrayList<>(linhas);
        ordenadas.sort(Comparator.comparing(
                (Map<String, Object> l) -> String.valueOf(l.get("datahora"))).reversed());

        int limite = Math.min(ordenadas.size(), MAX_DETALHE);
        List<Map<String, Object>> detalhe = new ArrayList<>(limite);
        for (int i = 0; i < limite; i++) {
            Map<String, Object> l = ordenadas.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            for (String col : COLUNAS_DETALHE) {
                item.put(col, l.get(col));
            }
            detalhe.add(item);
        }
        return detalhe;
    }

    // ── Utilitários ─────────────────────────────────────────────────────

    private static String strOf(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static double arred(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static Integer lerInteiro(String v) {
        if (v == null || v.isBlank() || !v.trim().matches("\\d+")) return null;
        return Integer.valueOf(v.trim());
    }
}
