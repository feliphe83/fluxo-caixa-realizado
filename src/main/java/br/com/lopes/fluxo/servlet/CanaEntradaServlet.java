package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.CanaEntradaDAO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET /api/cana-entrada?safra=75         -> painel de entrada de cana
 * GET /api/cana-entrada/safras           -> safras cadastradas
 *
 * O DAO devolve mês × tipo de fundo agrícola × tipo de corte. Aqui essas
 * linhas viram o que o painel mostra: total da safra, quebra por origem
 * (própria / fornecedor / acionista), quebra por colheita (manual /
 * mecanizada) e a série mês a mês com as duas quebras.
 *
 * A classificação é pela DESCRIÇÃO cadastrada no ERP, não por código.
 * Código de tipo de fundo agrícola muda de empresa para empresa e não está
 * documentado em lugar nenhum deste projeto; a descrição está à vista de
 * quem cadastra. O que não casar com nenhuma das três vai para "Outros" —
 * visível no painel, e não somado à força dentro de uma das categorias:
 * uma origem nova classificada errado é pior do que uma fatia "Outros"
 * pedindo atenção.
 */
@WebServlet({"/api/cana-entrada", "/api/cana-entrada/*"})
public class CanaEntradaServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(CanaEntradaServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final CanaEntradaDAO dao = new CanaEntradaDAO();

    /** Rótulos do painel — a ordem aqui é a ordem em que aparecem na tela. */
    private static final String PROPRIA    = "Própria";
    private static final String FORNECEDOR = "Fornecedor";
    private static final String ACIONISTA  = "Acionista";
    private static final String OUTROS     = "Outros";

    private static final String MECANIZADA = "Mecanizada";
    private static final String MANUAL     = "Manual";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        try {
            if ("/safras".equals(req.getPathInfo())) {
                escrever(resp, "{\"ok\":true,\"data\":" + GSON.toJson(dao.safras()) + "}");
                return;
            }

            String safra = req.getParameter("safra");
            if (safra == null || !safra.trim().matches("\\d+")) {
                resp.setStatus(400);
                escrever(resp, "{\"ok\":false,\"erro\":\"Parâmetro safra é obrigatório e numérico\"}");
                return;
            }

            escrever(resp, GSON.toJson(montar(safra.trim(), dao.buscar(safra.trim()))));

        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro no painel de entrada de cana", e);
            resp.setStatus(500);
            String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            escrever(resp, "{\"ok\":false,\"erro\":\"" + msg.replace("\"", "'").replace("\n", " ") + "\"}");
        }
    }

    private void escrever(HttpServletResponse resp, String corpo) throws IOException {
        resp.getWriter().print(corpo);
        resp.getWriter().flush();
    }

    // ── Montagem do painel ────────────────────────────────────────────────

    static JsonObject montar(String safra, List<Map<String, Object>> linhas) {
        // TreeMap: os meses saem em ordem cronológica sozinhos, porque a
        // chave é YYYY-MM. Ordenar por nome de mês ("abr", "ago"...) daria
        // ordem alfabética, que num gráfico de safra é ruído puro.
        Map<String, Mes> meses = new TreeMap<>();
        Map<String, BigDecimal> porOrigem   = new LinkedHashMap<>();
        Map<String, BigDecimal> porColheita = new LinkedHashMap<>();
        porOrigem.put(PROPRIA, BigDecimal.ZERO);
        porOrigem.put(FORNECEDOR, BigDecimal.ZERO);
        porOrigem.put(ACIONISTA, BigDecimal.ZERO);
        porOrigem.put(OUTROS, BigDecimal.ZERO);
        porColheita.put(MECANIZADA, BigDecimal.ZERO);
        porColheita.put(MANUAL, BigDecimal.ZERO);
        porColheita.put(OUTROS, BigDecimal.ZERO);

        BigDecimal total = BigDecimal.ZERO;

        for (Map<String, Object> l : linhas) {
            BigDecimal t = decimal(l.get("toneladas"));
            if (t.signum() == 0) continue;

            String origem   = origemDe(texto(l.get("tipo_fazenda")));
            String colheita = colheitaDe(texto(l.get("tipo_corte")));
            String mes      = texto(l.get("mes"));

            total = total.add(t);
            porOrigem.merge(origem, t, BigDecimal::add);
            porColheita.merge(colheita, t, BigDecimal::add);

            Mes m = meses.computeIfAbsent(mes, Mes::new);
            m.total = m.total.add(t);
            m.origem.merge(origem, t, BigDecimal::add);
            m.colheita.merge(colheita, t, BigDecimal::add);
        }

        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("safra", safra);
        r.addProperty("atualizadoEm", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        r.addProperty("total", total);

        r.add("porOrigem",   fatias(porOrigem, total));
        r.add("porColheita", fatias(porColheita, total));

        JsonArray serie = new JsonArray();
        for (Mes m : meses.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("mes", m.chave);
            o.addProperty("label", rotuloMes(m.chave));
            o.addProperty("total", m.total);
            o.addProperty("propria",    m.origem.getOrDefault(PROPRIA, BigDecimal.ZERO));
            o.addProperty("fornecedor", m.origem.getOrDefault(FORNECEDOR, BigDecimal.ZERO));
            o.addProperty("acionista",  m.origem.getOrDefault(ACIONISTA, BigDecimal.ZERO));
            o.addProperty("outros",     m.origem.getOrDefault(OUTROS, BigDecimal.ZERO));
            o.addProperty("mecanizada", m.colheita.getOrDefault(MECANIZADA, BigDecimal.ZERO));
            o.addProperty("manual",     m.colheita.getOrDefault(MANUAL, BigDecimal.ZERO));
            serie.add(o);
        }
        r.add("meses", serie);

        // As descrições cruas do ERP, com quanto cada uma trouxe. É por aqui
        // que se descobre que "Outros" na verdade é um tipo novo que ninguém
        // avisou — sem precisar abrir o banco.
        r.add("origensDoErp", cruas(linhas, "tipo_fazenda"));
        r.add("cortesDoErp",  cruas(linhas, "tipo_corte"));
        return r;
    }

    private static final class Mes {
        final String chave;
        BigDecimal total = BigDecimal.ZERO;
        final Map<String, BigDecimal> origem   = new LinkedHashMap<>();
        final Map<String, BigDecimal> colheita = new LinkedHashMap<>();
        Mes(String chave) { this.chave = chave; }
    }

    private static JsonArray fatias(Map<String, BigDecimal> mapa, BigDecimal total) {
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, BigDecimal> e : mapa.entrySet()) {
            // "Outros" zerado não vira fatia: só aparece quando existe mesmo.
            if (OUTROS.equals(e.getKey()) && e.getValue().signum() == 0) continue;
            JsonObject o = new JsonObject();
            o.addProperty("nome", e.getKey());
            o.addProperty("toneladas", e.getValue());
            o.addProperty("pct", total.signum() == 0 ? 0
                    : e.getValue().multiply(BigDecimal.valueOf(100))
                       .divide(total, 2, java.math.RoundingMode.HALF_UP));
            arr.add(o);
        }
        return arr;
    }

    /** Descrições como estão no ERP, somadas — para conferência. */
    private static JsonArray cruas(List<Map<String, Object>> linhas, String campo) {
        Map<String, BigDecimal> soma = new TreeMap<>();
        for (Map<String, Object> l : linhas) {
            soma.merge(texto(l.get(campo)), decimal(l.get("toneladas")), BigDecimal::add);
        }
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, BigDecimal> e : soma.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("descricao", e.getKey());
            o.addProperty("toneladas", e.getValue());
            arr.add(o);
        }
        return arr;
    }

    // ── Classificação ─────────────────────────────────────────────────────

    static String origemDe(String descricao) {
        String d = semAcento(descricao);
        if (d.contains("PROPRIA")) return PROPRIA;
        if (d.contains("ACIONISTA")) return ACIONISTA;
        if (d.contains("FORNECEDOR")) return FORNECEDOR;
        return OUTROS;
    }

    static String colheitaDe(String descricao) {
        String d = semAcento(descricao);
        // "MECANIZADA", "MECANICO", "MEC." — todos começam por MEC, e nenhum
        // outro tipo de corte começa assim.
        if (d.contains("MEC")) return MECANIZADA;
        if (d.contains("MANUAL")) return MANUAL;
        return OUTROS;
    }

    /** Maiúsculas e sem acento: o cadastro do ERP escreve "Própria" e "PROPRIA". */
    static String semAcento(String v) {
        if (v == null) return "";
        return Normalizer.normalize(v, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .trim();
    }

    /** "2026-03" -> "mar/26". */
    static String rotuloMes(String aaaaMm) {
        String[] nomes = { "jan","fev","mar","abr","mai","jun","jul","ago","set","out","nov","dez" };
        if (aaaaMm == null || !aaaaMm.matches("\\d{4}-\\d{2}")) return aaaaMm == null ? "" : aaaaMm;
        int m = Integer.parseInt(aaaaMm.substring(5, 7));
        if (m < 1 || m > 12) return aaaaMm;
        return nomes[m - 1] + "/" + aaaaMm.substring(2, 4);
    }

    private static String texto(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

}
