package com.atendimento.cerebro.application.service.billing;

import com.atendimento.cerebro.application.dto.billing.TenantEntitlementDecision;
import com.atendimento.cerebro.application.dto.billing.TenantSubscriptionSnapshot;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Decide acesso ao produto a partir do espelho local da Stripe (§6 da spec billing).
 */
public class TenantEntitlementEvaluator {

    private final int pastDueGraceDays;

    public TenantEntitlementEvaluator(int pastDueGraceDays) {
        this.pastDueGraceDays = pastDueGraceDays;
    }

    public TenantEntitlementDecision evaluate(TenantSubscriptionSnapshot subscription, Instant now) {
        if (subscription == null) {
            return new TenantEntitlementDecision(false, null, "BLOCKED_NO_SUBSCRIPTION");
        }
        String status = subscription.stripeStatus();
        if ("active".equalsIgnoreCase(status) || "trialing".equalsIgnoreCase(status)) {
            if (!now.isBefore(subscription.currentPeriodStart())
                    && now.isBefore(subscription.currentPeriodEnd())) {
                return new TenantEntitlementDecision(true, subscription.tier(), "OK_ACTIVE");
            }
            if (!now.isBefore(subscription.currentPeriodEnd())) {
                return blocked("BLOCKED_PERIOD_ENDED");
            }
            return blocked("BLOCKED_BEFORE_PERIOD_START");
        }
        if ("past_due".equalsIgnoreCase(status)) {
            Instant since = subscription.pastDueSince() != null ? subscription.pastDueSince() : now;
            long days = ChronoUnit.DAYS.between(since, now);
            if (days < pastDueGraceDays) {
                return new TenantEntitlementDecision(true, subscription.tier(), "OK_PAST_DUE_GRACE");
            }
            return new TenantEntitlementDecision(false, null, "BLOCKED_PAST_DUE_GRACE_EXPIRED");
        }
        if ("canceled".equalsIgnoreCase(status) || "unpaid".equalsIgnoreCase(status)) {
            return blocked("BLOCKED_SUBSCRIPTION_TERMINAL_STATE");
        }
        return blocked("BLOCKED_UNSUPPORTED_STATUS_" + status);
    }

    private static TenantEntitlementDecision blocked(String code) {
        return new TenantEntitlementDecision(false, null, code);
    }
}
