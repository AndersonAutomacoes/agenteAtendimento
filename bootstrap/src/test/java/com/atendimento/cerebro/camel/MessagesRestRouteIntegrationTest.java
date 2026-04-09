package com.atendimento.cerebro.camel;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.monitoring.ChatMessageRole;
import com.atendimento.cerebro.domain.monitoring.ChatMessageStatus;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.ChatMessageItemResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.vectorstore.pgvector.initialize-schema=true")
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisabledIfEnvironmentVariable(named = "CEREBRO_IT_USE_LOCAL_PG", matches = "(?i)^\\s*true\\s*$")
class MessagesRestRouteIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Test
    void getMessages_returnsLastStoredForTenant() {
        TenantId tenant = new TenantId("tenant-monitor");
        Instant t0 = Instant.parse("2026-04-08T12:00:00Z");
        chatMessageRepository.save(
                new ChatMessage(
                        null,
                        tenant,
                        "5511999999999",
                        ChatMessageRole.USER,
                        "Olá",
                        ChatMessageStatus.SENT,
                        t0,
                        "Maria Silva",
                        null,
                        "greeting"));
        chatMessageRepository.save(
                new ChatMessage(
                        null,
                        tenant,
                        "5511999999999",
                        ChatMessageRole.ASSISTANT,
                        "Olá!",
                        ChatMessageStatus.SENT,
                        t0.plusSeconds(1),
                        null,
                        null,
                        null));

        ResponseEntity<java.util.List<ChatMessageItemResponse>> response =
                restTemplate.exchange(
                        "/api/v1/messages?tenantId=tenant-monitor",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).role()).isEqualTo("ASSISTANT");
        assertThat(response.getBody().get(0).content()).isEqualTo("Olá!");
        assertThat(response.getBody().get(1).role()).isEqualTo("USER");
        assertThat(response.getBody().get(1).content()).isEqualTo("Olá");
        assertThat(response.getBody().get(1).contactDisplayName()).isEqualTo("Maria Silva");
        assertThat(response.getBody().get(1).detectedIntent()).isEqualTo("greeting");
        assertThat(response.getBody().get(0).contactDisplayName()).isNull();
        assertThat(response.getBody().get(0).detectedIntent()).isNull();
    }

    @Test
    void getMessages_withoutTenant_returns400() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/messages", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void retryAssistantError_returns204_andEventuallySent() {
        TenantId tenant = new TenantId("tenant-retry");
        Instant t0 = Instant.parse("2026-04-08T14:00:00Z");
        long id =
                chatMessageRepository.insertReturningId(
                        new ChatMessage(
                                null,
                                tenant,
                                "5511888777666",
                                ChatMessageRole.ASSISTANT,
                                "Reenviar isto",
                                ChatMessageStatus.ERROR,
                                t0,
                                null,
                                null,
                                null));

        ResponseEntity<Void> retry =
                restTemplate.exchange(
                        "/api/v1/messages/" + id + "/retry?tenantId=tenant-retry",
                        HttpMethod.POST,
                        null,
                        Void.class);

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<java.util.List<ChatMessageItemResponse>> get =
                restTemplate.exchange(
                        "/api/v1/messages?tenantId=tenant-retry",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {});

        assertThat(get.getBody()).isNotNull();
        var row = get.getBody().stream().filter(m -> m.id() == id).findFirst();
        assertThat(row).isPresent();
        assertThat(row.get().status()).isEqualTo("SENT");
    }

    @Test
    void retryNotError_returns409() {
        TenantId tenant = new TenantId("tenant-retry-409");
        Instant t0 = Instant.parse("2026-04-08T15:00:00Z");
        long id =
                chatMessageRepository.insertReturningId(
                        new ChatMessage(
                                null,
                                tenant,
                                "5511888777666",
                                ChatMessageRole.ASSISTANT,
                                "Já ok",
                                ChatMessageStatus.SENT,
                                t0,
                                null,
                                null,
                                null));

        ResponseEntity<String> retry =
                restTemplate.exchange(
                        "/api/v1/messages/" + id + "/retry?tenantId=tenant-retry-409",
                        HttpMethod.POST,
                        null,
                        String.class);

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void retryUnknownId_returns404() {
        ResponseEntity<String> retry =
                restTemplate.exchange(
                        "/api/v1/messages/999999999/retry?tenantId=tenant-none",
                        HttpMethod.POST,
                        null,
                        String.class);

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
