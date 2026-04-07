package com.atendimento.cerebro;

import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integração com Postgres via Testcontainers (isolado; ideal em CI/Linux).
 * <p>No Windows, o cliente Java do Testcontainers às vezes recebe HTTP 400 do Docker Engine mesmo com o Desktop
 * “ok”. Nesse caso use {@link ChatServiceIntegrationLocalPostgresTest} com {@code CEREBRO_IT_USE_LOCAL_PG=true} e
 * {@code docker compose up -d}.
 */
@SpringBootTest(properties = "spring.ai.vectorstore.pgvector.initialize-schema=true")
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisabledIfEnvironmentVariable(named = "CEREBRO_IT_USE_LOCAL_PG", matches = "(?i)^\\s*true\\s*$")
class ChatServiceIntegrationTest extends ChatServiceIntegrationBase {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
}
