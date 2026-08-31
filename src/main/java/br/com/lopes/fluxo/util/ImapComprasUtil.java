package br.com.lopes.fluxo.util;

import br.com.lopes.fluxo.dao.NfEmailConfigDAO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.mail.*;
import javax.mail.internet.MimeUtility;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
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
 * Configuração: tela Administração → NF sem Entrada (guardada em
 * {@link NfEmailConfigDAO}, MySQL) é a fonte principal. Quando o usuário e a
 * senha não estiverem preenchidos ali (base recém-criada, ainda sem ninguém
 * ter passado pela tela), cai para as variáveis de ambiente
 * IMAP_COMPRAS_USER/IMAP_COMPRAS_SENHA/IMAP_COMPRAS_HOST/IMAP_COMPRAS_PASTA
 * no setenv.sh do Tomcat (mesmo padrão do {@link EvolutionApiUtil}) — assim
 * quem já tinha configurado por variável de ambiente antes da tela existir
 * continua funcionando sem precisar refazer nada.
 *
 * Não testado contra uma caixa real (sem acesso de rede a partir daqui) —
 * validar pela própria tela de administração ("Testar conexão"), depois do
 * deploy.
 */
public final class ImapComprasUtil {

    private static final Logger LOG = Logger.getLogger(ImapComprasUtil.class.getName());
    private static final NfEmailConfigDAO CONFIG_DAO = new NfEmailConfigDAO();

    private ImapComprasUtil() {}

    /** A configuração efetiva desta chamada: banco, com variável de ambiente como reserva. */
    private static final class Efetiva {
        final String host, pasta, usuario, senha;
        Efetiva(NfEmailConfigDAO.ConfigComSenha db) {
            host = valor(db == null ? null : db.host, "IMAP_COMPRAS_HOST", "email-ssl.com.br");
            pasta = valor(db == null ? null : db.pasta, "IMAP_COMPRAS_PASTA", "INBOX");
            usuario = exigirComReserva(db == null ? null : db.usuario, "IMAP_COMPRAS_USER");
            senha = exigirComReserva(db == null ? null : db.senha, "IMAP_COMPRAS_SENHA");
        }
    }

    private static Efetiva configEfetiva() {
        NfEmailConfigDAO.ConfigComSenha db;
        try {
            db = CONFIG_DAO.obterComSenha();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Não foi possível ler a configuração de NF sem entrada, usando variáveis de ambiente", e);
            db = null;
        }
        return new Efetiva(db);
    }

    private static String valor(String doBanco, String varAmbiente, String padrao) {
        if (doBanco != null && !doBanco.isBlank()) return doBanco.trim();
        String v = System.getenv(varAmbiente);
        return (v == null || v.isBlank()) ? padrao : v;
    }

    private static String exigirComReserva(String doBanco, String varAmbiente) {
        if (doBanco != null && !doBanco.isBlank()) return doBanco.trim();
        String v = System.getenv(varAmbiente);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                "Caixa de e-mail de Compras não configurada — preencha em Administração → NF sem Entrada, "
                + "ou configure " + varAmbiente + " no setenv.sh do Tomcat.");
        }
        return v;
    }

    /** Um PDF encontrado anexado a um e-mail da caixa de Compras. */
    public static final class AnexoPdf {
        public String messageId;
        public String remetente;
        public String assunto;
        public String nomeArquivo;
        public Date dataEmail;
        public String texto;   // texto extraído do PDF (pode vir vazio, se for PDF escaneado sem camada de texto)
        public byte[] bytes;   // o PDF inteiro, para guardar em disco e/ou reenviar por WhatsApp
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
        Efetiva cfg = configEfetiva();

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", cfg.host);
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "20000");
        // A caixa de Compras continua em uso normal por quem trabalha nela —
        // ler os e-mails aqui não pode marcá-los como lidos. FOLDER.READ_ONLY
        // já faz o provider IMAP da Jakarta Mail usar BODY.PEEK ao buscar o
        // conteúdo, mas "peek" é reforçado aqui explicitamente como segunda
        // trava, para não depender só do modo da pasta.
        props.put("mail.imaps.peek", "true");

        Session session = Session.getInstance(props);
        List<AnexoPdf> encontrados = new ArrayList<>();

        try (Store store = session.getStore("imaps")) {
            store.connect(cfg.host, cfg.usuario, cfg.senha);
            Folder folder = store.getFolder(cfg.pasta);
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
        if (!msg.isMimeType("multipart/*")) return;   // e-mail sem anexo (só texto/html) não interessa

        Object content = msg.getContent();
        if (!(content instanceof Multipart)) return;

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

    /**
     * Cada parte é lida no máximo UMA VEZ (um único {@code getInputStream()}).
     * Antes disso, {@code getContent()} era chamado só para descobrir se a
     * parte era um multipart aninhado — e para um anexo de PDF (sem
     * DataContentHandler registrado no JavaMail para "application/pdf"),
     * {@code getContent()} já materializa o conteúdo por baixo dos panos.
     * Chamar {@code getInputStream()} de novo em seguida buscava a MESMA
     * parte pela segunda vez no IMAP — e essa segunda busca, em pelo menos um
     * caso real, veio truncada (PDF que o PDFBox ainda conseguiu ler o
     * suficiente pra achar a chave de acesso, mas chegou corrompido no
     * disco). Descobrir "é multipart?" só pelo Content-Type evita o
     * getContent() inteiramente para uma parte que é o próprio anexo.
     */
    private static void percorrerPartes(Multipart mp, String messageId, String remetente, String assunto,
                                         Date dataEmail, List<AnexoPdf> saida) throws Exception {
        for (int i = 0; i < mp.getCount(); i++) {
            BodyPart parte = mp.getBodyPart(i);
            if (parte.isMimeType("multipart/*")) {
                Object conteudoParte = parte.getContent();
                if (conteudoParte instanceof Multipart nested) {
                    percorrerPartes(nested, messageId, remetente, assunto, dataEmail, saida);
                }
                continue;
            }
            String nomeArquivo = parte.getFileName();
            boolean ehPdf = ehPdf(parte, nomeArquivo);
            if (!ehPdf) continue;

            byte[] bytes;
            try (InputStream in = parte.getInputStream()) {
                bytes = in.readAllBytes();
            }

            if (!pareceInicioDePdf(bytes)) {
                // Os primeiros bytes de um PDF de verdade são sempre "%PDF-"
                // (é a assinatura do formato). Se não vieram assim, a captura
                // saiu errada — registra tudo que ajuda a achar o motivo
                // (tipo de codificação, tamanho) e NÃO oferece esse anexo
                // pra download: melhor não ter o PDF do que oferecer um que
                // não abre.
                LOG.warning("Anexo " + nomeArquivo + " (msg " + messageId + ") não começa com a assinatura %PDF-. "
                        + "Content-Type=" + tentarHeader(parte, "Content-Type")
                        + " Content-Transfer-Encoding=" + tentarHeader(parte, "Content-Transfer-Encoding")
                        + " tamanho=" + bytes.length + " bytes"
                        + " primeirosBytes=" + previaHex(bytes));
                bytes = null;
            }

            AnexoPdf anexo = new AnexoPdf();
            anexo.messageId = messageId;
            anexo.remetente = remetente;
            anexo.assunto = assunto;
            anexo.nomeArquivo = nomeArquivo != null ? decodificarNome(nomeArquivo) : "anexo.pdf";
            anexo.dataEmail = dataEmail;
            anexo.bytes = bytes;
            anexo.texto = bytes != null ? extrairTexto(bytes) : "";
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

    private static final byte[] ASSINATURA_PDF = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private static boolean pareceInicioDePdf(byte[] bytes) {
        if (bytes == null || bytes.length < ASSINATURA_PDF.length) return false;
        for (int i = 0; i < ASSINATURA_PDF.length; i++) {
            if (bytes[i] != ASSINATURA_PDF[i]) return false;
        }
        return true;
    }

    private static String tentarHeader(BodyPart parte, String nome) {
        try {
            String[] v = parte.getHeader(nome);
            return v != null && v.length > 0 ? v[0] : "(ausente)";
        } catch (Exception e) {
            return "(erro ao ler)";
        }
    }

    /** Os primeiros bytes em hexadecimal, pra ver no log se é lixo binário, HTML de erro, base64 cru etc. */
    private static String previaHex(byte[] bytes) {
        int n = Math.min(bytes.length, 24);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(String.format("%02x ", bytes[i]));
        return sb.toString().trim();
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
    private static String extrairTexto(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Não foi possível ler o texto de um PDF anexado", e);
            return "";
        }
    }
}
