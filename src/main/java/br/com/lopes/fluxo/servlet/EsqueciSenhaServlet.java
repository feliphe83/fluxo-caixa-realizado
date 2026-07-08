package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.util.EmailUtil;
import com.google.gson.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;
import java.sql.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * POST /api/esqueci-senha
 *
 * Recebe { "logon": "..." } (aceita logon OU e-mail no mesmo campo).
 * Se encontrar um usuário ativo com e-mail cadastrado, gera um token de
 * redefinição válido por 30 minutos e envia por e-mail via SMTP Locaweb.
 *
 * Por segurança, a resposta é sempre genérica (não revela se o logon/e-mail
 * existe ou não na base) — evita enumeração de usuários.
 */
@WebServlet("/api/esqueci-senha")
public class EsqueciSenhaServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(EsqueciSenhaServlet.class.getName());
    private final Gson gson = new Gson();

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/intranet?useSSL=false&serverTimezone=America/Recife&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "lopes_app";
    private static final String DB_PASS = "Lopes@App2024";

    // URL base usada no link de redefinição enviado por e-mail
    private static final String BASE_URL = "https://intra.usinasclotilde.com.br/fluxo-caixa";

    private static final String MSG_GENERICA =
        "{\"ok\":true,\"mensagem\":\"Se o usuário existir e tiver e-mail cadastrado, " +
        "um link de redefinição foi enviado.\"}";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            String entrada = body.has("logon") ? body.get("logon").getAsString().trim() : "";

            if (entrada.isEmpty()) {
                resp.setStatus(400);
                out.print("{\"ok\":false,\"erro\":\"Informe seu usuário ou e-mail\"}");
                out.flush(); return;
            }

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

                // Aceita tanto logon quanto e-mail no mesmo campo
                Integer idUsuario = null;
                String emailDestino = null;
                String nomeUsuario = null;

                // Prioriza correspondência exata por logon. Só usa o e-mail como
                // critério de busca quando o e-mail é único na base (evita pegar o
                // usuário errado quando duas contas compartilham o mesmo e-mail).
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, nome, email FROM fc_usuario " +
                        "WHERE UPPER(logon) = UPPER(?) AND ativo = 'S'")) {
                    ps.setString(1, entrada);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            idUsuario = rs.getInt("id");
                            nomeUsuario = rs.getString("nome");
                            emailDestino = rs.getString("email");
                        }
                    }
                }

                // Se não achou por logon, tenta por e-mail — mas só se o e-mail
                // identificar exatamente UM usuário ativo.
                if (idUsuario == null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id, nome, email FROM fc_usuario " +
                            "WHERE UPPER(email) = UPPER(?) AND ativo = 'S'")) {
                        ps.setString(1, entrada);
                        try (ResultSet rs = ps.executeQuery()) {
                            Integer idTmp = null; String nomeTmp = null; String emailTmp = null;
                            int contagem = 0;
                            while (rs.next()) {
                                contagem++;
                                idTmp = rs.getInt("id");
                                nomeTmp = rs.getString("nome");
                                emailTmp = rs.getString("email");
                            }
                            // Só aceita se o e-mail for único — caso contrário, é
                            // ambíguo e a solicitação é tratada como "não encontrado"
                            // (resposta genérica, sem revelar a ambiguidade).
                            if (contagem == 1) {
                                idUsuario = idTmp;
                                nomeUsuario = nomeTmp;
                                emailDestino = emailTmp;
                            }
                        }
                    }
                }

                // Se não achou usuário, ou não tem e-mail cadastrado, responde
                // genericamente sem revelar o motivo (proteção contra enumeração).
                if (idUsuario == null || emailDestino == null || emailDestino.isBlank()) {
                    LOG.info("Tentativa de recuperação de senha sem usuário/e-mail válido: " + entrada);
                    out.print(MSG_GENERICA);
                    out.flush(); return;
                }

                // Gera token aleatório seguro
                String token = gerarToken();
                LocalDateTime expiraEm = LocalDateTime.now().plusMinutes(30);

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO fc_reset_token (id_usuario, token, expira_em) VALUES (?, ?, ?)")) {
                    ps.setInt(1, idUsuario);
                    ps.setString(2, token);
                    ps.setString(3, expiraEm.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    ps.executeUpdate();
                }

                String link = BASE_URL + "/redefinir-senha.html?token=" + token;
                String corpoHtml = montarEmailHtml(nomeUsuario, link);

                EmailUtil.enviar(emailDestino, "Redefinição de senha — Intranet USC", corpoHtml);

                out.print(MSG_GENERICA);

            }

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no fluxo de esqueci-senha", e);
            // Mesmo em erro interno, resposta genérica para não expor detalhes
            out.print(MSG_GENERICA);
        } finally {
            out.flush();
        }
    }

    private String gerarToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String montarEmailHtml(String nome, String link) {
        return "<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif;background:#f4f6fb;padding:30px;\">" +
            "<div style=\"max-width:480px;margin:0 auto;background:white;border-radius:14px;padding:32px;border:1px solid #e3e8f5;\">" +
            "<div style=\"font-size:20px;font-weight:800;color:#0f2460;margin-bottom:6px;\">Intranet USC</div>" +
            "<div style=\"font-size:14px;color:#64748b;margin-bottom:24px;\">Usina Santa Clotilde S/A</div>" +
            "<p style=\"font-size:14px;color:#1a1a2e;\">Olá, " + (nome != null ? nome : "") + "!</p>" +
            "<p style=\"font-size:14px;color:#1a1a2e;\">Recebemos uma solicitação para redefinir sua senha de acesso à Intranet. " +
            "Clique no botão abaixo para definir uma nova senha. Este link é válido por <strong>30 minutos</strong>.</p>" +
            "<div style=\"text-align:center;margin:28px 0;\">" +
            "<a href=\"" + link + "\" style=\"background:#1a3a7c;color:white;padding:13px 28px;border-radius:9px;" +
            "text-decoration:none;font-weight:700;font-size:14px;display:inline-block;\">Redefinir minha senha</a>" +
            "</div>" +
            "<p style=\"font-size:12px;color:#94a0b8;\">Se você não solicitou essa redefinição, ignore este e-mail — " +
            "sua senha atual permanece inalterada.</p>" +
            "<p style=\"font-size:11px;color:#c2c9d6;margin-top:24px;\">Se o botão não funcionar, copie e cole este link no navegador:<br>" + link + "</p>" +
            "</div></body></html>";
    }
}
