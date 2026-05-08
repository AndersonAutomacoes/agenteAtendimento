package com.atendimento.cerebro.infrastructure.billing.webhook;

import com.stripe.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Recebe {@link Event} já verificado por assinatura HMAC e delega para {@link StripeBillingWebhookProcessor}
 * (inbox idempotente + sincronização de subscrição / perfis).
 */
@Component
public class StripeValidatedWebhookSink {

    private static final Logger log = LoggerFactory.getLogger(StripeValidatedWebhookSink.class);

    private final StripeBillingWebhookProcessor processor;

    public StripeValidatedWebhookSink(StripeBillingWebhookProcessor processor) {
        this.processor = processor;
    }

    public void accept(Event event) {
        log.debug("stripe webhook accepted id={} type={}", event.getId(), event.getType());
        processor.process(event);
    }
}
