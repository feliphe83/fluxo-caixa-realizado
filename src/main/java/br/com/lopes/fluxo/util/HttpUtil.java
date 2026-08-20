package br.com.lopes.fluxo.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * GET simples para as fontes externas dos indicadores econômicos.
 *
 * Existe por três motivos que o navegador não resolve sozinho:
 *
 *  - REDIRECT. A API de câmbio histórico responde 301 e o corpo vem vazio
 *    para quem não segue.
 *  - USER-AGENT. O widget do CEPEA devolve 403 para cliente sem cara de
 *    navegador — conferido: com UA de Chrome ele responde 200.
 *  - TEMPO. Fonte externa fora do ar não pode segurar a página: o timeout é
 *    curto e quem chama trata a falha de cada fonte separadamente.
 */
public final class HttpUtil {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      + "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

    private static final HttpClient CLIENTE = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpUtil() {}

    /** @return o corpo da resposta; lança se não for 2xx. */
    public static String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", UA)
                .header("Accept", "*/*")
                .GET()
                .build();
        HttpResponse<String> resp = CLIENTE.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + " em " + url);
        }
        return resp.body();
    }
}
