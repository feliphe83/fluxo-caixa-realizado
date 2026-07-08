package br.com.lopes.fluxo.ia;

public interface IaProvider {
    String gerarResposta(String systemPrompt, String userPrompt) throws Exception;
}
