package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.PrecoCanaDAO;
import br.com.lopes.fluxo.util.PdfUtil;
import br.com.lopes.fluxo.util.PrecoCanaConsecanaParser;
import br.com.lopes.fluxo.util.PrecoCanaConsecanaParser.Registro;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Importação do preço da cana pela tela de administração.
 *
 * GET  /api/admin/preco-cana/status    -> o que está gravado hoje
 * POST /api/admin/preco-cana/importar  -> lê o PDF do CONSECANA e grava o mês
 *
 * É seguro repetir: cada PDF é de um mês, e reimportar o mesmo mês só
 * regrava aquela linha (não mexe nas outras). Diferente do índice contábil,
 * aqui não há prévia separada — importar um mês não reescreve os demais,
 * então o risco de um import pela metade não existe.
 */
@WebServlet("/api/admin/preco-cana/*")
@MultipartConfig(fileSizeThreshold = 1 << 20,
                 maxFileSize = 15L * 1024 * 1024,     // um PDF do CONSECANA tem poucas centenas de KB
                 maxRequestSize = 20L * 1024 * 1024)
public class PrecoCanaImportServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(PrecoCanaImportServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final PrecoCanaDAO dao = new PrecoCanaDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        json(resp);
        String acao = trecho(req);
        JsonObject r = new JsonObject();
        try {
            if ("status".equals(acao)) {
                dao.garantirEstrutura();
                r.addProperty("ok", true);
                r.add("meses", tabela());
            } else {
                r.addProperty("ok", false);
                r.addProperty("erro", "ação desconhecida");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "preco-cana/status falhou", e);
            r.addProperty("ok", false);
            r.addProperty("erro", msg(e));
        }
        resp.getWriter().print(GSON.toJson(r));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        json(resp);
        JsonObject r = new JsonObject();
        try {
            if (!"importar".equals(trecho(req))) throw new IllegalArgumentException("ação desconhecida");
            Part parte = req.getPart("arquivo");
            if (parte == null || parte.getSize() == 0) throw new IllegalArgumentException("nenhum PDF foi enviado");

            byte[] pdf;
            try (InputStream in = parte.getInputStream()) { pdf = in.readAllBytes(); }
            String texto = PdfUtil.extrairTexto(pdf);
            Registro reg = PrecoCanaConsecanaParser.ler(texto);

            dao.garantirEstrutura();
            dao.salvar(reg);

            r.addProperty("ok", true);
            r.addProperty("mensagem", "Importado o mês " + reg.rotulo() + ".");
            JsonObject lido = new JsonObject();
            lido.addProperty("mes", reg.rotulo());
            lido.addProperty("bruto", reg.bruto());
            lido.addProperty("liquido", reg.liquido());
            lido.addProperty("acumBruto", reg.acumBruto());
            lido.addProperty("acumLiquido", reg.acumLiquido());
            r.add("lido", lido);
            r.add("meses", tabela());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Falha ao importar o preço da cana", e);
            r.addProperty("ok", false);
            r.addProperty("erro", msg(e));
        }
        resp.getWriter().print(GSON.toJson(r));
    }

    private JsonArray tabela() throws Exception {
        JsonArray arr = new JsonArray();
        for (PrecoCanaDAO.Linha l : dao.todos()) {
            JsonObject o = new JsonObject();
            o.addProperty("mes", l.rotulo());
            o.addProperty("bruto", l.bruto());
            o.addProperty("liquido", l.liquido());
            arr.add(o);
        }
        return arr;
    }

    private static String trecho(HttpServletRequest req) {
        String p = req.getPathInfo();
        return p == null ? "" : p.replaceAll("^/+", "");
    }

    private static void json(HttpServletResponse resp) {
        resp.setContentType("application/json; charset=UTF-8");
    }

    private static String msg(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
