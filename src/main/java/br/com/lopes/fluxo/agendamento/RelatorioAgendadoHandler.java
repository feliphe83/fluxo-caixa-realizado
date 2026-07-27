package br.com.lopes.fluxo.agendamento;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * Um handler por "tipo_relatorio" (fc_relatorio_agendado.tipo_relatorio) —
 * sabe gerar aquele relatório específico e mandar pra cada destinatário.
 * Registrado em {@link RelatorioAgendadoScheduler#HANDLERS}.
 *
 * Lança exceção em caso de falha; quem chama (o scheduler) captura, registra
 * em fc_relatorio_agendado_execucao com status "erro" e segue pro próximo
 * agendamento — uma falha não trava os demais.
 */
public interface RelatorioAgendadoHandler {

    /**
     * @param parametros        JSON específico do tipo de relatório (ex.:
     *                          combustível usa {"dataIni":"...","combustivel":"..."})
     * @param destinatarios     lista de {id, nome, telefone, idLogonErp} (ver RelatorioAgendadoDAO.listarDestinatarios)
     * @param idUsuarioCriacao  usuário que criou o agendamento — usado pra
     *                          abrir a página com as mesmas permissões dele
     * @return resumo curto do que aconteceu, pra aparecer no histórico de
     *         execuções da tela (ex.: "3 ordens avisadas"); null cai num
     *         texto genérico
     */
    String executar(JsonObject parametros, List<Map<String, Object>> destinatarios, long idUsuarioCriacao) throws Exception;
}
