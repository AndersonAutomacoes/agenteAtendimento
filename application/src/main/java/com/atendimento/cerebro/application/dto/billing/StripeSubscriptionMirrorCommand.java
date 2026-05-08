package com.atendimento.cerebro.application.dto.billing;

import com.atendimento.cerebro.domain.billing.BillingPlanTier;
import java.time.Instant;

/** Input neutro construído a partir de Stripe (infra) antes de gravar espelho + perfis. */
public record StripeSubscriptionMirrorCommand(
        String tenantId,
        String stripeSubscriptionId,
        String stripeCustomerId,
        String stripeStatus,
        BillingPlanTier tier,
        String priceId,
        String billingInterval,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant pastDueSince) {

    public TenantSubscriptionSnapshot toSnapshot() {
        return new TenantSubscriptionSnapshot(
                tenantId,
                stripeSubscriptionId,
                stripeCustomerId,
                stripeStatus,
                tier,
                priceId,
                billingInterval,
                currentPeriodStart,
                currentPeriodEnd,
                cancelAtPeriodEnd,
                pastDueSince);
    }
}
