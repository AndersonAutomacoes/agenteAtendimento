package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.config.GroqSttProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroqAudioTranscriptionAdapterTest {

    private HttpServer server;
    private String baseUrl;
    private GroqSttProperties properties;
    private final AtomicReference<String> lastGroqRequestBody = new AtomicReference<>("");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/media-ok", this::handleMediaOk);
        server.createContext("/media-404", this::handleMedia404);
        server.createContext("/groq-ok", this::handleGroqOk);
        server.createContext("/groq-429", this::handleGroqRateLimit);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        properties = new GroqSttProperties();
        properties.setApiKey("test-key");
        properties.setModel("whisper-large-v3");
        properties.setLanguage("pt");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void transcribe_whenGroqReturnsText_shouldReturnResult() {
        properties.setApiUrl(baseUrl + "/groq-ok");
        GroqAudioTranscriptionAdapter adapter = newAdapter();

        var result = adapter.transcribe(new TenantId("tenant-wa"), baseUrl + "/media-ok", "audio/ogg", "msg-1");

        assertThat(result).isPresent();
        assertThat(result.get().text()).isEqualTo("ola mundo");
        assertThat(result.get().language()).isEqualTo("pt");
        assertThat(lastGroqRequestBody.get()).contains("name=\"model\"");
        assertThat(lastGroqRequestBody.get()).contains("whisper-large-v3");
        assertThat(lastGroqRequestBody.get()).contains("name=\"language\"");
        assertThat(lastGroqRequestBody.get()).contains("\r\npt\r\n");
        assertThat(lastGroqRequestBody.get()).contains("name=\"prompt\"");
        assertThat(lastGroqRequestBody.get())
                .contains("1, 2, 3, 4, 5, 6, 7, 8, 9, 10, horário, agendamento, segunda, terça");
    }

    @Test
    void transcribe_whenGroqRateLimited_shouldReturnEmpty() {
        properties.setApiUrl(baseUrl + "/groq-429");
        GroqAudioTranscriptionAdapter adapter = newAdapter();

        var result = adapter.transcribe(new TenantId("tenant-wa"), baseUrl + "/media-ok", "audio/ogg", "msg-1");

        assertThat(result).isEmpty();
    }

    @Test
    void transcribe_whenGroqUnauthorizedInvalidKey_shouldReturnEmpty() {
        server.createContext("/groq-401", this::handleGroqUnauthorized);
        properties.setApiUrl(baseUrl + "/groq-401");
        GroqAudioTranscriptionAdapter adapter = newAdapter();

        var result = adapter.transcribe(new TenantId("tenant-wa"), baseUrl + "/media-ok", "audio/ogg", "msg-1");

        assertThat(result).isEmpty();
    }

    @Test
    void transcribe_whenGroqNoBalance402_shouldReturnEmpty() {
        server.createContext("/groq-402", this::handleGroqNoBalance);
        properties.setApiUrl(baseUrl + "/groq-402");
        GroqAudioTranscriptionAdapter adapter = newAdapter();

        var result = adapter.transcribe(new TenantId("tenant-wa"), baseUrl + "/media-ok", "audio/ogg", "msg-1");

        assertThat(result).isEmpty();
    }

    @Test
    void transcribe_whenMediaDownloadFails_shouldReturnEmpty() {
        properties.setApiUrl(baseUrl + "/groq-ok");
        GroqAudioTranscriptionAdapter adapter = newAdapter();

        var result = adapter.transcribe(new TenantId("tenant-wa"), baseUrl + "/media-404", "audio/ogg", "msg-1");

        assertThat(result).isEmpty();
    }

    private void handleMediaOk(HttpExchange exchange) throws IOException {
        byte[] payload = "audio-bytes".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "audio/ogg");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private void handleMedia404(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
    }

    private void handleGroqOk(HttpExchange exchange) throws IOException {
        byte[] request = exchange.getRequestBody().readAllBytes();
        lastGroqRequestBody.set(new String(request, StandardCharsets.UTF_8));
        byte[] payload = "{\"text\":\"ola mundo\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private void handleGroqRateLimit(HttpExchange exchange) throws IOException {
        byte[] request = exchange.getRequestBody().readAllBytes();
        lastGroqRequestBody.set(new String(request, StandardCharsets.UTF_8));
        byte[] payload = "{\"error\":\"rate_limited\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(429, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private void handleGroqUnauthorized(HttpExchange exchange) throws IOException {
        byte[] payload = "{\"error\":\"invalid_api_key\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(401, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private void handleGroqNoBalance(HttpExchange exchange) throws IOException {
        byte[] payload = "{\"error\":\"insufficient_quota\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(402, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private GroqAudioTranscriptionAdapter newAdapter() {
        TenantConfigurationStorePort store =
                new TenantConfigurationStorePort() {
                    @Override
                    public java.util.Optional<com.atendimento.cerebro.domain.tenant.TenantConfiguration> findByTenantId(
                            TenantId tenantId) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public void upsert(com.atendimento.cerebro.domain.tenant.TenantConfiguration configuration) {}
                };
        return new GroqAudioTranscriptionAdapter(properties, new ObjectMapper(), store, "");
    }
}
