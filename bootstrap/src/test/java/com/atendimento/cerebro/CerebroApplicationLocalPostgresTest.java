package com.atendimento.cerebro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.application.port.out.AIEnginePort;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Mesmo objetivo de {@link CerebroApplicationTest}, usando o Postgres do {@code docker-compose} (sem Testcontainers).
 * <p>Beans de teste com {@code @Primary} substituem OpenAI para subir o contexto sem chamar a API OpenAI.
 * <p>Fora do {@code mvn test} padrão (Surefire exclude); use {@code run-chat-it.cmd} ou {@code -Dtest=...}.
 */
@SpringBootTest(
        properties = {
            "spring.ai.vectorstore.pgvector.initialize-schema=true",
            "spring.datasource.url=jdbc:postgresql://localhost:5433/cerebro",
            "spring.datasource.username=cerebro",
            "spring.datasource.password=cerebro"
        })
@ActiveProfiles("test")
@Import(CerebroApplicationLocalPostgresTest.LocalAiMocks.class)
class CerebroApplicationLocalPostgresTest {

    @Autowired
    private ChatUseCase chatUseCase;

    @Test
    void contextLoads_andChatUseCaseIsWired() {
        assertThat(chatUseCase).isNotNull();
    }

    @TestConfiguration
    static class LocalAiMocks {

        @Bean
        @Primary
        AIEnginePort aiEnginePort() {
            return mock(AIEnginePort.class);
        }

        @Bean
        @Primary
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }
    }
}
