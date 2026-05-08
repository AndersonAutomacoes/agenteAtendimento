package com.atendimento.cerebro.infrastructure.billing;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BillingStripeProperties.class)
public class BillingStripeConfiguration {

    private final BillingStripeProperties properties;

    public BillingStripeConfiguration(BillingStripeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void configureStripeApiKeyIfPresent() {
        String key = properties.secretKey();
        if (key != null && !key.isBlank()) {
            Stripe.apiKey = key;
        }
    }
}
