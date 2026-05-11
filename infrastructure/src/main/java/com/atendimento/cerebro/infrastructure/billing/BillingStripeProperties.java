package com.atendimento.cerebro.infrastructure.billing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais e URLs Stripe + mapa opcional {@code price_id → BASIC|PRO|ULTRA}.
 */
@ConfigurationProperties(prefix = "stripe")
public record BillingStripeProperties(
        String secretKey,
        /** Segredo do endpoint de webhook no Dashboard Stripe (produção / staging). */
        String webhookSecret,
        /**
         * Segredo opcional do {@code stripe listen} (outro {@code whsec_…} que o CLI imprime ao arrancar). Sem isto,
         * pedidos reencaminhados pelo CLI falham verificação se só estiver configurado o segredo do Dashboard.
         */
        String webhookCliSecret,
        String successUrl,
        String cancelUrl,
        /** URL para onde o Stripe redireciona após sair do Customer Portal. */
        String portalReturnUrl,
        LinkedHashMap<String, String> priceTier) {

    /** Stripe {@code Price} ids → nível cobrado. */
    public Map<String, String> priceTierNonNull() {
        return priceTier != null ? Map.copyOf(priceTier) : Map.of();
    }

    /**
     * Segredos para {@link com.stripe.net.Webhook#constructEvent} — ordem: Dashboard, depois CLI (se definido).
     */
    public List<String> webhookSecretsForVerification() {
        List<String> out = new ArrayList<>(2);
        addIfSecretPresent(webhookSecret, out);
        addIfSecretPresent(webhookCliSecret, out);
        return List.copyOf(out);
    }

    private static void addIfSecretPresent(String raw, List<String> out) {
        if (raw == null) {
            return;
        }
        String t = raw.strip();
        if (!t.isEmpty()) {
            out.add(t);
        }
    }
}
