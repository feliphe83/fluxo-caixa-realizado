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
import java.util.LinkedHashMap;
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

            // Três blocos, e não dois. O tipo de fundo agrícola 9 (área
            // urbana) saía junto de terceiros só porque não era 1 — e área
            // urbana é da própria usina, não de terceiro. Somados, os dois
            // números ficavam certos no total e errados nos dois lados.
            Map<String, Map<String, Acc>> ativ = new LinkedHashMap<>();
            Map<String, Acc> feriado = new LinkedHashMap<>();
            Map<String, Map<String, Map<String, Acc>>> serv = new LinkedHashMap<>();
            Map<String, Map<String, Acc>> feriadoServ = new LinkedHashMap<>();
            for (String b : BLOCOS) {
                ativ.put(b, new HashMap<>());
                feriado.put(b, new Acc());
                serv.put(b, new HashMap<>());
                feriadoServ.put(b, new HashMap<>());
            }

            for (Map<String, Object> l : linhas) {
                String bloco = blocoDe(strOf(l.get("tipo_fundo_agricola")));
                double valor = num(l.get("valortotal"));
                String chaveDia = chaveDiaTrabalhado(l);
                String servico = strOf(l.get("descricaotiposervico"));
                if (servico.isBlank()) servico = "Não informado";

                // Própria e urbana são operação da casa: têm subprocesso
                // preenchido no apontamento. Terceiro não tem, e por isso
                // depende do de-para por tipo de serviço.
                String labelBruto;
                if (TERCEIRO.equals(bloco)) {
                    String codServico = strOf(l.get("cod_tiposervico"));
                    DeParaTipoServicoCache.Registro reg = DeParaTipoServicoCache.buscar(codServico);
                    labelBruto = (reg != null && reg.subprocesso != null && !reg.subprocesso.isBlank())
                            ? reg.subprocesso.trim() : "Não Classificado (de-para)";
                } else {
                    labelBruto = strOf(l.get("descricaosubprocesso"));
                    if (labelBruto.isBlank()) labelBruto = "Não Classificado";
                }

                boolean ehFeriado = contemFeriado(labelBruto)
                                 || contemFeriado(strOf(l.get("descricaotiposervico")));

                if (ehFeriado) {
                    feriado.get(bloco).add(chaveDia, valor);
                    feriadoServ.get(bloco).computeIfAbsent(servico, k -> new Acc()).add(chaveDia, valor);
                } else {
                    String atividade = normalizarAtividade(labelBruto);
                    ativ.get(bloco).computeIfAbsent(atividade, k -> new Acc()).add(chaveDia, valor);
                    serv.get(bloco).computeIfAbsent(atividade, k -> new HashMap<>())
                                   .computeIfAbsent(servico, k -> new Acc())
                                   .add(chaveDia, valor);
                }
            }

            List<JsonObject> atividades = new ArrayList<>();
            Set<String> usadas = new HashSet<>();
            for (String canon : ATIVIDADES_CANONICAS) {
                atividades.add(linhaAtividadeComServicos(canon, ativ, serv));
                usadas.add(canon);
            }

            Set<String> chavesExtras = new TreeSet<>();
            for (String b : BLOCOS) chavesExtras.addAll(ativ.get(b).keySet());
            chavesExtras.removeAll(usadas);

            List<String> extrasOrdenadas = chavesExtras.stream()
                    .sorted((a, b) -> Double.compare(valorDe(b, ativ), valorDe(a, ativ)))
                    .collect(Collectors.toList());
            for (String extra : extrasOrdenadas) {
                atividades.add(linhaAtividadeComServicos(extra, ativ, serv));
            }

            Map<String, Totais> subTotal = new LinkedHashMap<>();
            Map<String, Totais> totalGeral = new LinkedHashMap<>();
            for (String b : BLOCOS) {
                Totais st = somaTodas(ativ.get(b));
                subTotal.put(b, st);
                totalGeral.put(b, new Totais(st.diarias() + feriado.get(b).diarias(),
                                             st.valor()   + feriado.get(b).valor));
            }

            JsonObject resultado = new JsonObject();
            resultado.addProperty("ok", true);
            resultado.addProperty("totalLinhas", linhas.size());
            resultado.add("atividades", gson.toJsonTree(atividades));
            resultado.add("subTotal", blocoTotais(subTotal));
            JsonObject feriadoJson = blocoAcc(feriado);
            feriadoJson.add("servicos", gson.toJsonTree(listaServicos(feriadoServ)));
            resultado.add("feriado", feriadoJson);
            resultado.add("totalGeral", blocoTotais(totalGeral));

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

    /** Os três blocos do quadro, na ordem em que a tela os mostra. */
    static final String PROPRIA = "propria", URBANO = "urbano", TERCEIRO = "terceiro";
    static final String[] BLOCOS = { PROPRIA, URBANO, TERCEIRO };

    /**
     * Em que bloco o apontamento entra, pelo tipo de fundo agrícola.
     *
     * 1 é fazenda própria e 9 é área urbana — as duas da usina. Antes só o 1
     * era "própria" e todo o resto caía em terceiro, o que jogava a área
     * urbana no bloco errado: o total fechava, mas os dois lados que se
     * comparam ficavam ambos errados.
     */
    static String blocoDe(String tipoFundoAgricola) {
        if ("1".equals(tipoFundoAgricola)) return PROPRIA;
        if ("9".equals(tipoFundoAgricola)) return URBANO;
        return TERCEIRO;
    }

    private JsonObject linhaAtividade(String nome, Map<String, Map<String, Acc>> ativ) {
        JsonObject o = new JsonObject();
        o.addProperty("nome", nome);
        for (String b : BLOCOS) o.add(b, accJson(ativ.get(b).get(nome)));
        return o;
    }

    /** Linha de atividade com o detalhamento por Tipo de Serviço embutido, para o front-end "explodir" ao clicar. */
    private JsonObject linhaAtividadeComServicos(String nome, Map<String, Map<String, Acc>> ativ,
                                                 Map<String, Map<String, Map<String, Acc>>> serv) {
        JsonObject o = linhaAtividade(nome, ativ);
        Map<String, Map<String, Acc>> porBloco = new LinkedHashMap<>();
        for (String b : BLOCOS) {
            Map<String, Acc> m = serv.get(b).get(nome);
            porBloco.put(b, m == null ? Map.of() : m);
        }
        o.add("servicos", gson.toJsonTree(listaServicos(porBloco)));
        return o;
    }

    /** Lista de linhas por Tipo de Serviço, maior valor combinado primeiro. */
    private List<JsonObject> listaServicos(Map<String, Map<String, Acc>> porBloco) {
        Set<String> chaves = new TreeSet<>();
        for (String b : BLOCOS) chaves.addAll(porBloco.getOrDefault(b, Map.of()).keySet());

        List<String> ordenadas = chaves.stream()
                .sorted((a, b) -> Double.compare(valorDe(b, porBloco), valorDe(a, porBloco)))
                .collect(Collectors.toList());

        List<JsonObject> lista = new ArrayList<>();
        for (String s : ordenadas) {
            JsonObject o = new JsonObject();
            o.addProperty("nome", s);
            for (String b : BLOCOS) o.add(b, accJson(porBloco.getOrDefault(b, Map.of()).get(s)));
            lista.add(o);
        }
        return lista;
    }

    /** Soma da chave nos três blocos — é por ela que a ordenação decide. */
    private static double valorDe(String chave, Map<String, Map<String, Acc>> porBloco) {
        double total = 0;
        for (String b : BLOCOS) {
            Acc a = porBloco.getOrDefault(b, Map.of()).get(chave);
            if (a != null) total += a.valor;
        }
        return total;
    }

    private JsonObject blocoAcc(Map<String, Acc> porBloco) {
        JsonObject o = new JsonObject();
        for (String b : BLOCOS) o.add(b, accJson(porBloco.get(b)));
        return o;
    }

    private JsonObject blocoTotais(Map<String, Totais> porBloco) {
        JsonObject o = new JsonObject();
        for (String b : BLOCOS) o.add(b, totaisJson(porBloco.get(b)));
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
