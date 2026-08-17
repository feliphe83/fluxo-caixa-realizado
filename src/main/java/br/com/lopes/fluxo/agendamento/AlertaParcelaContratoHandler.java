package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.ParcelaContratoAprovacaoDAO;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * tipo_relatorio = "parcela_contrato_aprovacao" — avisa por WhatsApp as
 * parcelas de contrato que ainda aguardam aprovação.
 *
 * A consulta é fixa, como veio da área financeira: uma lista só, igual para
 * todos os destinatários marcados no agendamento. Não depende do cadastro de
 * ninguém no ERP — quem for marcado, recebe.
 *
 * Cada parcela é avisada uma única vez, por destinatário. A chave é contrato
 * + parcela — não só a parcela, senão a "parcela 1" de dois contratos
 * diferentes seria a mesma coisa para o controle e uma delas nunca sairia.
 *
 * Sem parâmetros.
 */
public class AlertaParcelaContratoHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(AlertaParcelaContratoHandler.class.getName());

    public static final String TIPO = "PARCELA CONTRATO APROVACAO";

    /** Teto de mensagens por destinatário em um ciclo, pra uma enxurrada não virar spam. */
    private static final int MAX_MENSAGENS_POR_CICLO = 15;

    private final AlertaOcPendenteDAO controle = new AlertaOcPendenteDAO();
    private final ParcelaContratoAprovacaoDAO erp = new ParcelaContratoAprovacaoDAO();

    @Override
    public String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        // Uma consulta só para todos: a lista não depende de quem recebe —
        // o que muda de um destinatário para outro é apenas o que cada um
        // já viu.
        List<Map<String, Object>> parcelas = erp.buscarSemAprovacao();
        if (parcelas.isEmpty()) return "Nenhuma parcela aguardando aprovação.";

        int totalAvisados = 0;
        List<String> falhas = new ArrayList<>();

        for (Map<String, Object> destinatario : destinatarios) {
            try {
                totalAvisados += avisar(destinatario, parcelas);
            } catch (Exception e) {
                // Falha de um destinatário não pode travar os demais.
                falhas.add(descreverFalha(destinatario, e));
                LOG.log(Level.SEVERE, "Erro no alerta de parcela para aprovação de " + destinatario.get("nome"), e);
            }
        }

        if (!falhas.isEmpty()) {
            throw new RuntimeException(String.join(" | ", falhas));
        }
        return totalAvisados == 0
                ? "Nenhuma parcela nova para aprovação."
                : totalAvisados + " parcela(s) avisada(s).";
    }

    /** Contrato + parcela: a parcela sozinha se repete entre contratos. */
    private static String chave(Map<String, Object> p) {
        return AlertaOcPendenteDAO.chave(TIPO, txt(p.get("documento")), txt(p.get("parcela")));
    }

    /** @return quantas parcelas foram avisadas a este destinatário */
    private int avisar(Map<String, Object> destinatario, List<Map<String, Object>> parcelas) throws Exception {
        int idUsuario = ((Number) destinatario.get("id")).intValue();
        String nome = String.valueOf(destinatario.get("nome"));
        String telefone = String.valueOf(destinatario.get("telefone"));

        Set<String> jaEnviados = controle.jaEnviados(idUsuario);

        int enviadas = 0;
        for (Map<String, Object> parcela : parcelas) {
            if (jaEnviados.contains(chave(parcela))) continue;

            if (enviadas >= MAX_MENSAGENS_POR_CICLO) {
                LOG.info("Alerta de parcela para aprovação de " + nome + ": limite de "
                        + MAX_MENSAGENS_POR_CICLO + " mensagens por ciclo atingido, o resto vai no próximo.");
                break;
            }

            EvolutionApiUtil.enviarTexto(telefone, montarMensagem(parcela));
            // Só marca depois do envio dar certo: se a Evolution API falhar, a
            // parcela continua "não avisada" e entra de novo no próximo ciclo.
            controle.registrarEnviado(idUsuario, TIPO, txt(parcela.get("documento")), txt(parcela.get("parcela")));
            enviadas++;
        }

        if (enviadas > 0) {
            LOG.info("Alerta de parcela para aprovação: " + enviadas + " parcela(s) avisada(s) para " + nome);
        }
        return enviadas;
    }

    private static String montarMensagem(Map<String, Object> p) {
        StringBuilder msg = new StringBuilder();
        msg.append("💳 *PARCELA AGUARDANDO APROVAÇÃO* 💳\n\n")
           .append("🔢 *Contrato:* ").append(txt(p.get("documento")))
           .append("  ·  *Parcela:* ").append(txt(p.get("parcela"))).append("\n")
           .append("📅 *Vencimento:* ").append(FormatoMensagem.data(p.get("datavcto"))).append("\n")
           .append("🏢 *Fornecedor:* ").append(txt(p.get("nome_fornecedor"))).append("\n")
           // "valor", e não "valor_liquido": é o nome da coluna que a consulta
           // devolve hoje. Ler o nome errado não dá erro nenhum — só faz o
           // valor sumir da mensagem, que é pior.
           .append("💰 *Valor:* R$ ").append(FormatoMensagem.valor(p.get("valor"))).append("\n")
           .append("🎯 *Objeto de custo:* ").append(txt(p.get("desc_objetocusto"))).append("\n")
           .append("📌 *Empenho:* ").append(txt(p.get("desc_empenho"))).append("\n")
           .append("🔁 *Tipo:* ").append(fixoVariavel(p.get("fixovariavel")));

        // A observação é campo livre e às vezes vem vazia: só entra quando há
        // o que mostrar, para a mensagem não terminar num rótulo solto.
        String obs = txt(p.get("observacao"));
        if (!obs.isBlank() && !"-".equals(obs)) {
            msg.append("\n📝 *Observação:* ").append(obs.length() > 300 ? obs.substring(0, 300) + "…" : obs);
        }
        return msg.toString();
    }

    /** A coluna guarda F ou V; o alerta de contrato mostra por extenso. */
    private static String fixoVariavel(Object v) {
        String s = txt(v);
        if ("V".equalsIgnoreCase(s)) return "Variável";
        if ("F".equalsIgnoreCase(s)) return "Fixo";
        return s;
    }

    private static String descreverFalha(Map<String, Object> destinatario, Exception e) {
        String nome = txt(destinatario.get("nome"));
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return nome + ": " + msg;
    }

    private static String txt(Object v) {
        return v == null ? "-" : String.valueOf(v).trim();
    }
}
