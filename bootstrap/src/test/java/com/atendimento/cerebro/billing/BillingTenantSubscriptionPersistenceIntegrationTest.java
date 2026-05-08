package com.atendimento.cerebro.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.dto.billing.TenantSubscriptionSnapshot;
import com.atendimento.cerebro.application.port.out.PortalUserStorePort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.application.port.out.TenantSubscriptionPersistencePort;
import com.atendimento.cerebro.domain.billing.BillingPlanTier;
import com.atendimento.cerebro.domain.portal.PortalUser;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        properties = {
            "spring.ai.vectorstore.pgvector.initialize-schema=true",
            "spring.flyway.enabled=true"
        })
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisabledIfEnvironmentVariable(named = "CEREBRO_IT_USE_LOCAL_PG", matches = "(?i)^\\s*true\\s*$")
class BillingTenantSubscriptionPersistenceIntegrationTest {

    private static final String TENANT = "billing-persist-it";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private TenantSubscriptionPersistencePort subscriptionPort;

    @Autowired
    private TenantConfigurationStorePort tenantConfigurationStore;

    @Autowired
    private PortalUserStorePort portalUserStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTenant() {
        jdbcTemplate.update("DELETE FROM tenant_subscription WHERE tenant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM stripe_customer WHERE tenant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM portal_user WHERE tenant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM tenant_configuration WHERE tenant_id = ?", TENANT);
    }

    @Test
    void upsert_persistsAndPropagatesTierToTenantConfig_andPortalUsers() {
        TenantId tenantId = new TenantId(TENANT);
        tenantConfigurationStore.upsert(TenantConfiguration.defaults(tenantId));

        PortalUser user = new PortalUser(UUID.randomUUID(), "billing-it-owner-" + TENANT, tenantId, ProfileLevel.BASIC);
        portalUserStore.insert(user);

        TenantSubscriptionSnapshot snap = new TenantSubscriptionSnapshot(
                TENANT,
                "sub_test_integration",
                "cus_test_integration",
                "active",
                BillingPlanTier.PRO,
                "price_test_integration",
                "MONTH",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                false,
                null);

        subscriptionPort.upsertAndApplyProfileLevel(snap);

        Optional<TenantSubscriptionSnapshot> loaded = subscriptionPort.findByTenantId(TENANT);
        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().tier()).isEqualTo(BillingPlanTier.PRO);
        assertThat(loaded.orElseThrow().stripeSubscriptionId()).isEqualTo("sub_test_integration");

        String cfgProfile =
                jdbcTemplate.queryForObject(
                        "SELECT profile_level FROM tenant_configuration WHERE tenant_id = ?", String.class, TENANT);
        assertThat(cfgProfile).isEqualTo("PRO");

        String portalProfile =
                jdbcTemplate.queryForObject(
                        "SELECT profile_level FROM portal_user WHERE tenant_id = ?", String.class, TENANT);
        assertThat(portalProfile).isEqualTo("PRO");
    }
}
