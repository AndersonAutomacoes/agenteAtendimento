package com.atendimento.cerebro.infrastructure.billing.webhook;

import com.atendimento.cerebro.application.dto.billing.StripeSubscriptionMirrorCommand;
import com.atendimento.cerebro.application.port.out.StripeWebhookInboxPersistencePort;
import com.atendimento.cerebro.application.port.out.TenantSubscriptionPersistencePort;
import com.atendimento.cerebro.application.service.billing.BillingSubscriptionSyncService;
import com.atendimento.cerebro.domain.billing.BillingPlanTier;
import com.atendimento.cerebro.infrastructure.billing.BillingStripeProperties;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Price;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.net.RequestOptions;
import com.stripe.model.checkout.Session;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StripeBillingWebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(StripeBillingWebhookProcessor.class);

    private final StripeWebhookInboxPersistencePort inbox;
    private final BillingSubscriptionSyncService syncService;
    private final TenantSubscriptionPersistencePort tenantSubscriptionPersistence;
    private final BillingStripeProperties props;

    public StripeBillingWebhookProcessor(
            StripeWebhookInboxPersistencePort inbox,
            BillingSubscriptionSyncService syncService,
            TenantSubscriptionPersistencePort tenantSubscriptionPersistence,
            BillingStripeProperties props) {
        this.inbox = inbox;
        this.syncService = syncService;
        this.tenantSubscriptionPersistence = tenantSubscriptionPersistence;
        this.props = props;
    }

    public void process(Event event) {
        if (!inbox.tryAcquire(event.getId(), event.getType())) {
            log.debug("stripe billing skip duplicate/replay event_id={}", event.getId());
            return;
        }
        try {
            dispatch(event);
            inbox.markDone(event.getId());
        } catch (IllegalArgumentException e) {
            log.warn("stripe billing webhook event_id={}: {}", event.getId(), e.getMessage());
            inbox.markDone(event.getId());
        } catch (StripeException e) {
            log.error("Stripe API failure event_id={}", event.getId(), e);
            inbox.discardForRetry(event.getId());
            throw new IllegalStateException("Stripe API failure: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("stripe billing webhook failed event_id={}", event.getId(), e);
            inbox.discardForRetry(event.getId());
            throw e;
        }
    }

    private void dispatch(Event event) throws StripeException {
        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(event);
            case "invoice.paid", "invoice.payment_failed" -> handleInvoiceSubscriptionEvent(event);
            case "customer.subscription.created",
                    "customer.subscription.updated",
                    "customer.subscription.deleted" -> handleSubscriptionEvent(event);
            default -> log.debug("stripe billing ignoring event type {}", event.getType());
        }
    }

    private void handleCheckoutSessionCompleted(Event event) throws StripeException {
        Session session = unwrap(event, Session.class);
        Optional<String> subId = subscriptionId(session.getSubscription());
        if (subId.isEmpty()) {
            log.debug("checkout.session.completed without subscription (event_id={})", event.getId());
            return;
        }
        syncFromSubscriptionId(subId.get());
    }

    private void handleInvoiceSubscriptionEvent(Event event) throws StripeException {
        Invoice invoice = unwrap(event, Invoice.class);
        Optional<String> subId = subscriptionId(invoice.getSubscription());
        if (subId.isEmpty()) {
            log.debug("invoice event without subscription (event_id={} type={})", event.getId(), event.getType());
            return;
        }
        syncFromSubscriptionId(subId.get());
    }

    private void handleSubscriptionEvent(Event event) throws StripeException {
        Subscription partial = unwrap(event, Subscription.class);
        syncFromSubscriptionId(partial.getId());
    }

    private void syncFromSubscriptionId(String subscriptionId) throws StripeException {
        Subscription sub = loadSubscriptionWithItems(subscriptionId);
        syncFromSubscription(sub);
    }

    private static Subscription loadSubscriptionWithItems(String subscriptionId) throws StripeException {
        Map<String, Object> params = new HashMap<>();
        params.put("expand", List.of("items.data.price"));
        return Subscription.retrieve(subscriptionId, params, RequestOptions.getDefault());
    }

    private void syncFromSubscription(Subscription sub) {
        String tenantId = requireTenantId(sub);
        Subscription full = needsItemExpand(sub) ? loadSubscriptionSafely(sub.getId()) : sub;

        String priceId = firstPriceId(full);
        Price price = firstPrice(full);
        if (price == null || price.getRecurring() == null) {
            full = loadSubscriptionSafely(full.getId());
            priceId = firstPriceId(full);
            price = firstPrice(full);
        }
        if (priceId == null || price == null || price.getRecurring() == null) {
            throw new IllegalArgumentException("subscription has no recurring price items");
        }

        BillingPlanTier tier = tierForStripePrice(priceId);
        String billingInterval =
                billingIntervalFromStripe(price.getRecurring().getInterval());

        String customerId = customerId(full);
        String status = Optional.ofNullable(full.getStatus()).orElse("unknown");
        Instant start = unixOrEpoch(full.getCurrentPeriodStart());
        Instant end = unixOrEpoch(full.getCurrentPeriodEnd());
        Boolean cancelRaw = full.getCancelAtPeriodEnd();
        boolean cancelAtPeriodEnd = Boolean.TRUE.equals(cancelRaw);

        Instant pastDueSince = computePastDueSince(tenantId, status);

        StripeSubscriptionMirrorCommand cmd =
                new StripeSubscriptionMirrorCommand(
                        tenantId,
                        full.getId(),
                        customerId,
                        status,
                        tier,
                        priceId,
                        billingInterval,
                        start,
                        end,
                        cancelAtPeriodEnd,
                        pastDueSince);

        syncService.syncFromStripe(cmd);
    }

    private Subscription loadSubscriptionSafely(String id) {
        try {
            return loadSubscriptionWithItems(id);
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe subscription retrieve failed: " + id, e);
        }
    }

    private static boolean needsItemExpand(Subscription sub) {
        List<SubscriptionItem> data = subscriptionItemsData(sub);
        if (data.isEmpty()) {
            return true;
        }
        Price p = data.get(0).getPrice();
        return p == null || p.getRecurring() == null;
    }

    private static List<SubscriptionItem> subscriptionItemsData(Subscription sub) {
        if (sub.getItems() == null || sub.getItems().getData() == null) {
            return List.of();
        }
        return sub.getItems().getData();
    }

    private static String firstPriceId(Subscription sub) {
        List<SubscriptionItem> data = subscriptionItemsData(sub);
        if (data.isEmpty()) {
            return null;
        }
        Price p = data.get(0).getPrice();
        return p != null ? p.getId() : null;
    }

    private static Price firstPrice(Subscription sub) {
        List<SubscriptionItem> data = subscriptionItemsData(sub);
        if (data.isEmpty()) {
            return null;
        }
        return data.get(0).getPrice();
    }

    private Instant computePastDueSince(String tenantId, String status) {
        if (!"past_due".equalsIgnoreCase(status)) {
            return null;
        }
        return tenantSubscriptionPersistence
                .findByTenantId(tenantId)
                .filter(
                        snapshot ->
                                "past_due".equalsIgnoreCase(snapshot.stripeStatus())
                                        && snapshot.pastDueSince() != null)
                .map(snapshot -> snapshot.pastDueSince())
                .orElse(Instant.now());
    }

    private BillingPlanTier tierForStripePrice(String priceId) {
        String tierKey = props.priceTierNonNull().get(priceId);
        if (tierKey == null || tierKey.isBlank()) {
            throw new IllegalArgumentException("unknown Stripe price id (configure stripe.price-tier): " + priceId);
        }
        return BillingPlanTier.valueOf(tierKey.trim().toUpperCase(Locale.ROOT));
    }

    private static String billingIntervalFromStripe(String stripeInterval) {
        if (stripeInterval == null) {
            throw new IllegalArgumentException("missing recurring.interval on price");
        }
        String u = stripeInterval.trim().toLowerCase(Locale.ROOT);
        if ("month".equals(u)) {
            return "MONTH";
        }
        if ("year".equals(u)) {
            return "YEAR";
        }
        throw new IllegalArgumentException(
                "unsupported recurring interval (expected month|year): " + stripeInterval);
    }

    private static String customerId(Subscription sub) {
        Object c = sub.getCustomer();
        if (c instanceof String s && !s.isBlank()) {
            return s;
        }
        if (c instanceof Customer cust) {
            return cust.getId();
        }
        throw new IllegalArgumentException("subscription customer id not available");
    }

    private static Optional<String> subscriptionId(Object subscriptionField) {
        if (subscriptionField == null) {
            return Optional.empty();
        }
        if (subscriptionField instanceof String s) {
            return s.isBlank() ? Optional.empty() : Optional.of(s);
        }
        if (subscriptionField instanceof Subscription s) {
            return Optional.ofNullable(s.getId());
        }
        return Optional.empty();
    }

    private static Instant unixOrEpoch(Long epochSeconds) {
        if (epochSeconds == null) {
            return Instant.EPOCH;
        }
        return Instant.ofEpochSecond(epochSeconds);
    }

    private static String requireTenantId(Subscription sub) {
        Map<String, String> meta = sub.getMetadata();
        if (meta == null) {
            throw new IllegalArgumentException("subscription missing metadata tenant_id");
        }
        String t = meta.get("tenant_id");
        if (t == null || t.isBlank()) {
            t = meta.get("tenantId");
        }
        if (t == null || t.isBlank()) {
            throw new IllegalArgumentException("subscription metadata must include tenant_id");
        }
        return t.trim();
    }

    private static <T extends StripeObject> T unwrap(Event event, Class<T> type) {
        StripeObject raw = deserializePayloadObject(event);
        if (raw == null) {
            throw new IllegalArgumentException("event payload object missing");
        }
        if (!type.isInstance(raw)) {
            throw new IllegalArgumentException(
                    "unexpected stripe object type: want " + type.getSimpleName() + " got " + raw.getClass().getName());
        }
        return type.cast(raw);
    }

    private static StripeObject deserializePayloadObject(Event event) {
        var des = event.getDataObjectDeserializer();
        Optional<StripeObject> o = des.getObject();
        if (o.isPresent()) {
            return o.get();
        }
        try {
            return des.deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            return null;
        }
    }
}
