package com.atendimento.cerebro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        properties = {
            "spring.ai.vectorstore.pgvector.initialize-schema=true",
            "spring.flyway.enabled=true"
        })
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisabledIfEnvironmentVariable(named = "CEREBRO_IT_USE_LOCAL_PG", matches = "(?i)^\\s*true\\s*$")
class PortalSecurityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void analytics_summary_sem_token_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary").param("tenantId", "t-integration"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_sem_bearer_retorna401() throws Exception {
        mockMvc.perform(
                        post("/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"inviteCode\":\"qualquer\"}"))
                .andExpect(status().isUnauthorized());
    }
}
