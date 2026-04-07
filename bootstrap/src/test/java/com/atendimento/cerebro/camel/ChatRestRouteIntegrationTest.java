package com.atendimento.cerebro.camel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.dto.ChatResult;
import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.ChatFallbackMessages;
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
 * Integração HTTP do Camel com Postgres via Testcontainers (sobe um container próprio; não é o do {@code docker compose}).
 * No Windows o {@code bootstrap/pom.xml} define {@code DOCKER_HOST} para o pipe do Docker Desktop.
 * Sem API do Docker, use {@code run-chat-it.cmd} (Postgres em {@code localhost:5433}) ou os testes {@code *LocalPostgresTest}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.vectorstore.pgvector.initialize-schema=true")
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisabledIfEnvironmentVariable(named = "CEREBRO_IT_USE_LOCAL_PG", matches = "(?i)^\\s*true\\s*$")
class ChatRestRouteIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private ChatUseCase chatUseCase;

    @Test
    void postChat_returnsAssistantMessage() {
        when(chatUseCase.chat(any())).thenReturn(new ChatResult("Resposta da IA"));

        ResponseEntity<ChatResult> response =
                postChat("{\"tenantId\":\"t1\",\"sessionId\":\"c1\",\"message\":\"Olá\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ChatResult::assistantMessage)
                .isEqualTo("Resposta da IA");
    }

    @Test
    void postChat_whenUseCaseThrows_returns503AndFriendlyMessage() {
        when(chatUseCase.chat(any())).thenThrow(new RuntimeException("falha simulada"));

        ResponseEntity<ChatResult> response =
                postChat("{\"tenantId\":\"t1\",\"sessionId\":\"c1\",\"message\":\"Olá\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ChatResult::assistantMessage)
                .isEqualTo(ChatFallbackMessages.MAINTENANCE);
    }

    private ResponseEntity<ChatResult> postChat(String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return restTemplate.exchange("/api/v1/chat", HttpMethod.POST, entity, ChatResult.class);
    }
}
