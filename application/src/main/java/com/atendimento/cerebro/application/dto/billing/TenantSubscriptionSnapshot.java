package com.atendimento.cerebro.application.dto.billing;

import com.atendimento.cerebro.domain.billing.BillingPlanTier;
import java.time.Instant;

public record TenantSubscriptionSnapshot(
        String tenantId,
        String stripeSubscriptionId,
        String stripeCustomerId,
        String stripeStatus,
        BillingPlanTier tier,
        String priceId,
        /** {@code MONTH} ou {@code YEAR} (coluna {@code tenant_subscription.billing_interval}). */
        String billingInterval,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant pastDueSince) {}
