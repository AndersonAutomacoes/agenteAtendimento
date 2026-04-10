package com.atendimento.cerebro.camel;

import static com.atendimento.cerebro.testsupport.AnalyticsIntegrationTestSupport.get;
import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.testsupport.AnalyticsIntegrationAuth;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
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
class AnalyticsExportRestRouteIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exportCsv_returnsUtf8Attachment() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now();
        String q = String.format(
                "/api/v1/analytics/export?tenantId=t-export&startDate=%s&endDate=%s&format=csv&locale=pt-BR",
                start, end);

        ResponseEntity<byte[]> response = get(restTemplate, q, "t-export", byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("Data/Hora");
        assertThat(response.getHeaders().getContentType()).isNotNull();
    }

    @Test
    void exportPdf_returnsPdfBytes() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now();
        String q = String.format(
                "/api/v1/analytics/export?tenantId=t-export&startDate=%s&endDate=%s&format=pdf&locale=en",
                start, end);

        ResponseEntity<byte[]> response = get(restTemplate, q, "t-export", byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isGreaterThan(100);
        assertThat(new String(response.getBody(), 0, 5, StandardCharsets.UTF_8)).startsWith("%PDF");
    }

    @Test
    void export_semAuthorization_retorna401() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(
                        "/api/v1/analytics/export?format=csv&startDate=2026-01-01T00:00:00Z&endDate=2026-01-02T00:00:00Z",
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
