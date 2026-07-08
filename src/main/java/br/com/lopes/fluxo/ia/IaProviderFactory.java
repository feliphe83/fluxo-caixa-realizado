package br.com.lopes.fluxo.ia;

/**
 * Fábrica do provedor de IA atualmente em uso.
 *
 * PARA TROCAR DE PROVEDOR (ex: Gemini → Claude → ChatGPT):
 * Basta alterar a linha dentro de getProvider() para retornar a
 * implementação desejada (ex: new ClaudeProvider() ou new OpenAiProvider()),
 * desde que a nova classe implemente a interface IaProvider.
 *
 * Nenhum outro arquivo do projeto precisa ser alterado.
 */
public class IaProviderFactory {

    private static IaProvider instance;

    public static synchronized IaProvider getProvider() {
        if (instance == null) {
            // ── Provedor ativo: Gemini ───────────────────────────────
            instance = new GeminiProvider();

            // Para trocar de provedor, comente a linha acima e
            // descomente uma das linhas abaixo (após criar a classe):
            // instance = new ClaudeProvider();
            // instance = new OpenAiProvider();
        }
        return instance;
    }
}
