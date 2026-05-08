package com.atendimento.cerebro.infrastructure.billing;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais e URLs Stripe + mapa opcional {@code price_id → BASIC|PRO|ULTRA}.
 */
@ConfigurationProperties(prefix = "stripe")
public record BillingStripeProperties(
        String secretKey,
        String webhookSecret,
        String successUrl,
        String cancelUrl,
        /** URL para onde o Stripe redireciona após sair do Customer Portal. */
        String portalReturnUrl,
        LinkedHashMap<String, String> priceTier) {

    /** Stripe {@code Price} ids → nível cobrado. */
    public Map<String, String> priceTierNonNull() {
        return priceTier != null ? Map.copyOf(priceTier) : Map.of();
    }
}
