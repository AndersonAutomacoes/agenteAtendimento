package com.atendimento.cerebro.domain.billing;

import com.atendimento.cerebro.domain.tenant.ProfileLevel;

/**
 * Tiers cobrados via Stripe ({@link ProfileLevel} BASIC / PRO / ULTRA). COMERCIAL é interno/backoffice — não há Price Stripe.
 */
public enum BillingPlanTier {
    BASIC,
    PRO,
    ULTRA;

    public ProfileLevel toProfileLevel() {
        return switch (this) {
            case BASIC -> ProfileLevel.BASIC;
            case PRO -> ProfileLevel.PRO;
            case ULTRA -> ProfileLevel.ULTRA;
        };
    }

    public static BillingPlanTier fromProfileLevel(ProfileLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        return switch (level) {
            case BASIC -> BASIC;
            case PRO -> PRO;
            case ULTRA -> ULTRA;
            case COMERCIAL -> throw new IllegalArgumentException("COMERCIAL is not a Stripe-paid tier");
        };
    }
}
