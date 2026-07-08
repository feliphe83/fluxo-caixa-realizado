package br.com.lopes.fluxo.servlet;

import br.com.lopes.fluxo.ia.IaProvider;
import br.com.lopes.fluxo.ia.IaProviderFactory;
import com.google.gson.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;
import java.util.logging.*;

/**
 * Endpoint: POST /api/ia/analisar
 *
 * Recebe um JSON com:
 *   {
 *     "contexto": "...",   // texto descrevendo o relatório atual (totais, dados resumidos)
 *     "pergunta": "..."    // pergunta do usuário, ou vazio para análise automática
 *   }
 *
 * Retorna:
 *   { "ok": true, "resposta": "..." }
 */
@WebServlet("/api/ia/analisar")
public class IaAnaliseServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(IaAnaliseServlet.class.getName());
    private final Gson gson = new Gson();

    private static final String SYSTEM_PROMPT = """
        Você é um analista financeiro especializado em fluxo de caixa, atuando para a
        Usina Santa Clotilde S/A (USC), uma empresa do setor sucroalcooleiro.

        Você recebe dados resumidos de relatórios de fluxo de caixa (Realizado, A Realizar
        ou Comparativo entre períodos) e deve:

        - Responder de forma objetiva, em português do Brasil, sem floreios.
        - Quando solicitado um resumo, destacar: total do período, maiores categorias de
          despesa, fornecedores com maior volume, e variações relevantes entre períodos
          (se for um comparativo).
        - Apontar anomalias ou variações fora do padrão quando identificáveis (ex: aumento
          ou queda abrupta em uma categoria ou fornecedor).
        - Ao responder perguntas livres, basear-se exclusivamente nos dados fornecidos no
          contexto. Se a informação não estiver disponível nos dados, informe isso claramente
          em vez de inventar valores.
        - Usar valores em Real (R$) formatados de forma legível (ex: R$ 221.556,00).
        - Ser direto: evite introduções genéricas como "Aqui está a análise solicitada".
        """;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        PrintWriter out = resp.getWriter();

        try {
            // Ler corpo da requisição
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            String contexto = body.has("contexto") ? body.get("contexto").getAsString() : "";
            String pergunta = body.has("pergunta") ? body.get("pergunta").getAsString() : "";

            if (contexto.isBlank()) {
                resp.setStatus(400);
                out.print("{\"ok\":false,\"erro\":\"Contexto (dados do relatório) é obrigatório\"}");
                return;
            }

            String userPrompt;
            if (pergunta.isBlank()) {
                userPrompt = "Aqui estão os dados do relatório:\n\n" + contexto +
                        "\n\nFaça um resumo executivo desses dados, destacando os pontos " +
                        "mais relevantes e qualquer variação ou anomalia identificável.";
            } else {
                userPrompt = "Aqui estão os dados do relatório:\n\n" + contexto +
                        "\n\nPergunta do usuário: " + pergunta;
            }

            IaProvider provider = IaProviderFactory.getProvider();
            String resposta = provider.gerarResposta(SYSTEM_PROMPT, userPrompt);

            JsonObject resultado = new JsonObject();
            resultado.addProperty("ok", true);
            resultado.addProperty("resposta", resposta);
            out.print(gson.toJson(resultado));

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Erro no servlet de análise IA", e);
            String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'").replace("\n", " ")
                    : e.getClass().getName();
            out.print("{\"ok\":false,\"erro\":\"" + msg + "\"}");
        } finally {
            out.flush();
        }
    }
}
