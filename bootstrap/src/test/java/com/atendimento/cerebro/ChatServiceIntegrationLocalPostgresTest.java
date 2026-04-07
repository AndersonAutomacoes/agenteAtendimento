package com.atendimento.cerebro;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Mesmo fluxo que {@link ChatServiceIntegrationTest}, mas usando o Postgres do {@code docker-compose} na máquina
 * (sem Testcontainers). Exige {@code docker compose up -d} e credenciais como no {@code application.yml}
 * (localhost:5433, usuário {@code cerebro} — porta do docker-compose).
 * <p>Esta classe fica fora do {@code mvn test} padrão (Surefire exclude no {@code bootstrap/pom.xml}); rode com
 * {@code run-chat-it.cmd} ou {@code mvn -pl bootstrap -am test -Dtest=ChatServiceIntegrationLocalPostgresTest}.
 */
@SpringBootTest(
        properties = {
            "spring.ai.vectorstore.pgvector.initialize-schema=true",
            "spring.datasource.url=jdbc:postgresql://localhost:5433/cerebro",
            "spring.datasource.username=cerebro",
            "spring.datasource.password=cerebro"
        })
@ActiveProfiles("test")
class ChatServiceIntegrationLocalPostgresTest extends ChatServiceIntegrationBase {}
