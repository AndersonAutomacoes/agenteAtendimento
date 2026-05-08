package com.atendimento.cerebro.application.dto.billing;

import com.atendimento.cerebro.domain.billing.BillingPlanTier;

public record TenantEntitlementDecision(boolean allowed, BillingPlanTier tier, String reasonCode) {}
