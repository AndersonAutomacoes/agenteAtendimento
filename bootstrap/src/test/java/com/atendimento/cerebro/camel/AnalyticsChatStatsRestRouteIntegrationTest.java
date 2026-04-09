package com.atendimento.cerebro.camel;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import com.atendimento.cerebro.application.analytics.ChatSentiment;
import com.atendimento.cerebro.application.port.out.ChatAnalyticsRepository;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.ChatAnalyticsStatsHttpResponse;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.IntentCategoryCountHttp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class AnalyticsChatStatsRestRouteIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ChatAnalyticsRepository chatAnalyticsRepository;

    @Test
    void getAnalyticsStats_returnsIntentAndSentimentCounts() {
        TenantId tenant = new TenantId("tenant-chat-stats-api");
        chatAnalyticsRepository.upsert(tenant, "551199900001", ChatMainIntent.Venda, ChatSentiment.Positivo);
        chatAnalyticsRepository.upsert(tenant, "551199900002", ChatMainIntent.Venda, ChatSentiment.Negativo);
        chatAnalyticsRepository.upsert(tenant, "551199900003", ChatMainIntent.Orcamento, ChatSentiment.Positivo);

        ResponseEntity<ChatAnalyticsStatsHttpResponse> response =
                restTemplate.getForEntity(
                        "/api/v1/analytics/stats?tenantId=tenant-chat-stats-api",
                        ChatAnalyticsStatsHttpResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        ChatAnalyticsStatsHttpResponse body = response.getBody();
        assertThat(body.tenantId()).isEqualTo("tenant-chat-stats-api");
        assertThat(body.generatedAt()).isNotBlank();
        assertThat(body.intents()).hasSize(5);
        assertThat(body.sentiments()).hasSize(3);

        long venda = 0L;
        long orc = 0L;
        for (IntentCategoryCountHttp c : body.intents()) {
            if ("Venda".equals(c.category())) {
                venda = c.count();
            } else if ("Orçamento".equals(c.category())) {
                orc = c.count();
            }
        }
        assertThat(venda).isEqualTo(2L);
        assertThat(orc).isEqualTo(1L);

        long pos = 0L;
        long neg = 0L;
        for (IntentCategoryCountHttp s : body.sentiments()) {
            if ("Positivo".equals(s.category())) {
                pos = s.count();
            } else if ("Negativo".equals(s.category())) {
                neg = s.count();
            }
        }
        assertThat(pos).isEqualTo(2L);
        assertThat(neg).isEqualTo(1L);
    }

    @Test
    void getAnalyticsStats_withoutTenant_returns400() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/analytics/stats", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
