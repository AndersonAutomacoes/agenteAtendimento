package com.atendimento.cerebro.camel;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.application.port.out.ConversationBotStatePort;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.monitoring.ChatMessageRole;
import com.atendimento.cerebro.domain.monitoring.ChatMessageStatus;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.ChatMessageItemResponse;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.ChatMessagesListResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
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

    @Autowired
    private ConversationBotStatePort conversationBotStatePort;

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

        ResponseEntity<ChatMessagesListResponse> response =
                restTemplate.exchange(
                        "/api/v1/messages?tenantId=tenant-monitor",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().messages()).hasSize(2);
        assertThat(response.getBody().messages().get(0).role()).isEqualTo("ASSISTANT");
        assertThat(response.getBody().messages().get(0).content()).isEqualTo("Olá!");
        assertThat(response.getBody().messages().get(1).role()).isEqualTo("USER");
        assertThat(response.getBody().messages().get(1).content()).isEqualTo("Olá");
        assertThat(response.getBody().messages().get(1).contactDisplayName()).isEqualTo("Maria Silva");
        assertThat(response.getBody().messages().get(1).detectedIntent()).isEqualTo("greeting");
        assertThat(response.getBody().messages().get(0).contactDisplayName()).isNull();
        assertThat(response.getBody().messages().get(0).detectedIntent()).isNull();
        assertThat(response.getBody().botEnabledByPhone()).containsEntry("5511999999999", true);
    }

    @Test
    void getMessages_includesBotDisabledWhenConversationRowExists() {
        TenantId tenant = new TenantId("tenant-bot-off");
        Instant t0 = Instant.parse("2026-04-08T13:00:00Z");
        chatMessageRepository.save(
                new ChatMessage(
                        null,
                        tenant,
                        "5511888000000",
                        ChatMessageRole.USER,
                        "Oi",
                        ChatMessageStatus.SENT,
                        t0,
                        null,
                        null,
                        null));
        conversationBotStatePort.setBotEnabled(tenant, "5511888000000", false);

        ResponseEntity<ChatMessagesListResponse> response =
                restTemplate.exchange(
                        "/api/v1/messages?tenantId=tenant-bot-off",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().botEnabledByPhone()).containsEntry("5511888000000", false);
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

        ResponseEntity<ChatMessagesListResponse> get =
                restTemplate.exchange(
                        "/api/v1/messages?tenantId=tenant-retry",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {});

        assertThat(get.getBody()).isNotNull();
        var row = get.getBody().messages().stream().filter(m -> m.id() == id).findFirst();
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

    @Test
    void humanHandoff_disablesBot_returns204() {
        TenantId tenant = new TenantId("tenant-handoff");
        assertThat(conversationBotStatePort.isBotEnabled(tenant, "5511777000001")).isTrue();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"phoneNumber\":\"5511777000001\"}", headers);

        ResponseEntity<Void> res =
                restTemplate.exchange(
                        "/api/v1/conversations/human-handoff?tenantId=tenant-handoff",
                        HttpMethod.POST,
                        entity,
                        Void.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(conversationBotStatePort.isBotEnabled(tenant, "5511777000001")).isFalse();
    }

    @Test
    void humanHandoff_idempotentSecondCall_returns204WithoutDoubleSend() {
        TenantId tenant = new TenantId("tenant-handoff-idem");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"phoneNumber\":\"5511666000002\"}", headers);
        String url = "/api/v1/conversations/human-handoff?tenantId=tenant-handoff-idem";

        ResponseEntity<Void> first = restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
        ResponseEntity<Void> second = restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(conversationBotStatePort.isBotEnabled(tenant, "5511666000002")).isFalse();
    }

    @Test
    void enableBot_restoresBot_returns204() {
        TenantId tenant = new TenantId("tenant-enable-bot");
        conversationBotStatePort.setBotEnabled(tenant, "5511555000003", false);
        assertThat(conversationBotStatePort.isBotEnabled(tenant, "5511555000003")).isFalse();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"phoneNumber\":\"5511555000003\"}", headers);

        ResponseEntity<Void> res =
                restTemplate.exchange(
                        "/api/v1/conversations/enable-bot?tenantId=tenant-enable-bot",
                        HttpMethod.POST,
                        entity,
                        Void.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(conversationBotStatePort.isBotEnabled(tenant, "5511555000003")).isTrue();
    }

    @Test
    void conversationsMissingTenant_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"phoneNumber\":\"5511\"}", headers);

        ResponseEntity<String> res =
                restTemplate.exchange(
                        "/api/v1/conversations/human-handoff", HttpMethod.POST, entity, String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void humanReply_botStillActive_returns409() {
        TenantId tenant = new TenantId("tenant-human-409");
        Instant t0 = Instant.parse("2026-04-08T16:00:00Z");
        chatMessageRepository.save(
                new ChatMessage(
                        null,
                        tenant,
                        "5511999000001",
                        ChatMessageRole.USER,
                        "Oi",
                        ChatMessageStatus.SENT,
                        t0,
                        null,
                        null,
                        null));
        assertThat(conversationBotStatePort.isBotEnabled(tenant, "5511999000001")).isTrue();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity =
                new HttpEntity<>("{\"phoneNumber\":\"5511999000001\",\"text\":\"Olá do operador\"}", headers);

        ResponseEntity<String> res =
                restTemplate.exchange(
                        "/api/v1/messages/human-reply?tenantId=tenant-human-409",
                        HttpMethod.POST,
                        entity,
                        String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void humanReply_botDisabled_returns204_andPersistsAssistant() {
        TenantId tenant = new TenantId("tenant-human-ok");
        Instant t0 = Instant.parse("2026-04-08T17:00:00Z");
        chatMessageRepository.save(
                new ChatMessage(
                        null,
                        tenant,
                        "5511999000002",
                        ChatMessageRole.USER,
                        "Cliente",
                        ChatMessageStatus.SENT,
                        t0,
                        null,
                        null,
                        null));
        conversationBotStatePort.setBotEnabled(tenant, "5511999000002", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity =
                new HttpEntity<>("{\"phoneNumber\":\"5511999000002\",\"text\":\"Resposta humana\"}", headers);

        ResponseEntity<Void> post =
                restTemplate.exchange(
                        "/api/v1/messages/human-reply?tenantId=tenant-human-ok",
                        HttpMethod.POST,
                        entity,
                        Void.class);

        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ChatMessagesListResponse> get =
                restTemplate.exchange(
                        "/api/v1/messages?tenantId=tenant-human-ok",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {});

        assertThat(get.getBody()).isNotNull();
        var assistant =
                get.getBody().messages().stream()
                        .filter(m -> "5511999000002".equals(m.phoneNumber()) && "ASSISTANT".equals(m.role()))
                        .filter(m -> "Resposta humana".equals(m.content()))
                        .findFirst();
        assertThat(assistant).isPresent();
        assertThat(assistant.get().status()).isEqualTo("SENT");
    }

    @Test
    void humanReply_missingBody_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"phoneNumber\":\"5511\"}", headers);

        ResponseEntity<String> res =
                restTemplate.exchange(
                        "/api/v1/messages/human-reply?tenantId=tenant-x",
                        HttpMethod.POST,
                        entity,
                        String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
