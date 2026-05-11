package com.atendimento.cerebro.infrastructure.billing.webhook;

import com.atendimento.cerebro.infrastructure.billing.BillingStripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final BillingStripeProperties stripeProperties;
    private final StripeValidatedWebhookSink sink;

    public StripeWebhookController(BillingStripeProperties stripeProperties, StripeValidatedWebhookSink sink) {
        this.stripeProperties = stripeProperties;
        this.sink = sink;
    }

    /**
     * Stripe usa {@code Content-Type: application/json} sem charset garantido — não restringir a um único string.
     * <p>Dois paths: o canónico {@code …/stripe} e {@code …/webhook} (sem sufixo) para alinhar a URLs comuns em
     * reverse-proxy / Stripe CLI onde se omite {@code /stripe} por engano.
     */
    @PostMapping(
            path = {"/v1/billing/webhook/stripe", "/v1/billing/webhook", "/v1/billing/webhook/"},
            consumes = {MediaType.APPLICATION_JSON_VALUE, "application/json; charset=utf-8"})
    public ResponseEntity<String> receive(
            @RequestBody byte[] payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {
        if (stripeSignature == null || stripeSignature.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("missing signature");
        }
        List<String> webhookSecrets = stripeProperties.webhookSecretsForVerification();
        if (webhookSecrets.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("webhook not configured");
        }
        String body = new String(payload, StandardCharsets.UTF_8);
        Event event = null;
        for (String secret : webhookSecrets) {
            try {
                event = Webhook.constructEvent(body, stripeSignature, secret);
                break;
            } catch (SignatureVerificationException ignored) {
                // tenta o próximo segredo (ex.: Dashboard vs `stripe listen`)
            }
        }
        if (event == null) {
            log.warn(
                    "Stripe webhook: assinatura inválida com {} segredo(s). Com `stripe listen --forward-to`, o "
                            + "`whsec_…` do terminal tem de estar em STRIPE_WEBHOOK_SECRET ou STRIPE_WEBHOOK_CLI_SECRET "
                            + "(é diferente do segredo do endpoint no Dashboard).",
                    webhookSecrets.size());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
        }
        sink.accept(event);
        return ResponseEntity.ok("received");
    }
}
