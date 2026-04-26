package com.atendimento.cerebro.camel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.dto.ChatResult;
import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.ChatFallbackMessages;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Timeout curto para validar fallback sem esperar o timeout padrão (15s) em CI.
 * Exige Docker acessível ao JVM (Testcontainers + Postgres).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.ai.vectorstore.pgvector.initialize-schema=true",
            "chat.circuit.timeout-ms=500"
        })
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisabledIfEnvironmentVariable(named = "CEREBRO_IT_USE_LOCAL_PG", matches = "(?i)^\\s*true\\s*$")
class ChatRestRouteTimeoutIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private ChatUseCase chatUseCase;

    @Test
    void postChat_whenUseCaseSlow_returns504AndTimeoutMessage() throws Exception {
        when(chatUseCase.chat(any()))
                .thenAnswer(invocation -> {
                    Thread.sleep(1200);
                    return new ChatResult("tarde demais");
                });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(
                        Map.of(
                                "tenantId", "t1",
                                "sessionId", "c1",
                                "message", "Olá"),
                        headers);
        ResponseEntity<ChatResult> response =
                restTemplate.exchange("/api/v1/chat", HttpMethod.POST, entity, ChatResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ChatResult::assistantMessage)
                .isEqualTo(ChatFallbackMessages.TIMEOUT);
    }
}
