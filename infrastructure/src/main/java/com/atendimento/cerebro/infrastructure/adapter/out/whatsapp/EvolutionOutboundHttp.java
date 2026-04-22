package com.atendimento.cerebro.infrastructure.adapter.out.whatsapp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * POST síncrono para a Evolution API (sendText / sendButtons), fora do {@code toD} Camel,
 * para permitir várias chamadas no mesmo turno (ex.: texto de verificação + botões).
 */
@Component
public class EvolutionOutboundHttp {

    private static final Logger LOG = LoggerFactory.getLogger(EvolutionOutboundHttp.class);

    private static final Duration CONNECT = Duration.ofSeconds(15);
    private static final Duration REQUEST = Duration.ofSeconds(45);

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(CONNECT).build();

    /**
     * @return código HTTP (2xx esperado)
     */
    public int postJson(String url, String apiKey, String jsonBody) throws IOException, InterruptedException {
        HttpResponse<String> res = postJsonResponse(url, apiKey, jsonBody);
        int code = res.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException(
                    "Evolution HTTP " + code + " url=" + url + " body=" + truncate(res.body(), 500));
        }
        return code;
    }

    /**
     * POST com corpo de resposta (auditoria de {@code messageId} na Evolution API).
     */
    public HttpResponse<String> postJsonResponse(String url, String apiKey, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest req =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(REQUEST)
                        .header("Content-Type", "application/json")
                        .header("apikey", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "{}"))
                        .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        int code = res.statusCode();
        String responseBody = res.body();
        if (url.contains("/message/sendButtons/")) {
            LOG.info(
                    "Evolution sendButtons HTTP {} response={}",
                    code,
                    truncate(responseBody, 3000));
        } else {
            LOG.debug("Evolution HTTP {} url={} response={}", code, url, truncate(responseBody, 800));
        }
        return res;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
