package br.com.lopes.fluxo.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.mail.*;
import javax.mail.internet.MimeUtility;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Leitura (só leitura — a pasta é aberta em READ_ONLY, nada é marcado como
 * lido nem movido) da caixa de e-mail de Compras via IMAP, para achar anexos
 * em PDF e extrair o texto deles.
 *
 * Configuração (variáveis de ambiente, no setenv.sh do Tomcat — mesmo padrão
 * do {@link EvolutionApiUtil}):
 *   - IMAP_COMPRAS_USER:   caixa completa (ex.: compras@usinasantaclotilde.com.br)
 *   - IMAP_COMPRAS_SENHA:  senha da caixa
 *   - IMAP_COMPRAS_HOST:   opcional, default "email-ssl.com.br" (mesmo host do
 *                          SMTP em {@link EmailUtil} — servidor Locaweb costuma
 *                          atender IMAP/SMTP/POP3 no mesmo endereço; se não for
 *                          o caso aqui, ajuste por esta variável sem precisar
 *                          recompilar)
 *   - IMAP_COMPRAS_PASTA:  opcional, default "INBOX"
 *
 * Não testado contra uma caixa real (sem acesso de rede a partir daqui) —
 * validar depois do deploy, com as variáveis configuradas.
 */
public final class ImapComprasUtil {

    private static final Logger LOG = Logger.getLogger(ImapComprasUtil.class.getName());

    private ImapComprasUtil() {}

    private static String host()   { return valorOuPadrao("IMAP_COMPRAS_HOST", "email-ssl.com.br"); }
    private static String pasta()  { return valorOuPadrao("IMAP_COMPRAS_PASTA", "INBOX"); }
    private static String usuario() { return exigir("IMAP_COMPRAS_USER"); }
    private static String senha()   { return exigir("IMAP_COMPRAS_SENHA"); }

    private static String exigir(String var) {
        String v = System.getenv(var);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                "Variável de ambiente " + var + " não configurada. "
                + "Configure no setenv.sh do Tomcat (ver ImapComprasUtil).");
        }
        return v;
    }

    private static String valorOuPadrao(String var, String padrao) {
        String v = System.getenv(var);
        return (v == null || v.isBlank()) ? padrao : v;
    }

    /** Um PDF encontrado anexado a um e-mail da caixa de Compras. */
    public static final class AnexoPdf {
        public String messageId;
        public String remetente;
        public String assunto;
        public String nomeArquivo;
        public Date dataEmail;
        public String texto;   // texto extraído do PDF (pode vir vazio, se for PDF escaneado sem camada de texto)
    }

    /**
     * Varre a caixa de Compras à procura de e-mails recebidos nos últimos
     * {@code diasParaTras} dias e devolve um item por anexo PDF encontrado.
     *
     * A janela é sempre reaberta do zero (não guarda "última posição lida"
     * do IMAP) — quem evita reprocessar o mesmo anexo é o controle em
     * {@link br.com.lopes.fluxo.dao.NfEmailDAO}, pela chave (Message-ID +
     * nome do arquivo). Reler a mesma janela a cada ciclo custa pouco: o
     * volume de e-mail de Compras é dezenas por dia, não milhares.
     */
    public static List<AnexoPdf> buscarAnexosPdf(int diasParaTras) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host());
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "20000");

        Session session = Session.getInstance(props);
        List<AnexoPdf> encontrados = new ArrayList<>();

        try (Store store = session.getStore("imaps")) {
            store.connect(host(), usuario(), senha());
            Folder folder = store.getFolder(pasta());
            folder.open(Folder.READ_ONLY);
            try {
                Calendar corte = Calendar.getInstance();
                corte.add(Calendar.DAY_OF_MONTH, -diasParaTras);
                Message[] mensagens = folder.search(
                        new ReceivedDateTerm(ComparisonTerm.GE, corte.getTime()));

                for (Message msg : mensagens) {
                    try {
                        coletarAnexos(msg, encontrados);
                    } catch (Exception e) {
                        // Um e-mail com estrutura estranha não pode derrubar a
                        // varredura inteira dos demais.
                        LOG.log(Level.WARNING, "Falha ao ler e-mail (assunto: "
                                + tentarAssunto(msg) + ") da caixa de Compras", e);
                    }
                }
            } finally {
                folder.close(false);
            }
        }
        return encontrados;
    }

    private static String tentarAssunto(Message msg) {
        try { return msg.getSubject(); } catch (Exception e) { return "?"; }
    }

    private static void coletarAnexos(Message msg, List<AnexoPdf> saida) throws Exception {
        Object content = msg.getContent();
        if (!(content instanceof Multipart)) return;   // e-mail sem anexo (só texto/html) não interessa

        String messageId = primeiroHeader(msg, "Message-ID");
        if (messageId == null || messageId.isBlank()) {
            // Sem Message-ID (raríssimo em e-mail real) não dá pra deduplicar
            // com segurança — melhor ignorar o e-mail do que arriscar
            // reprocessar (ou perder) a mesma nota a cada ciclo.
            LOG.warning("E-mail sem Message-ID ignorado (assunto: " + tentarAssunto(msg) + ")");
            return;
        }
        String remetente = msg.getFrom() != null && msg.getFrom().length > 0 ? msg.getFrom()[0].toString() : "";
        String assunto = msg.getSubject() == null ? "" : msg.getSubject();
        Date dataEmail = msg.getReceivedDate() != null ? msg.getReceivedDate() : msg.getSentDate();

        percorrerPartes((Multipart) content, messageId, remetente, assunto, dataEmail, saida);
    }

    private static void percorrerPartes(Multipart mp, String messageId, String remetente, String assunto,
                                         Date dataEmail, List<AnexoPdf> saida) throws Exception {
        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart parte = mp.getBodyPart(i);
            Object conteudoParte = parte.getContent();
            if (conteudoParte instanceof Multipart) {
                percorrerPartes((Multipart) conteudoParte, messageId, remetente, assunto, dataEmail, saida);
                continue;
            }
            String nomeArquivo = parte.getFileName();
            boolean ehPdf = ehPdf(parte, nomeArquivo);
            if (!ehPdf) continue;

            AnexoPdf anexo = new AnexoPdf();
            anexo.messageId = messageId;
            anexo.remetente = remetente;
            anexo.assunto = assunto;
            anexo.nomeArquivo = nomeArquivo != null ? decodificarNome(nomeArquivo) : "anexo.pdf";
            anexo.dataEmail = dataEmail;
            anexo.texto = extrairTexto(parte);
            saida.add(anexo);
        }
    }

    private static boolean ehPdf(BodyPart parte, String nomeArquivo) throws MessagingException {
        if (nomeArquivo != null && nomeArquivo.toLowerCase().endsWith(".pdf")) return true;
        try {
            return parte.isMimeType("application/pdf");
        } catch (Exception e) {
            return false;
        }
    }

    private static String decodificarNome(String nome) {
        try {
            return MimeUtility.decodeText(nome);
        } catch (Exception e) {
            return nome;
        }
    }

    private static String primeiroHeader(Message msg, String nome) throws MessagingException {
        String[] valores = msg.getHeader(nome);
        return valores != null && valores.length > 0 ? valores[0] : null;
    }

    /** Texto puro do PDF (PDFBox) — string vazia se o PDF for escaneado (sem camada de texto) ou vier corrompido. */
    private static String extrairTexto(BodyPart parte) {
        try (InputStream in = parte.getInputStream();
             PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            return new PDFTextStripper().getText(doc);
        } catch (IOException | MessagingException e) {
            LOG.log(Level.WARNING, "Não foi possível ler o texto de um PDF anexado", e);
            return "";
        }
    }
}
