package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.dao.AcessoExternoDAO;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entrada das empresas de fora, pelo portal de manobra.
 *
 * POST /api/externo/login   logon + senha
 * GET  /api/externo/login   encerra a sessão
 *
 * A sessão criada aqui é marcada como externa. É essa marca que o AuthFilter
 * usa para manter quem entrou por aqui dentro do módulo de manobra — sem ela,
 * uma sessão válida é uma sessão válida, e a empresa de manobra chegaria no
 * fluxo de caixa.
 *
 * Não há checagem de contrato na entrada: o contrato é informado à mão no
 * boletim, e o cadastro de quem entra é conferido pela usina quando a empresa
 * é liberada. Barrar aqui por falta de contrato cadastrado deixaria alguém
 * sem trabalhar por causa de um cadastro que não é dele.
 */
@WebServlet("/api/externo/login")
public class LoginExternoServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(LoginExternoServlet.class.getName());
    private static final Gson GSON = new Gson();

    private final AcessoExternoDAO dao = new AcessoExternoDAO();

    private void json(HttpServletResponse resp, String body) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().print(body);
        resp.getWriter().flush();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String logon = req.getParameter("logon");
        String senha = req.getParameter("senha");

        if (logon == null || logon.isBlank() || senha == null || senha.isBlank()) {
            resp.setStatus(400);
            json(resp, "{\"ok\":false,\"erro\":\"Usuário e senha são obrigatórios\"}");
            return;
        }

        try {
            Map<String, Object> u = dao.autenticar(logon, senha);
            if (u == null) {
                // Mensagem única para os dois casos: dizer qual deles errou
                // conta a quem tenta se aquele usuário existe.
                resp.setStatus(401);
                json(resp, "{\"ok\":false,\"erro\":\"Usuário ou senha incorretos\"}");
                return;
            }

            int id = (Integer) u.get("id");

            HttpSession s = req.getSession(true);
            s.setAttribute("externo",       Boolean.TRUE);
            s.setAttribute("idExterno",     id);
            s.setAttribute("nome",          u.get("nome"));
            s.setAttribute("logonExterno",  u.get("logon"));
            s.setAttribute("matricula",     u.get("matricula"));
            s.setAttribute("idEmpresa",     u.get("idEmpresa"));
            s.setAttribute("cnpj",          u.get("cnpj"));
            s.setAttribute("razaoSocial",   u.get("razaoSocial"));
            s.setMaxInactiveInterval(60 * 60 * 4);
            dao.marcarAcesso(id);

            LOG.info("Acesso externo: " + u.get("logon") + " da empresa " + u.get("cnpj"));
            json(resp, "{\"ok\":true,\"nome\":" + GSON.toJson(u.get("nome"))
                    + ",\"empresa\":" + GSON.toJson(u.get("razaoSocial"))
                    + ",\"contratos\":" + GSON.toJson(dao.contratosDe(id))
                    + ",\"equipamentos\":" + GSON.toJson(dao.equipamentosDe(id)) + "}");

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erro no login externo", e);
            resp.setStatus(500);
            json(resp, "{\"ok\":false,\"erro\":\"Falha ao verificar o acesso\"}");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        if (s != null) s.invalidate();
        resp.sendRedirect(req.getContextPath() + "/manobra-login.html");
    }
}
