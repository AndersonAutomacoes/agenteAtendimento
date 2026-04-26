package com.atendimento.cerebro.camel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.dto.ChatResult;
import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.ChatFallbackMessages;
import java.util.Map;
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
 * Mesmo que {@link ChatRestRouteIntegrationTest}, usando o Postgres do {@code docker compose}
 * em {@code localhost:5433} (sem Testcontainers). Exige {@code docker compose up -d}.
 * <p>Fora do {@code mvn test} padrão (Surefire exclude); use {@code run-chat-it.cmd} ou
 * {@code mvn -pl bootstrap test -Dtest=ChatRestRouteIntegrationLocalPostgresTest}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.ai.vectorstore.pgvector.initialize-schema=true",
            "spring.datasource.url=jdbc:postgresql://localhost:5433/cerebro",
            "spring.datasource.username=cerebro",
            "spring.datasource.password=cerebro"
        })
@ActiveProfiles("test")
class ChatRestRouteIntegrationLocalPostgresTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private ChatUseCase chatUseCase;

    @Test
    void postChat_returnsAssistantMessage() {
        when(chatUseCase.chat(any())).thenReturn(new ChatResult("Resposta da IA"));

        ResponseEntity<ChatResult> response = postChat("t1", "c1", "Olá");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ChatResult::assistantMessage)
                .isEqualTo("Resposta da IA");
    }

    @Test
    void postChat_whenUseCaseThrows_returns503AndFriendlyMessage() {
        when(chatUseCase.chat(any())).thenThrow(new RuntimeException("falha simulada"));

        ResponseEntity<ChatResult> response = postChat("t1", "c1", "Olá");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ChatResult::assistantMessage)
                .isEqualTo(ChatFallbackMessages.MAINTENANCE);
    }

    private ResponseEntity<ChatResult> postChat(String tenantId, String sessionId, String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(
                        Map.of(
                                "tenantId", tenantId,
                                "sessionId", sessionId,
                                "message", message),
                        headers);
        return restTemplate.exchange("/api/v1/chat", HttpMethod.POST, entity, ChatResult.class);
    }
}
