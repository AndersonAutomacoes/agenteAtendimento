package com.atendimento.cerebro.camel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.dto.ChatCommand;
import com.atendimento.cerebro.application.dto.ChatResult;
import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.application.port.out.ConversationBotStatePort;
import com.atendimento.cerebro.application.port.out.WhatsAppOutboundPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.ChatFallbackMessages;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.IngestErrorResponse;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.WhatsAppWebhookResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.vectorstore.pgvector.initialize-schema=true")
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "cerebro.whatsapp.tenants[5511999999999]=tenant-wa",
        })
@DisabledIfEnvironmentVariable(named = "CEREBRO_IT_USE_LOCAL_PG", matches = "(?i)^\\s*true\\s*$")
class WhatsAppIntegrationRouteIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ConversationBotStatePort conversationBotStatePort;

    @MockitoBean
    private ChatUseCase chatUseCase;

    @MockitoBean
    private WhatsAppOutboundPort whatsAppOutboundPort;

    @AfterEach
    void resetConversationBotState() {
        conversationBotStatePort.setBotEnabled(new TenantId("tenant-wa"), "5511999999999", true);
    }

    @Test
    void postWhatsApp_simpleJson_returnsProcessed() {
        when(chatUseCase.chat(any())).thenReturn(new ChatResult("Resposta da IA"));

        ResponseEntity<WhatsAppWebhookResponse> response =
                postWebhook("{\"from\":\"5511999999999\",\"text\":\"Olá\"}", WhatsAppWebhookResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().extracting(WhatsAppWebhookResponse::status).isEqualTo("processed");
        verify(chatUseCase).chat(any(ChatCommand.class));
        verify(whatsAppOutboundPort)
                .sendMessage(eq(new TenantId("tenant-wa")), eq("5511999999999"), eq("Resposta da IA"));
    }

    @Test
    void postWhatsApp_metaEnvelope_returnsProcessed() {
        when(chatUseCase.chat(any())).thenReturn(new ChatResult("ok meta"));

        String metaJson =
                """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "changes": [
                        {
                          "value": {
                            "messages": [
                              {
                                "from": "5511999999999",
                                "type": "text",
                                "text": { "body": "Olá" }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        ResponseEntity<WhatsAppWebhookResponse> response = postWebhook(metaJson, WhatsAppWebhookResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().extracting(WhatsAppWebhookResponse::status).isEqualTo("processed");
        verify(whatsAppOutboundPort)
                .sendMessage(eq(new TenantId("tenant-wa")), eq("5511999999999"), eq("ok meta"));
    }

    @Test
    void postWhatsApp_noTextMessages_returnsIgnored() {
        String json = "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":[]}}]}]}";

        ResponseEntity<WhatsAppWebhookResponse> response = postWebhook(json, WhatsAppWebhookResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().extracting(WhatsAppWebhookResponse::status).isEqualTo("ignored");
        verifyNoInteractions(whatsAppOutboundPort);
    }

    @Test
    void postWhatsApp_unknownNumber_returns404() {
        ResponseEntity<IngestErrorResponse> response =
                postWebhook("{\"from\":\"5599999999999\",\"text\":\"Olá\"}", IngestErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isNotEmpty();
        verifyNoInteractions(whatsAppOutboundPort);
    }

    @Test
    void postWhatsApp_blankText_returns400() {
        ResponseEntity<IngestErrorResponse> response =
                postWebhook("{\"from\":\"5511999999999\",\"text\":\"\"}", IngestErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(whatsAppOutboundPort);
    }

    @Test
    void postWhatsApp_whenUseCaseThrows_returns503AndFriendlyMessage() {
        when(chatUseCase.chat(any())).thenThrow(new RuntimeException("falha simulada"));

        ResponseEntity<ChatResult> response =
                postWebhook("{\"from\":\"5511999999999\",\"text\":\"Olá\"}", ChatResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody())
                .isNotNull()
                .extracting(ChatResult::assistantMessage)
                .isEqualTo(ChatFallbackMessages.MAINTENANCE);
        verify(whatsAppOutboundPort)
                .sendMessage(
                        eq(new TenantId("tenant-wa")),
                        eq("5511999999999"),
                        eq(ChatFallbackMessages.MAINTENANCE));
    }

    @Test
    void postWhatsApp_botDisabled_skipsChatAndSendsHumanHandoffStatus() {
        conversationBotStatePort.setBotEnabled(new TenantId("tenant-wa"), "5511999999999", false);

        ResponseEntity<WhatsAppWebhookResponse> response =
                postWebhook("{\"from\":\"5511999999999\",\"text\":\"Olá\"}", WhatsAppWebhookResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().extracting(WhatsAppWebhookResponse::status).isEqualTo("human_handoff");
        verifyNoInteractions(chatUseCase);
        verifyNoInteractions(whatsAppOutboundPort);
    }

    private <T> ResponseEntity<T> postWebhook(String jsonBody, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return restTemplate.exchange("/api/v1/whatsapp/webhook", HttpMethod.POST, entity, responseType);
    }
}
