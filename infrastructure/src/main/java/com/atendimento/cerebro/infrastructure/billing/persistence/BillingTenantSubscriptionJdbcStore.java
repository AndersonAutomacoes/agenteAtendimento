package com.atendimento.cerebro.infrastructure.billing.persistence;

import com.atendimento.cerebro.application.dto.billing.TenantSubscriptionSnapshot;
import com.atendimento.cerebro.application.port.out.TenantSubscriptionPersistencePort;
import com.atendimento.cerebro.domain.billing.BillingPlanTier;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BillingTenantSubscriptionJdbcStore implements TenantSubscriptionPersistencePort {

    private static final Logger log = LoggerFactory.getLogger(BillingTenantSubscriptionJdbcStore.class);

    private final JdbcClient jdbcClient;

    public BillingTenantSubscriptionJdbcStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void upsertAndApplyProfileLevel(TenantSubscriptionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        String tenantId = snapshot.tenantId();
        validateBillingInterval(snapshot.billingInterval());

        jdbcClient
                .sql(
                        """
                        INSERT INTO tenant_subscription (
                            tenant_id, stripe_subscription_id, stripe_customer_id, stripe_status, tier,
                            price_id, billing_interval, current_period_start, current_period_end,
                            cancel_at_period_end, past_due_since, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                        ON CONFLICT (tenant_id) DO UPDATE SET
                            stripe_subscription_id = EXCLUDED.stripe_subscription_id,
                            stripe_customer_id = EXCLUDED.stripe_customer_id,
                            stripe_status = EXCLUDED.stripe_status,
                            tier = EXCLUDED.tier,
                            price_id = EXCLUDED.price_id,
                            billing_interval = EXCLUDED.billing_interval,
                            current_period_start = EXCLUDED.current_period_start,
                            current_period_end = EXCLUDED.current_period_end,
                            cancel_at_period_end = EXCLUDED.cancel_at_period_end,
                            past_due_since = EXCLUDED.past_due_since,
                            updated_at = NOW()
                        """)
                .param(tenantId)
                .param(snapshot.stripeSubscriptionId())
                .param(snapshot.stripeCustomerId())
                .param(snapshot.stripeStatus())
                .param(snapshot.tier().name())
                .param(snapshot.priceId())
                .param(snapshot.billingInterval())
                .param(Timestamp.from(snapshot.currentPeriodStart()))
                .param(Timestamp.from(snapshot.currentPeriodEnd()))
                .param(snapshot.cancelAtPeriodEnd())
                .param(snapshot.pastDueSince() != null ? Timestamp.from(snapshot.pastDueSince()) : null)
                .update();

        String tierName = snapshot.tier().toProfileLevel().name();

        int nCfg =
                jdbcClient
                        .sql("UPDATE tenant_configuration SET profile_level = ? WHERE tenant_id = ?")
                        .param(tierName)
                        .param(tenantId)
                        .update();
        int nPu =
                jdbcClient
                        .sql("UPDATE portal_user SET profile_level = ? WHERE tenant_id = ?")
                        .param(tierName)
                        .param(tenantId)
                        .update();

        log.debug(
                "billing sync tenant_id={} tier={} rows tenant_configuration={} portal_user={}",
                tenantId,
                tierName,
                nCfg,
                nPu);
    }

    private static void validateBillingInterval(String raw) {
        if (!"MONTH".equals(raw) && !"YEAR".equals(raw)) {
            throw new IllegalArgumentException("billingInterval must be MONTH or YEAR, got: " + raw);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantSubscriptionSnapshot> findByTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient
                .sql(
                        """
                        SELECT tenant_id, stripe_subscription_id, stripe_customer_id, stripe_status, tier,
                               price_id, billing_interval, current_period_start, current_period_end,
                               cancel_at_period_end, past_due_since
                        FROM tenant_subscription WHERE tenant_id = ?
                        """)
                .param(tenantId)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    private static TenantSubscriptionSnapshot mapRow(ResultSet rs) throws SQLException {
        Timestamp pdu = rs.getTimestamp("past_due_since");
        return new TenantSubscriptionSnapshot(
                rs.getString("tenant_id"),
                rs.getString("stripe_subscription_id"),
                rs.getString("stripe_customer_id"),
                rs.getString("stripe_status"),
                BillingPlanTier.valueOf(rs.getString("tier")),
                rs.getString("price_id"),
                rs.getString("billing_interval"),
                rs.getTimestamp("current_period_start").toInstant(),
                rs.getTimestamp("current_period_end").toInstant(),
                rs.getBoolean("cancel_at_period_end"),
                pdu != null ? pdu.toInstant() : null);
    }
}
