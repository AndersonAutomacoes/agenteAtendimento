package com.atendimento.cerebro.application.dto.billing;

import com.atendimento.cerebro.domain.billing.BillingPlanTier;
import java.time.Instant;

public record TenantSubscriptionSnapshot(
        String tenantId,
        String stripeSubscriptionId,
        String stripeStatus,
        BillingPlanTier tier,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant pastDueSince) {}
