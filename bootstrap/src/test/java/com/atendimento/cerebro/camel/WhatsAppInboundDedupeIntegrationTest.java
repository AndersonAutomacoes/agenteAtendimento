package com.atendimento.cerebro.camel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.dto.ChatCommand;
import com.atendimento.cerebro.application.dto.ChatResult;
import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.application.port.out.InboundWhatsAppDeduperPort;
import com.atendimento.cerebro.application.port.out.WhatsAppOutboundPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.WhatsAppWebhookResponse;
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

/**
 * O mesmo {@code providerMessageId} no webhook não deve disparar chat/outbound duas vezes.
 */
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
class WhatsAppInboundDedupeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private ChatUseCase chatUseCase;

    @MockitoBean
    private WhatsAppOutboundPort whatsAppOutboundPort;

    @MockitoBean
    private InboundWhatsAppDeduperPort inboundWhatsAppDeduper;

    @Test
    void postWhatsApp_sameEvolutionKeyIdTwice_secondIgnored() {
        when(inboundWhatsAppDeduper.tryClaimInboundMessage(any(), any())).thenReturn(true, false);
        when(chatUseCase.chat(any())).thenReturn(new ChatResult("uma resposta"));

        String evolutionJson =
                """
                {
                  "event": "messages.upsert",
                  "data": {
                    "key": {
                      "remoteJid": "5511999999999@s.whatsapp.net",
                      "fromMe": false,
                      "id": "stable-provider-msg-id-001"
                    },
                    "message": { "conversation": "Olá" },
                    "messageType": "conversation"
                  }
                }
                """;

        ResponseEntity<WhatsAppWebhookResponse> first =
                postWebhook(evolutionJson, WhatsAppWebhookResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isNotNull().extracting(WhatsAppWebhookResponse::status).isEqualTo("processed");

        ResponseEntity<WhatsAppWebhookResponse> second =
                postWebhook(evolutionJson, WhatsAppWebhookResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull().extracting(WhatsAppWebhookResponse::status).isEqualTo("ignored");

        verify(chatUseCase, times(1)).chat(any(ChatCommand.class));
        verify(whatsAppOutboundPort, times(1))
                .sendMessage(eq(new TenantId("tenant-wa")), eq("5511999999999"), eq("uma resposta"));
    }

    private <T> ResponseEntity<T> postWebhook(String jsonBody, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        return restTemplate.exchange("/api/v1/whatsapp/webhook", HttpMethod.POST, entity, responseType);
    }
}
