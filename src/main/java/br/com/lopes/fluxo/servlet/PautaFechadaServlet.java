package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import com.google.gson.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;
import java.sql.*;
import java.util.logging.*;

/**
 * GET /api/pauta-fechada
 *
 * Retorna a data limite de contas a pagar (parâmetro financeiro),
 * usada para exibir "Pauta fechada até: DD/MM/YYYY" na tela do
 * Fluxo de Caixa Realizado.
 */
@WebServlet("/api/pauta-fechada")
public class PautaFechadaServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(PautaFechadaServlet.class.getName());
    private final Gson gson = new Gson();

    private static final String SQL =
        "SELECT f.datalimitecontaspagar FROM financeiro.parametro_financeiro f " +
        "WHERE f.cod_empresa = 1 AND f.cod_filial = 1";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL);
             ResultSet rs = ps.executeQuery()) {

            JsonObject result = new JsonObject();

            if (rs.next()) {
                java.sql.Date data = rs.getDate(1);
                result.addProperty("ok", true);
                result.addProperty("dataLimite", data != null ? data.toString() : null);
            } else {
                result.addProperty("ok", true);
                result.addProperty("dataLimite", (String) null);
            }

            out.print(gson.toJson(result));

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro ao buscar pauta fechada", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"","'").replace("\n"," ")
                    : e.getClass().getName();
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }
}
