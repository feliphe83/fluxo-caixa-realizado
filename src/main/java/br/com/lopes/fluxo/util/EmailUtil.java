package br.com.lopes.fluxo.util;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.logging.*;

/**
 * Utilitário de envio de e-mail via SMTP da Locaweb.
 *
 * Configuração: email-ssl.com.br, porta 465, SSL/TLS.
 * Credenciais abaixo — recomenda-se mover para variáveis de ambiente
 * em produção (mesmo padrão usado para a GEMINI_API_KEY no setenv.sh).
 */
public class EmailUtil {

    private static final Logger LOG = Logger.getLogger(EmailUtil.class.getName());

    private static final String SMTP_HOST = "email-ssl.com.br";
    private static final String SMTP_PORT = "465";
    private static final String EMAIL_REMETENTE = "trocadesenha@usinasclotilde.com.br";
    private static final String EMAIL_SENHA     = "Safra@2026";
    private static final String NOME_REMETENTE  = "Intranet USC";

    public static void enviar(String destinatario, String assunto, String corpoHtml) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.socketFactory.port", SMTP_PORT);
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.ssl.enable", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_REMETENTE, EMAIL_SENHA);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_REMETENTE, NOME_REMETENTE));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
            message.setSubject(assunto, "UTF-8");
            message.setContent(corpoHtml, "text/html; charset=UTF-8");

            Transport.send(message);
            LOG.info("E-mail enviado para " + destinatario);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro ao enviar e-mail para " + destinatario, e);
            throw new MessagingException("Falha ao enviar e-mail: " + e.getMessage(), e);
        }
    }
}
