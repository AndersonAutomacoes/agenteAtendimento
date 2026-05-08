package com.atendimento.cerebro.application.service.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.dto.billing.TenantEntitlementDecision;
import com.atendimento.cerebro.application.dto.billing.TenantSubscriptionSnapshot;
import com.atendimento.cerebro.domain.billing.BillingPlanTier;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class TenantEntitlementEvaluatorTest {

    private final TenantEntitlementEvaluator evaluator = new TenantEntitlementEvaluator(7);

    @Test
    void active_allows_through_end_of_period() {
        Instant start = Instant.parse("2026-05-01T00:00:00Z");
        Instant end = Instant.parse("2026-06-01T00:00:00Z");
        TenantSubscriptionSnapshot snap = new TenantSubscriptionSnapshot(
                "t1",
                "sub_1",
                "active",
                BillingPlanTier.PRO,
                start,
                end,
                false,
                null);
        TenantEntitlementDecision d = evaluator.evaluate(snap, Instant.parse("2026-05-15T00:00:00Z"));
        assertThat(d.allowed()).isTrue();
        assertThat(d.tier()).isEqualTo(BillingPlanTier.PRO);
        assertThat(d.reasonCode()).isEqualTo("OK_ACTIVE");
    }

    @Test
    void canceled_at_period_end_still_allowed_while_active() {
        TenantSubscriptionSnapshot snap = new TenantSubscriptionSnapshot(
                "t1",
                "sub_1",
                "active",
                BillingPlanTier.BASIC,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                true,
                null);
        TenantEntitlementDecision d = evaluator.evaluate(snap, Instant.parse("2026-05-20T00:00:00Z"));
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void past_due_within_grace_allows() {
        Instant pastDueSince = Instant.parse("2026-05-08T12:00:00Z");
        TenantSubscriptionSnapshot snap = new TenantSubscriptionSnapshot(
                "t1",
                "sub_1",
                "past_due",
                BillingPlanTier.ULTRA,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                false,
                pastDueSince);
        TenantEntitlementDecision d = evaluator.evaluate(snap, pastDueSince.plus(3, ChronoUnit.DAYS));
        assertThat(d.allowed()).isTrue();
        assertThat(d.reasonCode()).isEqualTo("OK_PAST_DUE_GRACE");
    }

    @Test
    void past_due_after_grace_denies() {
        Instant pastDueSince = Instant.parse("2026-05-08T12:00:00Z");
        TenantSubscriptionSnapshot snap = new TenantSubscriptionSnapshot(
                "t1",
                "sub_1",
                "past_due",
                BillingPlanTier.ULTRA,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                false,
                pastDueSince);
        TenantEntitlementDecision d = evaluator.evaluate(snap, pastDueSince.plus(8, ChronoUnit.DAYS));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasonCode()).isEqualTo("BLOCKED_PAST_DUE_GRACE_EXPIRED");
    }

    @Test
    void no_subscription_denies() {
        TenantEntitlementDecision d = evaluator.evaluate(null, Instant.now());
        assertThat(d.allowed()).isFalse();
        assertThat(d.reasonCode()).isEqualTo("BLOCKED_NO_SUBSCRIPTION");
    }
}
