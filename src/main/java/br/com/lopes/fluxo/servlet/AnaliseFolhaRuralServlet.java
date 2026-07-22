package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AnaliseFolhaRuralDAO;
import br.com.lopes.fluxo.util.DataParamUtil;
import br.com.lopes.fluxo.util.DeParaTipoServicoCache;
import com.google.gson.Gson;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * API da Análise de Folha de Pagamento Rural (tela agrícola
 * analise-folha-pagamento-rural.html) — front-end web autenticado por sessão
 * (AuthFilter), NÃO usa o prefixo /api/agricola/ (reservado para as
 * ferramentas do Dr. Alfredo, autenticadas por chave de API).
 *
 * GET /api/analise-folha-rural?dataIni=yyyy-MM-dd&dataFim=yyyy-MM-dd
 *
 * A consulta (AnaliseFolhaRuralDAO) devolve uma linha por apontamento;
 * este servlet classifica e soma:
 *
 *  - Própria x Terceiro: pelo tipo de fundo agrícola da fazenda no momento
 *    do apontamento (cod_tipofazenda = 1 é Própria; qualquer outro valor
 *    (ou ausência de histórico) é Terceiro).
 *
 *  - Atividade Principal: Própria usa direto o subprocesso do Objeto de
 *    Custo do Oracle (obj_atual/obs — já classificado corretamente para
 *    fazenda própria); Terceiro usa o de-para de Tipo de Serviço
 *    (fc_depara_tiposervico, mesma tabela mantida na tela de administração
 *    "De-Para Serviços") porque o apontamento de terceiro nem sempre tem
 *    objeto de custo preenchido. Serviço sem de-para cadastrado cai em
 *    "Não Classificado (de-para)" — sinal para o admin completar o de-para.
 *
 *  - Feriado: apontamento cujo serviço/subprocesso contém "FERIADO" no
 *    nome, para qualquer um dos dois grupos — vira sua própria linha, fora
 *    das 8 atividades principais.
 *
 * (Complemento de Diária ainda não entra separado — complementa_diaria vem
 * 'S' na maioria dos apontamentos, não só nos que são de fato complemento;
 * até definir o critério certo, esses lançamentos ficam na Atividade
 * Principal normal, como qualquer outro.)
 *
 *  - Diárias: quantidade de trabalhadores-dia, não a soma de qtde_apontada.
 *    Se a mesma matrícula (cod_funcionario) aparecer mais de uma vez no
 *    mesmo dia dentro do mesmo bucket (mesma Atividade Principal, mesmo
 *    grupo Própria/Terceiro), conta 1 — daí a contagem por matrícula+dia em
 *    vez de somar as linhas de apontamento.
 *
 * Salário Mínimo Rural, o % sobre o Sub-Total e o cálculo de R$/Dia são
 * feitos no front-end (o salário é só digitado na tela, não é parâmetro do
 * servidor).
 */
@WebServlet("/api/analise-folha-rural")
public class AnaliseFolhaRuralServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AnaliseFolhaRuralServlet.class.getName());
    private static final DateTimeFormatter FMT_ORACLE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Ordem fixa de exibição — sempre aparecem, mesmo com valor zero. */
    private static final String[] ATIVIDADES_CANONICAS = {
            "Preparo de Solo",
            "Plantio de Cana",
            "Tratos Culturais Cana Planta",
            "Tratos Culturais Cana Soca",
            "Irrigação e Fertirrigação",
            "Departamento Técnico",
            "Administração Agrícola",
            "Serviços ADMIN. e IND"
    };

    private final Gson gson = new Gson();
    private final AnaliseFolhaRuralDAO dao = new AnaliseFolhaRuralDAO();

    /**
     * Diárias = quantidade de trabalhadores-dia, não a soma de qtde_apontada:
     * se a mesma matrícula (cod_funcionario) aparecer mais de uma vez no
     * mesmo dia dentro do mesmo grupo (mesma Atividade Principal, mesma
     * Fazenda Própria/Terceiro), conta 1 — daí o Set em vez de uma soma.
     */
    private static final class Acc {
        final Set<String> diasTrabalhados = new HashSet<>();
        double valor = 0;
        void add(String chaveDia, double v) {
            if (chaveDia != null) diasTrabalhados.add(chaveDia);
            valor += v;
        }
        double diarias() { return diasTrabalhados.size(); }
    }

    private record Totais(double diarias, double valor) {}

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

            String dataIniOracle = LocalDate.parse(dataIni).format(FMT_ORACLE);
            String dataFimOracle = LocalDate.parse(dataFim).format(FMT_ORACLE);

            List<Map<String, Object>> linhas = dao.buscar(dataIniOracle, dataFimOracle);

            Map<String, Acc> propriaAtiv = new HashMap<>();
            Map<String, Acc> terceiroAtiv = new HashMap<>();
            Acc propriaFeriado = new Acc(), terceiroFeriado = new Acc();

            // Detalhamento por Tipo de Serviço dentro de cada Atividade Principal
            // (bucket -> serviço -> Acc), usado para "explodir" a linha ao clicar.
            Map<String, Map<String, Acc>> propriaServicos = new HashMap<>();
            Map<String, Map<String, Acc>> terceiroServicos = new HashMap<>();
            Map<String, Acc> propriaFeriadoServicos = new HashMap<>();
            Map<String, Acc> terceiroFeriadoServicos = new HashMap<>();

            for (Map<String, Object> l : linhas) {
                boolean ehPropria = "1".equals(strOf(l.get("tipo_fundo_agricola")));
                double valor = num(l.get("valortotal"));
                String chaveDia = chaveDiaTrabalhado(l);
                String servico = strOf(l.get("descricaotiposervico"));
                if (servico.isBlank()) servico = "Não informado";

                String labelBruto;
                if (ehPropria) {
                    labelBruto = strOf(l.get("descricaosubprocesso"));
                    if (labelBruto.isBlank()) labelBruto = "Não Classificado";
                } else {
                    String codServico = strOf(l.get("cod_tiposervico"));
                    DeParaTipoServicoCache.Registro reg = DeParaTipoServicoCache.buscar(codServico);
                    labelBruto = (reg != null && reg.subprocesso != null && !reg.subprocesso.isBlank())
                            ? reg.subprocesso.trim() : "Não Classificado (de-para)";
                }

                boolean feriado = contemFeriado(labelBruto) || contemFeriado(strOf(l.get("descricaotiposervico")));

                if (feriado) {
                    (ehPropria ? propriaFeriado : terceiroFeriado).add(chaveDia, valor);
                    Map<String, Acc> mapaFeriadoServ = ehPropria ? propriaFeriadoServicos : terceiroFeriadoServicos;
                    mapaFeriadoServ.computeIfAbsent(servico, k -> new Acc()).add(chaveDia, valor);
                } else {
                    String bucket = normalizarAtividade(labelBruto);
                    Map<String, Acc> mapaAlvo = ehPropria ? propriaAtiv : terceiroAtiv;
                    mapaAlvo.computeIfAbsent(bucket, k -> new Acc()).add(chaveDia, valor);

                    Map<String, Map<String, Acc>> mapaServicosAlvo = ehPropria ? propriaServicos : terceiroServicos;
                    mapaServicosAlvo.computeIfAbsent(bucket, k -> new HashMap<>())
                                    .computeIfAbsent(servico, k -> new Acc())
                                    .add(chaveDia, valor);
                }
            }

            List<JsonObject> atividades = new ArrayList<>();
            Set<String> usadas = new HashSet<>();
            for (String canon : ATIVIDADES_CANONICAS) {
                List<JsonObject> servicos = listaServicos(propriaServicos.get(canon), terceiroServicos.get(canon));
                atividades.add(linhaAtividadeComServicos(canon, propriaAtiv.get(canon), terceiroAtiv.get(canon), servicos));
                usadas.add(canon);
            }

            Set<String> chavesExtras = new TreeSet<>();
            chavesExtras.addAll(propriaAtiv.keySet());
            chavesExtras.addAll(terceiroAtiv.keySet());
            chavesExtras.removeAll(usadas);

            Map<String, Acc> propriaAtivFinal = propriaAtiv;
            Map<String, Acc> terceiroAtivFinal = terceiroAtiv;
            List<String> extrasOrdenadas = chavesExtras.stream()
                    .sorted((a, b) -> Double.compare(valorCombinado(b, propriaAtivFinal, terceiroAtivFinal),
                                                      valorCombinado(a, propriaAtivFinal, terceiroAtivFinal)))
                    .collect(Collectors.toList());
            for (String extra : extrasOrdenadas) {
                List<JsonObject> servicos = listaServicos(propriaServicos.get(extra), terceiroServicos.get(extra));
                atividades.add(linhaAtividadeComServicos(extra, propriaAtiv.get(extra), terceiroAtiv.get(extra), servicos));
            }

            Totais subTotalPropria = somaTodas(propriaAtiv);
            Totais subTotalTerceiro = somaTodas(terceiroAtiv);

            Totais totalPropria = new Totais(subTotalPropria.diarias() + propriaFeriado.diarias(),
                                              subTotalPropria.valor() + propriaFeriado.valor);
            Totais totalTerceiro = new Totais(subTotalTerceiro.diarias() + terceiroFeriado.diarias(),
                                               subTotalTerceiro.valor() + terceiroFeriado.valor);

            JsonObject resultado = new JsonObject();
            resultado.addProperty("ok", true);
            resultado.addProperty("totalLinhas", linhas.size());
            resultado.add("atividades", gson.toJsonTree(atividades));
            resultado.add("subTotal", blocoTotais(subTotalPropria, subTotalTerceiro));
            JsonObject feriadoJson = blocoAcc(propriaFeriado, terceiroFeriado);
            feriadoJson.add("servicos", gson.toJsonTree(listaServicos(propriaFeriadoServicos, terceiroFeriadoServicos)));
            resultado.add("feriado", feriadoJson);
            resultado.add("totalGeral", blocoTotais(totalPropria, totalTerceiro));

            out.print(gson.toJson(resultado));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro na análise de folha de pagamento rural", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    private JsonObject linhaAtividade(String nome, Acc propria, Acc terceiro) {
        JsonObject o = new JsonObject();
        o.addProperty("nome", nome);
        o.add("propria", accJson(propria));
        o.add("terceiro", accJson(terceiro));
        return o;
    }

    /** Linha de atividade com o detalhamento por Tipo de Serviço embutido, para o front-end "explodir" ao clicar. */
    private JsonObject linhaAtividadeComServicos(String nome, Acc propria, Acc terceiro, List<JsonObject> servicos) {
        JsonObject o = linhaAtividade(nome, propria, terceiro);
        o.add("servicos", gson.toJsonTree(servicos));
        return o;
    }

    /** Lista de linhas {nome, propria, terceiro} por Tipo de Serviço, maior valor combinado primeiro. */
    private List<JsonObject> listaServicos(Map<String, Acc> servicosPropria, Map<String, Acc> servicosTerceiro) {
        Map<String, Acc> sp = servicosPropria != null ? servicosPropria : Map.of();
        Map<String, Acc> st = servicosTerceiro != null ? servicosTerceiro : Map.of();

        Set<String> chaves = new TreeSet<>();
        chaves.addAll(sp.keySet());
        chaves.addAll(st.keySet());

        List<String> ordenadas = chaves.stream()
                .sorted((a, b) -> Double.compare(valorCombinado(b, sp, st), valorCombinado(a, sp, st)))
                .collect(Collectors.toList());

        List<JsonObject> lista = new ArrayList<>();
        for (String s : ordenadas) {
            lista.add(linhaAtividade(s, sp.get(s), st.get(s)));
        }
        return lista;
    }

    private JsonObject blocoAcc(Acc propria, Acc terceiro) {
        JsonObject o = new JsonObject();
        o.add("propria", accJson(propria));
        o.add("terceiro", accJson(terceiro));
        return o;
    }

    private JsonObject blocoTotais(Totais propria, Totais terceiro) {
        JsonObject o = new JsonObject();
        o.add("propria", totaisJson(propria));
        o.add("terceiro", totaisJson(terceiro));
        return o;
    }

    private JsonObject accJson(Acc acc) {
        return numJson(acc == null ? 0 : acc.diarias(), acc == null ? 0 : acc.valor);
    }

    private JsonObject totaisJson(Totais t) {
        return numJson(t.diarias(), t.valor());
    }

    private JsonObject numJson(double diarias, double valor) {
        JsonObject o = new JsonObject();
        o.addProperty("diarias", arred(diarias));
        o.addProperty("valor", arred(valor));
        return o;
    }

    private static double valorCombinado(String chave, Map<String, Acc> propria, Map<String, Acc> terceiro) {
        double v = 0;
        Acc p = propria.get(chave);
        Acc t = terceiro.get(chave);
        if (p != null) v += p.valor;
        if (t != null) v += t.valor;
        return v;
    }

    /** Soma os buckets de um mapa (própria ou terceiro) num total simples — cada bucket já contou sua própria diária uma vez por matrícula/dia. */
    private static Totais somaTodas(Map<String, Acc> mapa) {
        double diarias = 0, valor = 0;
        for (Acc a : mapa.values()) { diarias += a.diarias(); valor += a.valor; }
        return new Totais(diarias, valor);
    }

    /** Chave matrícula+dia usada para não contar a mesma matrícula duas vezes no mesmo dia dentro do mesmo bucket. */
    private static String chaveDiaTrabalhado(Map<String, Object> l) {
        String matricula = strOf(l.get("cod_funcionario"));
        String dia = diaApontamento(l);
        if (matricula.isBlank() || dia == null) return null;
        return matricula + "|" + dia;
    }

    private static String diaApontamento(Map<String, Object> l) {
        Object d = l.get("data_apontamento");
        if (d == null) return null;
        String s = String.valueOf(d);
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    private static boolean contemFeriado(String s) {
        return s != null && s.toUpperCase(Locale.forLanguageTag("pt-BR")).contains("FERIADO");
    }

    private static String normalizarAtividade(String label) {
        String norm = normalizarChave(label);
        for (String canon : ATIVIDADES_CANONICAS) {
            if (normalizarChave(canon).equals(norm)) return canon;
        }
        return label.trim();
    }

    private static String normalizarChave(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.forLanguageTag("pt-BR")).replaceAll("\\s+", " ");
    }

    private static String strOf(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static double arred(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
