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
 *  - Complemento de Diária: apontamento com complementa_diaria = 'S' —
 *    idem, linha própria fora das atividades principais.
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

    private static final class Acc {
        double diarias = 0;
        double valor = 0;
        void add(double d, double v) { diarias += d; valor += v; }
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

            String dataIniOracle = LocalDate.parse(dataIni).format(FMT_ORACLE);
            String dataFimOracle = LocalDate.parse(dataFim).format(FMT_ORACLE);

            List<Map<String, Object>> linhas = dao.buscar(dataIniOracle, dataFimOracle);

            Map<String, Acc> propriaAtiv = new HashMap<>();
            Map<String, Acc> terceiroAtiv = new HashMap<>();
            Acc propriaComplemento = new Acc(), terceiroComplemento = new Acc();
            Acc propriaFeriado = new Acc(), terceiroFeriado = new Acc();

            for (Map<String, Object> l : linhas) {
                boolean ehPropria = "1".equals(strOf(l.get("tipo_fundo_agricola")));
                double diarias = num(l.get("qtde_apontada"));
                double valor = num(l.get("valortotal"));
                boolean complementoDiaria = "S".equalsIgnoreCase(strOf(l.get("complementa_diaria")));

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

                Map<String, Acc> mapaAlvo = ehPropria ? propriaAtiv : terceiroAtiv;
                if (complementoDiaria) {
                    (ehPropria ? propriaComplemento : terceiroComplemento).add(diarias, valor);
                } else if (feriado) {
                    (ehPropria ? propriaFeriado : terceiroFeriado).add(diarias, valor);
                } else {
                    String bucket = normalizarAtividade(labelBruto);
                    mapaAlvo.computeIfAbsent(bucket, k -> new Acc()).add(diarias, valor);
                }
            }

            List<JsonObject> atividades = new ArrayList<>();
            Set<String> usadas = new HashSet<>();
            for (String canon : ATIVIDADES_CANONICAS) {
                atividades.add(linhaAtividade(canon, propriaAtiv.get(canon), terceiroAtiv.get(canon)));
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
                atividades.add(linhaAtividade(extra, propriaAtiv.get(extra), terceiroAtiv.get(extra)));
            }

            Acc subTotalPropria = somaTodas(propriaAtiv);
            Acc subTotalTerceiro = somaTodas(terceiroAtiv);

            Acc totalPropria = new Acc();
            totalPropria.add(subTotalPropria.diarias + propriaComplemento.diarias + propriaFeriado.diarias,
                              subTotalPropria.valor + propriaComplemento.valor + propriaFeriado.valor);
            Acc totalTerceiro = new Acc();
            totalTerceiro.add(subTotalTerceiro.diarias + terceiroComplemento.diarias + terceiroFeriado.diarias,
                               subTotalTerceiro.valor + terceiroComplemento.valor + terceiroFeriado.valor);

            JsonObject resultado = new JsonObject();
            resultado.addProperty("ok", true);
            resultado.addProperty("totalLinhas", linhas.size());
            resultado.add("atividades", gson.toJsonTree(atividades));
            resultado.add("subTotal", blocoAcc(subTotalPropria, subTotalTerceiro));
            resultado.add("complementoDiaria", blocoAcc(propriaComplemento, terceiroComplemento));
            resultado.add("feriado", blocoAcc(propriaFeriado, terceiroFeriado));
            resultado.add("totalGeral", blocoAcc(totalPropria, totalTerceiro));

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

    private JsonObject blocoAcc(Acc propria, Acc terceiro) {
        JsonObject o = new JsonObject();
        o.add("propria", accJson(propria));
        o.add("terceiro", accJson(terceiro));
        return o;
    }

    private JsonObject accJson(Acc acc) {
        JsonObject o = new JsonObject();
        o.addProperty("diarias", arred(acc == null ? 0 : acc.diarias));
        o.addProperty("valor", arred(acc == null ? 0 : acc.valor));
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

    private static Acc somaTodas(Map<String, Acc> mapa) {
        Acc soma = new Acc();
        for (Acc a : mapa.values()) soma.add(a.diarias, a.valor);
        return soma;
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
