package br.com.lopes.fluxo.agendamento;

import br.com.lopes.fluxo.dao.AlertaOcPendenteDAO;
import br.com.lopes.fluxo.dao.OrdemCompraPendenteDAO;
import br.com.lopes.fluxo.util.EvolutionApiUtil;
import com.google.gson.JsonObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * tipo_relatorio = "oc_pendente" — avisa por WhatsApp quem tem ordem de
 * compra esperando aprovação, substituindo a aplicação que fazia isso num
 * agendador do Windows.
 *
 * É um agendamento recorrente (intervalo em minutos, não dia/hora): a cada
 * ciclo consulta as ordens pendentes de cada destinatário no ERP e manda
 * uma mensagem por ITEM novo — uma solicitação com três materiais vira três
 * mensagens, como na aplicação antiga. "Novo" é o item que ainda não foi
 * avisado àquela pessoa: enquanto a ordem seguir pendente ela continua
 * voltando na consulta, mas não é reenviada (ver
 * {@link AlertaOcPendenteDAO}).
 *
 * Cada destinatário precisa do "Código de logon no ERP" preenchido no
 * cadastro de usuário: é ele que diz quais ordens são daquele aprovador.
 * Quem não tiver é ignorado (com aviso no log), porque não há como saber o
 * que mandar. O mesmo cadastro diz se a pessoa é 1º ou 2º aprovador — são
 * consultas diferentes no ERP (ver {@link OrdemCompraPendenteDAO}).
 *
 * A exceção é o destinatário marcado como "recebe todas" no agendamento:
 * ele acompanha o grupo inteiro, recebendo tudo o que os aprovadores
 * receberam, mesmo sem ter alçada nenhuma (e portanto sem precisar de
 * código de logon).
 *
 * parametros: nenhum — o que varia (intervalo e destinatários) está nas
 * colunas do próprio agendamento.
 */
public class AlertaOcPendenteHandler implements RelatorioAgendadoHandler {

    private static final Logger LOG = Logger.getLogger(AlertaOcPendenteHandler.class.getName());

    /** Teto de mensagens por destinatário em um ciclo, pra uma enxurrada de ordens novas não virar spam. */
    private static final int MAX_MENSAGENS_POR_CICLO = 15;

    /** Valor sem o símbolo: o "R$" já está escrito no texto da mensagem. */
    private static final NumberFormat VALOR = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));
    private static final NumberFormat QTDE = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));
    static {
        VALOR.setMinimumFractionDigits(2);
        VALOR.setMaximumFractionDigits(2);
    }

    private final AlertaOcPendenteDAO controle = new AlertaOcPendenteDAO();
    private final OrdemCompraPendenteDAO erp = new OrdemCompraPendenteDAO();

    @Override
    public String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception {
        int totalAvisadas = 0;
        int semLogon = 0;
        // Uma linha por destinatário que falhou, com nome e telefone: o que
        // aparece na coluna "Última Execução" da tela precisa dizer DE QUEM é
        // o problema, senão não dá pra agir (erro típico: número que não tem
        // conta de WhatsApp, e sem o nome não se sabe qual cadastro corrigir).
        List<String> falhas = new ArrayList<>();

        // Quem está "em cópia" recebe tudo o que os aprovadores deste
        // agendamento receberam, mesmo não sendo da alçada dele — por isso os
        // itens de todos vão sendo acumulados aqui (sem repetir) e só depois
        // são enviados às cópias.
        Map<String, Map<String, Object>> itensDeTodos = new LinkedHashMap<>();
        List<Map<String, Object>> copias = new ArrayList<>();

        for (Map<String, Object> destinatario : destinatarios) {
            if (Boolean.TRUE.equals(destinatario.get("copia"))) {
                copias.add(destinatario);
                continue;
            }
            Object idLogon = destinatario.get("idLogonErp");
            if (!(idLogon instanceof Number)) {
                semLogon++;
                LOG.warning("Alerta de ordem de compra: " + destinatario.get("nome")
                        + " está sem o código de logon do ERP no cadastro — ignorado.");
                continue;
            }
            try {
                List<Map<String, Object>> itens = erp.buscarPendentes(
                        ((Number) idLogon).intValue(), etapaDe(destinatario));
                for (Map<String, Object> item : itens) itensDeTodos.putIfAbsent(chaveDoItem(item), item);
                totalAvisadas += avisar(destinatario, itens);
            } catch (Exception e) {
                // Falha de um destinatário não pode travar os demais.
                falhas.add(descreverFalha(destinatario, e));
                LOG.log(Level.SEVERE, "Erro no alerta de ordem de compra para " + destinatario.get("nome"), e);
            }
        }

        for (Map<String, Object> copia : copias) {
            try {
                totalAvisadas += avisar(copia, new ArrayList<>(itensDeTodos.values()));
            } catch (Exception e) {
                falhas.add(descreverFalha(copia, e));
                LOG.log(Level.SEVERE, "Erro no alerta de ordem de compra (cópia) para " + copia.get("nome"), e);
            }
        }

        if (!falhas.isEmpty()) {
            throw new RuntimeException(String.join(" | ", falhas));
        }
        if (semLogon > 0 && totalAvisadas == 0) {
            throw new IllegalStateException(semLogon + " destinatário(s) sem código de logon do ERP no cadastro de usuário.");
        }

        String resumo = totalAvisadas == 0
                ? "Nenhuma compra pendente nova."
                : totalAvisadas + " item(ns) avisado(s).";
        return semLogon > 0 ? resumo + " " + semLogon + " destinatário(s) sem código de logon do ERP." : resumo;
    }

    /**
     * "Fulano (82 99999-0000): motivo" — o suficiente para saber, olhando a
     * tela, qual cadastro corrigir. Números que o WhatsApp não reconhece são
     * o caso mais comum, e a resposta crua da Evolution API não diz de quem
     * é o número, só qual é.
     */
    private static String descreverFalha(Map<String, Object> destinatario, Exception e) {
        String nome = str(destinatario.get("nome"));
        String telefone = str(destinatario.get("telefone"));
        String motivo = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (motivo.contains("\"exists\":false")) {
            motivo = "número sem conta de WhatsApp (confira o telefone no cadastro)";
        }
        return nome + (telefone.isEmpty() ? "" : " (" + telefone + ")") + ": " + motivo;
    }

    /**
     * 1º ou 2º aprovador, do cadastro de usuário — decide qual das duas
     * consultas do ERP roda para essa pessoa. Sem valor no cadastro, 1º.
     */
    private static int etapaDe(Map<String, Object> destinatario) {
        Object etapa = destinatario.get("etapaAprovacao");
        return etapa instanceof Number n && n.intValue() == OrdemCompraPendenteDAO.ETAPA_SEGUNDO_APROVADOR
                ? OrdemCompraPendenteDAO.ETAPA_SEGUNDO_APROVADOR
                : OrdemCompraPendenteDAO.ETAPA_PRIMEIRO_APROVADOR;
    }

    private static String chaveDoItem(Map<String, Object> item) {
        return AlertaOcPendenteDAO.chave(str(item.get("tipo")), str(item.get("nr_solicitacao")), str(item.get("cod_material")));
    }

    /** @return quantos itens foram avisados a este destinatário */
    private int avisar(Map<String, Object> destinatario, List<Map<String, Object>> itens) throws Exception {
        int idUsuario = ((Number) destinatario.get("id")).intValue();
        String nome = String.valueOf(destinatario.get("nome"));
        String telefone = String.valueOf(destinatario.get("telefone"));

        if (itens.isEmpty()) return 0;

        Set<String> jaEnviados = controle.jaEnviados(idUsuario);

        int enviadas = 0;
        for (Map<String, Object> item : itens) {
            String tipo = str(item.get("tipo"));
            String nrSolicitacao = str(item.get("nr_solicitacao"));
            String material = str(item.get("cod_material"));

            if (jaEnviados.contains(AlertaOcPendenteDAO.chave(tipo, nrSolicitacao, material))) continue;

            if (enviadas >= MAX_MENSAGENS_POR_CICLO) {
                LOG.info("Alerta de ordem de compra para " + nome + ": limite de "
                        + MAX_MENSAGENS_POR_CICLO + " mensagens por ciclo atingido, o resto vai no próximo.");
                break;
            }

            EvolutionApiUtil.enviarTexto(telefone, montarMensagem(item));
            // Só marca depois do envio dar certo: se a Evolution API falhar, o
            // item continua "não avisado" e entra de novo no próximo ciclo.
            controle.registrarEnviado(idUsuario, tipo, nrSolicitacao, material);
            enviadas++;
        }

        if (enviadas > 0) {
            LOG.info("Alerta de ordem de compra: " + enviadas + " item(ns) avisado(s) para " + nome);
        }
        return enviadas;
    }

    /**
     * Uma mensagem por item, no mesmo formato que a aplicação do Windows já
     * enviava — a ideia é que quem recebe não perceba a troca.
     */
    private static String montarMensagem(Map<String, Object> item) {
        return " Compras Pendente \n\n"
             + "--------------------------------\n"
             + "📋 Tipo: " + str(item.get("tipo")) + "\n"
             + "📋 Número: " + str(item.get("nr_solicitacao")) + "\n"
             + "📦 Material: " + str(item.get("cod_material")) + "\n"
             + "🏷️ Unidade: " + str(item.get("cod_unidade")) + "\n"
             + "💰 Preço Unitário: R$ " + VALOR.format(num(item.get("preco_unitario"))) + "\n"
             + "🧮 Quantidade: " + QTDE.format(num(item.get("quantidade"))) + "\n"
             + "💵 Total: R$ " + VALOR.format(num(item.get("precototal"))) + "\n"
             + "👤 Fornecedor: " + str(item.get("nome")) + "\n"
             + "📝 Objeto de Custo: " + str(item.get("desc_objetocusto")) + "\n"
             + "🔍 Observação: " + str(item.get("observacao")) + "\n"
             + "--------------------------------";
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }
}
