package com.atendimento.cerebro.camel;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.dto.DashboardSummary;
import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.monitoring.ChatMessageRole;
import com.atendimento.cerebro.domain.monitoring.ChatMessageStatus;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class DashboardRestRouteIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Test
    void getDashboardSummary_returnsSeriesAndCounts() {
        TenantId tenant = new TenantId("tenant-dashboard");
        Instant now = Instant.now();
        chatMessageRepository.save(
                new ChatMessage(
                        null,
                        tenant,
                        "5511999990001",
                        ChatMessageRole.USER,
                        "Olá",
                        ChatMessageStatus.SENT,
                        now.minus(1, ChronoUnit.HOURS),
                        "Cliente Um",
                        "https://example.com/a.png",
                        "greeting"));
        chatMessageRepository.save(
                new ChatMessage(
                        null,
                        tenant,
                        "5511999990001",
                        ChatMessageRole.ASSISTANT,
                        "Oi!",
                        ChatMessageStatus.SENT,
                        now.minus(59, ChronoUnit.MINUTES),
                        null,
                        null,
                        null));
        chatMessageRepository.save(
                new ChatMessage(
                        null,
                        tenant,
                        "5511999990002",
                        ChatMessageRole.USER,
                        "Ajuda",
                        ChatMessageStatus.SENT,
                        now.minus(30, ChronoUnit.MINUTES),
                        null,
                        null,
                        "support"));

        ResponseEntity<DashboardSummary> response =
                restTemplate.getForEntity(
                        "/api/v1/dashboard/summary?tenantId=tenant-dashboard&range=day", DashboardSummary.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        DashboardSummary s = response.getBody();
        assertThat(s.totalClients()).isEqualTo(2);
        assertThat(s.messagesToday()).isGreaterThanOrEqualTo(2);
        assertThat(s.aiRatePercent()).isNotNull();
        assertThat(s.aiRatePercent()).isLessThanOrEqualTo(100.0);
        assertThat(s.series()).hasSize(24);
        assertThat(s.recentInteractions()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(s.recentInteractions().get(0).detectedIntent()).isIn("support", "greeting");

        ResponseEntity<DashboardSummary> week =
                restTemplate.getForEntity(
                        "/api/v1/dashboard/summary?tenantId=tenant-dashboard&range=week",
                        DashboardSummary.class);
        assertThat(week.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(week.getBody()).isNotNull();
        assertThat(week.getBody().series()).hasSize(7);
    }

    @Test
    void getDashboardSummary_withoutTenant_returns400() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/dashboard/summary", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
