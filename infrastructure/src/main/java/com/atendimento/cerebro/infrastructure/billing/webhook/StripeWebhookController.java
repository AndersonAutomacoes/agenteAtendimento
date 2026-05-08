package com.atendimento.cerebro.infrastructure.billing.webhook;

import com.atendimento.cerebro.infrastructure.billing.BillingStripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {

    private final BillingStripeProperties stripeProperties;
    private final StripeValidatedWebhookSink sink;

    public StripeWebhookController(BillingStripeProperties stripeProperties, StripeValidatedWebhookSink sink) {
        this.stripeProperties = stripeProperties;
        this.sink = sink;
    }

    /** Stripe usa {@code Content-Type: application/json} sem charset garantido — não restringir a um único string. */
    @PostMapping(
            path = "/v1/billing/webhook/stripe",
            consumes = {MediaType.APPLICATION_JSON_VALUE, "application/json; charset=utf-8"})
    public ResponseEntity<String> receive(
            @RequestBody byte[] payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {
        if (stripeSignature == null || stripeSignature.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("missing signature");
        }
        String webhookSecret = stripeProperties.webhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("webhook not configured");
        }
        try {
            String body = new String(payload, StandardCharsets.UTF_8);
            Event event = Webhook.constructEvent(body, stripeSignature, webhookSecret);
            sink.accept(event);
            return ResponseEntity.ok("received");
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
        }
    }
}
