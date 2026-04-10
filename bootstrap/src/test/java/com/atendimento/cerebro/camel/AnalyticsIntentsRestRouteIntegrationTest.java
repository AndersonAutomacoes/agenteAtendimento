package com.atendimento.cerebro.camel;

import static com.atendimento.cerebro.testsupport.AnalyticsIntegrationTestSupport.get;
import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.analytics.AnalyticsIntentTrigger;
import com.atendimento.cerebro.application.analytics.ConversationSentiment;
import com.atendimento.cerebro.application.analytics.PrimaryIntentCategory;
import com.atendimento.cerebro.application.port.out.AnalyticsIntentsRepository;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.AnalyticsIntentsHttpResponse;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.IntentCategoryCountHttp;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.SentimentCountHttp;
import com.atendimento.cerebro.testsupport.AnalyticsIntegrationAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.vectorstore.pgvector.initialize-schema=true")
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@AnalyticsIntegrationAuth
@DisabledIfEnvironmentVariable(named = "CEREBRO_IT_USE_LOCAL_PG", matches = "(?i)^\\s*true\\s*$")
class AnalyticsIntentsRestRouteIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AnalyticsIntentsRepository analyticsIntentsRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAnalyticsIntents_returnsCountsPerCategory() {
        TenantId tenant = new TenantId("tenant-intents-api");
        analyticsIntentsRepository.insert(
                tenant,
                "551188880001",
                PrimaryIntentCategory.ORCAMENTO,
                ConversationSentiment.POSITIVO,
                AnalyticsIntentTrigger.MESSAGE_THRESHOLD,
                4,
                null,
                "ORCAMENTO|POSITIVO");
        analyticsIntentsRepository.insert(
                tenant,
                "551188880002",
                PrimaryIntentCategory.AGENDAMENTO,
                ConversationSentiment.NEUTRO,
                AnalyticsIntentTrigger.MESSAGE_THRESHOLD,
                4,
                null,
                "AGENDAMENTO|NEUTRO");
        analyticsIntentsRepository.insert(
                tenant,
                "551188880003",
                PrimaryIntentCategory.ORCAMENTO,
                ConversationSentiment.POSITIVO,
                AnalyticsIntentTrigger.MESSAGE_THRESHOLD,
                8,
                null,
                "ORCAMENTO|POSITIVO");

        ResponseEntity<AnalyticsIntentsHttpResponse> response =
                get(
                        restTemplate,
                        "/api/v1/analytics/intents?tenantId=tenant-intents-api&days=30",
                        "tenant-intents-api",
                        AnalyticsIntentsHttpResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AnalyticsIntentsHttpResponse body = response.getBody();
        assertThat(body.tenantId()).isEqualTo("tenant-intents-api");
        assertThat(body.days()).isEqualTo(30);
        assertThat(body.counts()).hasSize(5);
        long orc = 0L;
        long ag = 0L;
        for (IntentCategoryCountHttp c : body.counts()) {
            if ("ORCAMENTO".equals(c.category())) {
                orc = c.count();
            } else if ("AGENDAMENTO".equals(c.category())) {
                ag = c.count();
            }
        }
        assertThat(orc).isEqualTo(2L);
        assertThat(ag).isEqualTo(1L);
        assertThat(body.previousCounts()).hasSize(5);
        assertThat(body.previousPeriodStart()).isNotBlank();
        assertThat(body.previousPeriodEnd()).isNotBlank();
        assertThat(body.sentimentCounts()).hasSize(3);
        long pos = 0L;
        long neu = 0L;
        for (SentimentCountHttp s : body.sentimentCounts()) {
            if ("POSITIVO".equals(s.sentiment())) {
                pos = s.count();
            } else if ("NEUTRO".equals(s.sentiment())) {
                neu = s.count();
            }
        }
        assertThat(pos).isEqualTo(2L);
        assertThat(neu).isEqualTo(1L);
    }

    @Test
    void getAnalyticsIntents_semAuthorization_retorna401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/analytics/intents", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
