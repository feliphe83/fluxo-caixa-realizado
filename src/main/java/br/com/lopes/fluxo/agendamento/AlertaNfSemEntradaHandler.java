package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.NfEmailDAO;
import br.com.lopes.fluxo.dao.NfEntradaOracleDAO;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
import br.com.lopes.fluxo.util.ImapComprasUtil;
import br.com.lopes.fluxo.util.NfeChaveUtil;
import com.google.gson.JsonObject;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * tipo_relatorio = "nf_sem_entrada" — lê a caixa de e-mail de Compras,
 * decodifica a chave de acesso das notas fiscais anexadas em PDF, confere no
 * ERP se já deram entrada e avisa por WhatsApp as que passaram do prazo.
 *
 * A cada ciclo, em três passos:
 *   1. {@link #escanearEmail()} — lê os PDFs recentes da caixa de Compras
 *      ({@link ImapComprasUtil}) e registra (em MySQL, {@link NfEmailDAO}) os
 *      que ainda não tinham sido vistos;
 *   2. {@link #conferirEntradasNoErp()} — para os pendentes com número/série
 *      conhecidos, pergunta ao Oracle ({@link NfEntradaOracleDAO}) se já
 *      existe entrada; se sim, marca como resolvido e para de alertar;
 *   3. o que continuar pendente e já tiver passado do prazo (contado da DATA
 *      DO E-MAIL, não da detecção) vira aviso — um por destinatário do
 *      agendamento, sem repetir (mesmo controle de
 *      {@link AlertaOcPendenteDAO} dos demais alertas, TIPO = {@link #TIPO}).
 *
 * Uma nota "SEM_CHAVE" (PDF que parece nota mas não deu pra ler a chave —
 * comum em PDF escaneado) fica visível na tela, mas nunca gera alerta
 * automático: sem número/série decodificados não tem contra o que checar no
 * Oracle, e avisar sem saber qual é a nota não ajudaria ninguém.
 *
 * parametros: nenhum.
 */
public class AlertaNfSemEntradaHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(AlertaNfSemEntradaHandler.class.getName());

    /** Distingue este alerta dos demais na tabela de controle de envio. */
    public static final String TIPO = "NF SEM ENTRADA";

    /** O prazo pedido: nota sem entrada por mais que isso vira alerta. */
    private static final int PRAZO_DIAS = 5;
    /** Janela de varredura do e-mail — maior que o prazo, pra um agendamento parado alguns dias não perder nota. */
    private static final int DIAS_VARREDURA_EMAIL = 20;
    /** Teto de mensagens por destinatário em um ciclo, pra uma enxurrada não virar spam. */
    private static final int MAX_MENSAGENS_POR_CICLO = 15;

    private final NfEmailDAO controle = new NfEmailDAO();
    private final NfEntradaOracleDAO erp = new NfEntradaOracleDAO();
    private final AlertaOcPendenteDAO enviados = new AlertaOcPendenteDAO();

    @Override
    public String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        int novas = escanearEmail();
        int confirmadas = conferirEntradasNoErp();
        List<Map<String, Object>> atrasadas = controle.listarPendentesAtrasados(PRAZO_DIAS);

        int totalAvisadas = 0;
        List<String> falhas = new ArrayList<>();
        if (!atrasadas.isEmpty()) {
            for (Map<String, Object> destinatario : destinatarios) {
                try {
                    totalAvisadas += avisar(destinatario, atrasadas);
                } catch (Exception e) {
                    falhas.add(descreverFalha(destinatario, e));
                    LOG.log(Level.SEVERE, "Erro no alerta de NF sem entrada para " + destinatario.get("nome"), e);
                }
            }
        }
        if (!falhas.isEmpty()) {
            throw new RuntimeException(String.join(" | ", falhas));
        }
        return novas + " nota(s) nova(s) detectada(s) no e-mail, " + confirmadas + " confirmada(s) com entrada, "
             + totalAvisadas + " aviso(s) de atraso enviado(s).";
    }

    /** Lê a caixa de Compras e registra os anexos de PDF ainda não vistos. @return quantos anexos novos foram gravados. */
    private int escanearEmail() {
        List<ImapComprasUtil.AnexoPdf> anexos;
        try {
            anexos = ImapComprasUtil.buscarAnexosPdf(DIAS_VARREDURA_EMAIL);
        } catch (Exception e) {
            // Sem acesso à caixa de e-mail neste ciclo não é motivo pra travar
            // a checagem de entrada do que já tinha sido detectado antes.
            LOG.log(Level.SEVERE, "Falha ao ler a caixa de e-mail de Compras", e);
            return 0;
        }
        int novas = 0;
        for (ImapComprasUtil.AnexoPdf anexo : anexos) {
            try {
                NfeChaveUtil.Chave chave = NfeChaveUtil.extrair(anexo.texto);
                NfEmailDAO.Registro r = new NfEmailDAO.Registro();
                r.messageId = anexo.messageId;
                r.nomeAnexo = anexo.nomeArquivo;
                r.dataEmail = anexo.dataEmail;
                r.remetente = anexo.remetente;
                r.assunto = anexo.assunto;
                if (chave != null) {
                    r.chaveAcesso = chave.chave44;
                    r.nrnf = chave.numero;
                    r.serie = chave.serie;
                    r.cnpjEmitente = chave.cnpjEmitente;
                    r.status = "PENDENTE";
                } else if (NfeChaveUtil.pareceDanfe(anexo.texto, anexo.nomeArquivo)) {
                    r.status = "SEM_CHAVE";
                } else {
                    continue;   // PDF anexado que não parece nota fiscal (boleto, catálogo etc.) — nem registra
                }
                if (controle.inserirSeNovo(r)) novas++;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Falha ao registrar anexo " + anexo.nomeArquivo + " do e-mail de Compras", e);
            }
        }
        return novas;
    }

    /** Confere no Oracle os pendentes com número/série conhecidos. @return quantos passaram a ENTRADA_CONFIRMADA. */
    private int conferirEntradasNoErp() throws SQLException {
        List<Map<String, Object>> pendentes = controle.listarPendentesParaChecar();
        int confirmadas = 0;
        for (Map<String, Object> p : pendentes) {
            try {
                if (erp.existeEntrada(String.valueOf(p.get("nrnf")), String.valueOf(p.get("serie")))) {
                    controle.marcarConfirmada(((Number) p.get("id")).intValue());
                    confirmadas++;
                }
            } catch (Exception e) {
                // Uma nota com problema não pode travar a checagem das demais.
                LOG.log(Level.WARNING, "Falha ao checar entrada da NF id=" + p.get("id"), e);
            }
        }
        return confirmadas;
    }

    private int avisar(Map<String, Object> destinatario, List<Map<String, Object>> atrasadas) throws Exception {
        int idUsuario = ((Number) destinatario.get("id")).intValue();
        String nome = String.valueOf(destinatario.get("nome"));
        String telefone = String.valueOf(destinatario.get("telefone"));

        Set<String> jaEnviados = enviados.jaEnviados(idUsuario);
        int enviadas = 0;
        for (Map<String, Object> nf : atrasadas) {
            String idNf = String.valueOf(nf.get("id"));
            if (jaEnviados.contains(AlertaOcPendenteDAO.chave(TIPO, idNf, ""))) continue;
            if (enviadas >= MAX_MENSAGENS_POR_CICLO) {
                LOG.info("Alerta de NF sem entrada para " + nome + ": limite de "
                        + MAX_MENSAGENS_POR_CICLO + " mensagens por ciclo atingido, o resto vai no próximo.");
                break;
            }
            EvolutionApiUtil.enviarTexto(telefone, montarMensagem(nf));
            // Só marca depois do envio dar certo: se a Evolution API falhar, a
            // nota continua "não avisada" e entra de novo no próximo ciclo.
            enviados.registrarEnviado(idUsuario, TIPO, idNf, "");
            enviadas++;
        }
        if (enviadas > 0) {
            LOG.info("Alerta de NF sem entrada: " + enviadas + " nota(s) avisada(s) para " + nome);
        }
        return enviadas;
    }

    private static String montarMensagem(Map<String, Object> nf) {
        long dias = diasDesde(nf.get("data_email"));
        StringBuilder msg = new StringBuilder();
        msg.append("📨 *Alerta de NF sem entrada*\n\n")
           .append("🧾 *NF:* ").append(txt(nf.get("nrnf"))).append(" - série ").append(txt(nf.get("serie"))).append("\n")
           .append("🏭 *CNPJ emitente:* ").append(formatarCnpj(txt(nf.get("cnpj_emitente")))).append("\n")
           .append("📧 *Recebida por e-mail em:* ").append(FormatoMensagem.data(nf.get("data_email")))
           .append(dias >= 0 ? " (" + dias + " dia(s) atrás)" : "").append("\n")
           .append("✉️ *Remetente:* ").append(txt(nf.get("remetente"))).append("\n")
           .append("📎 *Anexo:* ").append(txt(nf.get("nome_anexo"))).append("\n\n")
           .append("⚠️ Sem entrada registrada no ERP até agora.");
        return msg.toString();
    }

    private static long diasDesde(Object dataEmailIso) {
        try {
            LocalDateTime dt = LocalDateTime.parse(String.valueOf(dataEmailIso));
            return ChronoUnit.DAYS.between(dt.toLocalDate(), LocalDate.now());
        } catch (Exception e) {
            return -1;
        }
    }

    private static String formatarCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) return cnpj == null ? "" : cnpj;
        return cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8)
             + "/" + cnpj.substring(8, 12) + "-" + cnpj.substring(12, 14);
    }

    private static String descreverFalha(Map<String, Object> destinatario, Exception e) {
        String nome = txt(destinatario.get("nome"));
        String telefone = txt(destinatario.get("telefone"));
        String motivo = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (motivo.contains("\"exists\":false")) {
            motivo = "número sem conta de WhatsApp (confira o telefone no cadastro)";
        }
        return nome + (telefone.isEmpty() ? "" : " (" + telefone + ")") + ": " + motivo;
    }

    private static String txt(Object v) {
        return FormatoMensagem.texto(v);
    }
}
