package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.OracleConnectionUtil;
import com.google.gson.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.logging.*;

/**
 * GET /api/solicitacao-contratacao?dataIni=YYYY-MM-DD&dataFim=YYYY-MM-DD
 *
 * Agrega, por Departamento + Cargo:
 *   - Qtde Solicitada  (soma de solicitacontratacao.quantidade_solic)
 *   - Qtde Candidatos  (contagem de fichacandidato vinculados ao cargo, no período)
 *   - Qtde Contratada  (via rh.fn_qtde_contrat_por_solict, somada por solicitação)
 *   - Diferença = Qtde Solicitada - Qtde Candidatos - Qtde Contratada
 *
 * Filtro de visibilidade por solicitante/aprovador da query original foi
 * removido (mostra todas as solicitações do grupo empresa 1, período
 * filtrado por datasolicitacao) — conforme definido.
 */
@WebServlet("/api/solicitacao-contratacao")
public class SolicitacaoContratacaoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(SolicitacaoContratacaoServlet.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private final Gson gson = new Gson();

    private static final String SQL = """
        select max(dp) dp, max(descdp) descdp, cod_cargo, max(desccargo) desccargo,
               sum(qtde_solicitada) qtde_solicitada,
               sum(qtde_candidato)  qtde_candidato,
               sum(qtde_contratada) qtde_contratada,
               sum(qtde_solicitada) - sum(qtde_candidato) - sum(qtde_contratada) diferenca
        from (
            select solicitacontratacao.cod_departamento dp,
                   departamento.descricao descdp,
                   solicitacontratacao.cod_cargo,
                   cargo.descricao desccargo,
                   nvl(solicitacontratacao.quantidade_solic, 0) qtde_solicitada,
                   0 qtde_candidato,
                   nvl(rh.fn_qtde_contrat_por_solict(solicitacontratacao.cod_grupoempresa,
                                                      solicitacontratacao.cod_solicitacao), 0) qtde_contratada
            from rh.departamento,
                 rh.cargo,
                 rh.planocargo,
                 rh.solicitacontratacao solicitacontratacao
            where departamento.cod_departamento (+) = solicitacontratacao.cod_departamento
              and solicitacontratacao.cod_grupoempresa = 1
              and planocargo.cod_grupoempresa = cargo.cod_grupoempresa
              and planocargo.cod_planocargo = cargo.cod_planocargo
              and solicitacontratacao.cod_cargo = cargo.cod_cargo
              and solicitacontratacao.nivel = cargo.nivel
              and solicitacontratacao.cod_grupoempresa = cargo.cod_grupoempresa
              and solicitacontratacao.datasolicitacao between ? and ?

            union all

            select null dp, null descdp, ca.cod_cargo, ca.descricao desccargo,
                   0 qtde_solicitada,
                   count(f.cpf) qtde_candidato,
                   0 qtde_contratada
            from rh.fichacandidato f, rh.candidatocargo cc, rh.cargo ca
            where f.data between ? and ?
              and f.cpf = cc.cpf
              and cc.cod_cargo = ca.cod_cargo
            group by ca.cod_cargo, ca.descricao
        )
        group by cod_cargo
        order by dp, cod_cargo
        """;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        LocalDate dataIni = parseDate(req.getParameter("dataIni"));
        LocalDate dataFim = parseDate(req.getParameter("dataFim"));

        if (dataIni == null || dataFim == null) {
            resp.setStatus(400);
            out.print("{\"ok\":false,\"erro\":\"Parâmetros dataIni e dataFim são obrigatórios\"}");
            out.flush(); return;
        }

        try (Connection conn = OracleConnectionUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL)) {

            Date sqlIni = Date.valueOf(dataIni);
            Date sqlFim = Date.valueOf(dataFim);

            // 4 parâmetros: 2 pares (datasolicitacao, fichacandidato.data)
            ps.setDate(1, sqlIni); ps.setDate(2, sqlFim);
            ps.setDate(3, sqlIni); ps.setDate(4, sqlFim);

            JsonArray arr = new JsonArray();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("codDepartamento", rs.getString("dp"));
                    o.addProperty("descDepartamento", rs.getString("descdp"));
                    o.addProperty("codCargo", rs.getString("cod_cargo"));
                    o.addProperty("descCargo", rs.getString("desccargo"));
                    o.addProperty("qtdeSolicitada", rs.getBigDecimal("qtde_solicitada"));
                    o.addProperty("qtdeCandidato", rs.getBigDecimal("qtde_candidato"));
                    o.addProperty("qtdeContratada", rs.getBigDecimal("qtde_contratada"));
                    o.addProperty("diferenca", rs.getBigDecimal("diferenca"));
                    arr.add(o);
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("ok", true);
            result.add("data", arr);
            out.print(gson.toJson(result));

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro solicitacao-contratacao", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"","'").replace("\n"," ")
                    : e.getClass().getName();
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim(), FMT); }
        catch (DateTimeParseException e) { return null; }
    }
}
