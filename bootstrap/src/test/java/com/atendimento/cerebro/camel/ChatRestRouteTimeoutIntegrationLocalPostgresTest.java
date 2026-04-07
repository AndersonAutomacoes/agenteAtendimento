package com.atendimento.cerebro.camel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.dto.ChatResult;
import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.ChatFallbackMessages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Mesmo que {@link ChatRestRouteTimeoutIntegrationTest}, contra Postgres local (sem Testcontainers).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.ai.vectorstore.pgvector.initialize-schema=true",
            "chat.circuit.timeout-ms=500",
            "spring.datasource.url=jdbc:postgresql://localhost:5433/cerebro",
            "spring.datasource.username=cerebro",
            "spring.datasource.password=cerebro"
        })
@ActiveProfiles("test")
class ChatRestRouteTimeoutIntegrationLocalPostgresTest {

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
        HttpEntity<String> entity =
                new HttpEntity<>("{\"tenantId\":\"t1\",\"sessionId\":\"c1\",\"message\":\"Olá\"}", headers);
        ResponseEntity<ChatResult> response =
                restTemplate.exchange("/api/v1/chat", HttpMethod.POST, entity, ChatResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ChatResult::assistantMessage)
                .isEqualTo(ChatFallbackMessages.TIMEOUT);
    }
}
