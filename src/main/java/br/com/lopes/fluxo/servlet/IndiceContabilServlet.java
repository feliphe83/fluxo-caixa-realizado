package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.IndiceContabilDAO;
import br.com.lopes.fluxo.util.IndiceContabilParser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Importação do de/para contábil pela tela de administração.
 *
 * GET  /api/admin/indice-contabil/status   -> de onde cada painel lê hoje
 * POST /api/admin/indice-contabil/previa   -> lê a planilha e mostra o que MUDA
 * POST /api/admin/indice-contabil/aplicar  -> grava o que a prévia mostrou
 * POST /api/admin/indice-contabil/limpar   -> volta ao índice embutido
 *
 * PRÉVIA E APLICAR SÃO SEPARADOS DE PROPÓSITO. Trocar o de/para muda todo
 * número dos dois painéis, e uma conta na linha errada não deixa rastro:
 * os totais continuam fechando, só que errados. Quem aplica tem de ter visto
 * antes quantas contas entram, quantas saem e quantas mudam de linha.
 *
 * A prévia guarda o mapa já lido na sessão, e o aplicar usa aquele mapa —
 * assim o que foi aprovado é exatamente o que é gravado, sem a chance de o
 * arquivo ser trocado entre uma tela e outra.
 *
 * Recusa a planilha inteira quando não consegue resolver alguma conta. Meio
 * de/para é pior do que nenhum: as contas que faltam somem dos totais em
 * silêncio.
 */
@WebServlet("/api/admin/indice-contabil/*")
@MultipartConfig(fileSizeThreshold = 1 << 20,        // 1 MB em memória
                 maxFileSize = 20L * 1024 * 1024,    // planilha de índice tem centenas de KB
                 maxRequestSize = 25L * 1024 * 1024)
public class IndiceContabilServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(IndiceContabilServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final IndiceContabilDAO dao = new IndiceContabilDAO();

    /** Onde a prévia fica esperando o "aplicar". */
    private static final String NA_SESSAO = "indice.previa.";

    private boolean isAdmin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("administrador"));
    }

    private void json(HttpServletResponse resp, String corpo) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().print(corpo);
        resp.getWriter().flush();
    }

    private void erro(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        json(resp, "{\"ok\":false,\"erro\":\"" + msg.replace("\\", " ")
                .replace("\"", "'").replace("\n", " ") + "\"}");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Só administrador"); return; }
        if (!"/status".equals(req.getPathInfo())) { erro(resp, 404, "Rota desconhecida"); return; }

        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.add("dre", situacao(IndiceContabilDAO.PAINEL_DRE, dao.dre().size(),
                DemonstrativoFinanceiroServlet.doArquivo().size()));
        r.add("balanco", situacao(IndiceContabilDAO.PAINEL_BALANCO, dao.balanco().size(),
                BalancoPatrimonialServlet.doArquivo().size()));
        r.add("historico", GSON.toJsonTree(dao.historico(10)));
        json(resp, GSON.toJson(r));
    }

    private static JsonObject situacao(String painel, int noBanco, int noArquivo) {
        JsonObject o = new JsonObject();
        o.addProperty("painel", painel);
        o.addProperty("importado", noBanco > 0);
        o.addProperty("contas", noBanco > 0 ? noBanco : noArquivo);
        o.addProperty("origem", noBanco > 0 ? "planilha importada" : "arquivo do sistema");
        o.addProperty("contasNoArquivo", noArquivo);
        return o;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!isAdmin(req)) { erro(resp, 403, "Só administrador"); return; }

        String rota = req.getPathInfo() == null ? "" : req.getPathInfo();
        String painel = painel(req.getParameter("painel"));
        if (painel == null) { erro(resp, 400, "Painel inválido: use dre ou balanco"); return; }

        try {
            switch (rota) {
                case "/previa"  -> previa(req, resp, painel);
                case "/aplicar" -> aplicar(req, resp, painel);
                case "/limpar"  -> {
                    dao.limpar(painel);
                    req.getSession().removeAttribute(NA_SESSAO + painel);
                    json(resp, "{\"ok\":true,\"mensagem\":\"O painel voltou a usar o índice do sistema.\"}");
                }
                default -> erro(resp, 404, "Rota desconhecida");
            }
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro na importação do índice de " + painel, e);
            erro(resp, 500, e.getMessage() == null ? e.getClass().getName() : e.getMessage());
        }
    }

    private static String painel(String v) {
        if (IndiceContabilDAO.PAINEL_DRE.equalsIgnoreCase(v)) return IndiceContabilDAO.PAINEL_DRE;
        if (IndiceContabilDAO.PAINEL_BALANCO.equalsIgnoreCase(v)) return IndiceContabilDAO.PAINEL_BALANCO;
        return null;
    }

    // ── Prévia ────────────────────────────────────────────────────────────

    private void previa(HttpServletRequest req, HttpServletResponse resp, String painel)
            throws IOException, ServletException {
        Part arquivo = req.getPart("arquivo");
        if (arquivo == null || arquivo.getSize() == 0) {
            erro(resp, 400, "Nenhuma planilha foi enviada"); return;
        }
        String nome = arquivo.getSubmittedFileName() == null ? "planilha.xlsx" : arquivo.getSubmittedFileName();
        if (!nome.toLowerCase().endsWith(".xlsx")) {
            erro(resp, 400, "A planilha precisa estar em .xlsx (o .xls antigo não serve)"); return;
        }

        IndiceContabilParser.Resultado lido;
        try (InputStream in = arquivo.getInputStream()) {
            lido = IndiceContabilDAO.PAINEL_DRE.equals(painel)
                    ? IndiceContabilParser.lerDre(in)
                    : IndiceContabilParser.lerBalanco(in);
        } catch (IllegalArgumentException e) {
            erro(resp, 400, "Não consegui abrir a planilha: " + e.getMessage()); return;
        }

        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("painel", painel);
        r.addProperty("arquivo", nome);
        r.addProperty("contas", lido.mapa.size());
        r.addProperty("valida", lido.ok());
        r.add("problemas", GSON.toJsonTree(lido.problemas));
        r.add("porDestino", GSON.toJsonTree(lido.porDestino));
        r.add("mudancas", comparar(painel, lido.mapa));

        if (lido.ok()) {
            // Guarda o mapa APROVADO. O aplicar usa este, e não o arquivo de
            // novo: o que foi visto na tela é exatamente o que será gravado.
            req.getSession().setAttribute(NA_SESSAO + painel, new Object[]{ lido.mapa, nome });
        } else {
            req.getSession().removeAttribute(NA_SESSAO + painel);
        }
        json(resp, GSON.toJson(r));
    }

    /** O que entra, o que sai e o que muda de linha em relação ao índice em uso. */
    private JsonObject comparar(String painel, Map<String, String[]> novo) {
        Map<String, String[]> atual = emUso(painel);

        JsonArray entram = new JsonArray(), saem = new JsonArray(), mudam = new JsonArray();
        Set<String> todas = new LinkedHashSet<>(atual.keySet());
        todas.addAll(novo.keySet());
        for (String conta : todas) {
            String[] a = atual.get(conta), b = novo.get(conta);
            if (a == null && b != null) entram.add(linha(conta, b));
            else if (a != null && b == null) saem.add(linha(conta, a));
            else if (a != null && !java.util.Arrays.equals(a, b)) {
                JsonObject o = new JsonObject();
                o.addProperty("conta", conta);
                o.addProperty("de", String.join(" · ", a));
                o.addProperty("para", String.join(" · ", b));
                mudam.add(o);
            }
        }
        JsonObject d = new JsonObject();
        d.addProperty("emUso", atual.size());
        d.add("entram", entram);
        d.add("saem", saem);
        d.add("mudam", mudam);
        return d;
    }

    private static JsonObject linha(String conta, String[] v) {
        JsonObject o = new JsonObject();
        o.addProperty("conta", conta);
        o.addProperty("destino", String.join(" · ", v));
        return o;
    }

    /** O índice que os painéis estão usando agora: banco, ou o arquivo. */
    private Map<String, String[]> emUso(String painel) {
        if (IndiceContabilDAO.PAINEL_DRE.equals(painel)) {
            Map<String, String> banco = dao.dre();
            Map<String, String> fonte = banco.isEmpty() ? DemonstrativoFinanceiroServlet.doArquivo() : banco;
            Map<String, String[]> m = new LinkedHashMap<>();
            fonte.forEach((k, v) -> m.put(k, new String[]{ v }));
            return m;
        }
        Map<String, String[]> banco = dao.balanco();
        return banco.isEmpty() ? BalancoPatrimonialServlet.doArquivo() : banco;
    }

    // ── Aplicar ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void aplicar(HttpServletRequest req, HttpServletResponse resp, String painel)
            throws IOException {
        HttpSession s = req.getSession(false);
        Object[] guardado = s == null ? null : (Object[]) s.getAttribute(NA_SESSAO + painel);
        if (guardado == null) {
            erro(resp, 400, "Envie a planilha e confira a prévia antes de aplicar"); return;
        }
        Map<String, String[]> mapa = (Map<String, String[]>) guardado[0];
        String arquivo = String.valueOf(guardado[1]);
        String quem = s.getAttribute("logon") == null ? "?" : String.valueOf(s.getAttribute("logon"));

        int n = dao.salvar(painel, mapa, null, null, arquivo, quem);
        s.removeAttribute(NA_SESSAO + painel);

        // Os painéis guardam o índice em memória: sem isto, o número novo só
        // apareceria no próximo restart do Tomcat, e quem importou iria embora
        // achando que a importação não pegou.
        DemonstrativoFinanceiroServlet.esquecerIndice();
        BalancoPatrimonialServlet.esquecerIndice();

        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("contas", n);
        r.addProperty("mensagem", n + " contas gravadas. Os painéis já usam o índice novo.");
        json(resp, GSON.toJson(r));
    }
}
