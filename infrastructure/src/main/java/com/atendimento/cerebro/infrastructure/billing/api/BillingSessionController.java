package com.atendimento.cerebro.infrastructure.billing.api;

import com.atendimento.cerebro.infrastructure.billing.BillingStripeProperties;
import com.atendimento.cerebro.infrastructure.billing.persistence.BillingStripeCustomerAccess;
import com.atendimento.cerebro.infrastructure.security.PortalAuthenticationToken;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.billingportal.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.Mode;
import com.stripe.param.checkout.SessionCreateParams.SubscriptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/billing")
public class BillingSessionController {

    private static final Logger log = LoggerFactory.getLogger(BillingSessionController.class);

    private final BillingStripeProperties stripeProperties;
    private final BillingStripeCustomerAccess customerAccess;

    public BillingSessionController(BillingStripeProperties stripeProperties, BillingStripeCustomerAccess customerAccess) {
        this.stripeProperties = stripeProperties;
        this.customerAccess = customerAccess;
    }

    @PostMapping(path = "/checkout-session", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createCheckoutSession(
            @RequestBody BillingSessionApiDtos.CheckoutSessionRequest body, Authentication authentication) {
        ResponseEntity<?> configured = requireStripeConfigured();
        if (configured != null) {
            return configured;
        }
        PortalAuthenticationToken portal = requirePortalUser(authentication);
        if (portal == null) {
            return billingForbidden();
        }

        if (body == null
                || body.tenantId() == null
                || body.tenantId().isBlank()
                || body.priceId() == null
                || body.priceId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new BillingSessionApiDtos.ErrorResponse("tenantId e priceId são obrigatórios"));
        }

        String tenantId = body.tenantId().strip();
        if (!tenantId.equals(portal.getTenantId())) {
            return billingForbidden();
        }

        if (!stripeProperties.priceTierNonNull().containsKey(body.priceId())) {
            return ResponseEntity.badRequest()
                    .body(new BillingSessionApiDtos.ErrorResponse("priceId não reconhecido para este produto"));
        }

        String firebaseUid = portal.getFirebaseUid();
        String stripeCustomerId =
                customerAccess.findStripeCustomerIdForBillingActor(tenantId, firebaseUid).orElse(null);
        if (stripeCustomerId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BillingSessionApiDtos.ErrorResponse("cliente_stripe_não_configurado"));
        }

        String successUrl = stripeProperties.successUrl();
        String cancelUrl = stripeProperties.cancelUrl();
        if (successUrl == null || successUrl.isBlank() || cancelUrl == null || cancelUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new BillingSessionApiDtos.ErrorResponse("checkout_urls_não_configuradas"));
        }

        try {
            com.stripe.param.checkout.SessionCreateParams params =
                    com.stripe.param.checkout.SessionCreateParams.builder()
                            .setMode(Mode.SUBSCRIPTION)
                            .setCustomer(stripeCustomerId)
                            .addLineItem(LineItem.builder().setPrice(body.priceId()).setQuantity(1L).build())
                            .setSuccessUrl(successUrl)
                            .setCancelUrl(cancelUrl)
                            .setClientReferenceId(tenantId)
                            .putMetadata("tenant_id", tenantId)
                            .setSubscriptionData(
                                    SubscriptionData.builder().putMetadata("tenant_id", tenantId).build())
                            .build();

            Session session = Session.create(params, RequestOptions.getDefault());
            if (session.getUrl() == null || session.getUrl().isBlank()) {
                log.error("Stripe checkout session sem url (tenant_id={})", tenantId);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(new BillingSessionApiDtos.ErrorResponse("stripe_resposta_inválida"));
            }
            return ResponseEntity.ok(new BillingSessionApiDtos.SessionUrlResponse(session.getUrl()));
        } catch (StripeException e) {
            log.warn("Falha Stripe checkout tenant_id={}: {}", tenantId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new BillingSessionApiDtos.ErrorResponse("stripe_indisponível"));
        }
    }

    @PostMapping(path = "/portal-session", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createPortalSession(
            @RequestBody BillingSessionApiDtos.PortalSessionRequest body, Authentication authentication) {
        ResponseEntity<?> configured = requireStripeConfigured();
        if (configured != null) {
            return configured;
        }
        PortalAuthenticationToken portal = requirePortalUser(authentication);
        if (portal == null) {
            return billingForbidden();
        }

        if (body == null || body.tenantId() == null || body.tenantId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new BillingSessionApiDtos.ErrorResponse("tenantId é obrigatório"));
        }

        String tenantId = body.tenantId().strip();
        if (!tenantId.equals(portal.getTenantId())) {
            return billingForbidden();
        }

        String firebaseUid = portal.getFirebaseUid();
        String stripeCustomerId =
                customerAccess.findStripeCustomerIdForBillingActor(tenantId, firebaseUid).orElse(null);
        if (stripeCustomerId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BillingSessionApiDtos.ErrorResponse("cliente_stripe_não_configurado"));
        }

        String returnUrl = stripeProperties.portalReturnUrl();
        if (returnUrl == null || returnUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new BillingSessionApiDtos.ErrorResponse("portal_return_url_não_configurada"));
        }

        try {
            SessionCreateParams params =
                    SessionCreateParams.builder().setCustomer(stripeCustomerId).setReturnUrl(returnUrl).build();

            com.stripe.model.billingportal.Session portalSession =
                    com.stripe.model.billingportal.Session.create(params, RequestOptions.getDefault());
            if (portalSession.getUrl() == null || portalSession.getUrl().isBlank()) {
                log.error("Stripe billing portal session sem url (tenant_id={})", tenantId);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(new BillingSessionApiDtos.ErrorResponse("stripe_resposta_inválida"));
            }
            return ResponseEntity.ok(new BillingSessionApiDtos.SessionUrlResponse(portalSession.getUrl()));
        } catch (StripeException e) {
            log.warn("Falha Stripe portal tenant_id={}: {}", tenantId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new BillingSessionApiDtos.ErrorResponse("stripe_indisponível"));
        }
    }

    private PortalAuthenticationToken requirePortalUser(Authentication authentication) {
        if (!(authentication instanceof PortalAuthenticationToken token)) {
            return null;
        }
        return token;
    }

    private static ResponseEntity<?> billingForbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new BillingSessionApiDtos.ErrorResponse("sem_permissão_cobrança"));
    }

    private ResponseEntity<?> requireStripeConfigured() {
        String secret = stripeProperties.secretKey();
        if (secret == null || secret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new BillingSessionApiDtos.ErrorResponse("stripe_não_configurado"));
        }
        return null;
    }
}
