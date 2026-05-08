package com.atendimento.cerebro.infrastructure.billing.webhook;

import com.stripe.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Recebe {@link Event} já verificado por assinatura HMAC. Na Task 8 o corpo passa a sincronizar BD via
 * {@code BillingSubscriptionSyncService}.
 */
@Component
public class StripeValidatedWebhookSink {

    private static final Logger log = LoggerFactory.getLogger(StripeValidatedWebhookSink.class);

    public void accept(Event event) {
        log.info("stripe webhook event id={} type={}", event.getId(), event.getType());
    }
}
