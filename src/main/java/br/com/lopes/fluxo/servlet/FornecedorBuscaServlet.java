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
 * GET /api/fornecedor-busca?nome=TEXTO
 *
 * Busca fornecedores pelo nome (busca parcial, case-insensitive) para
 * o usuário descobrir o código quando não sabe de memória.
 * Retorna até 30 resultados.
 */
@WebServlet("/api/fornecedor-busca")
public class FornecedorBuscaServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(FornecedorBuscaServlet.class.getName());
    private final Gson gson = new Gson();

    private static final String SQL =
        "SELECT f.cod_fornecedor, p.nome " +
        "FROM material.fornecedor f, rh.pessoa p " +
        "WHERE f.cod_pessoa = p.cod_pessoa " +
        "AND UPPER(p.nome) LIKE UPPER(?) " +
        "ORDER BY p.nome " +
        "FETCH FIRST 30 ROWS ONLY";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        String nome = req.getParameter("nome");
        if (nome == null || nome.trim().length() < 2) {
            resp.setStatus(400);
            out.print("{\"ok\":false,\"erro\":\"Informe ao menos 2 caracteres para buscar\"}");
            out.flush(); return;
        }

        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            ps.setString(1, "%" + nome.trim() + "%");

            JsonArray arr = new JsonArray();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("codFornecedor", rs.getInt("cod_fornecedor"));
                    o.addProperty("nome", rs.getString("nome"));
                    arr.add(o);
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.add("data", arr);
            out.print(gson.toJson(result));

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro fornecedor-busca", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"","'").replace("\n"," ")
                    : e.getClass().getName();
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }
}
