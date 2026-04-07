package com.atendimento.cerebro;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.port.in.ChatUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.ai.vectorstore.pgvector.initialize-schema=true")
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisabledIfEnvironmentVariable(named = "CEREBRO_IT_USE_LOCAL_PG", matches = "(?i)^\\s*true\\s*$")
class CerebroApplicationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private ChatUseCase chatUseCase;

    @Test
    void contextLoads_andChatUseCaseIsWired() {
        assertThat(chatUseCase).isNotNull();
    }
}
