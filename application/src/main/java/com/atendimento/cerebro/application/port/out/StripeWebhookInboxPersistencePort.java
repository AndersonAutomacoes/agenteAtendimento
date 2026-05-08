package com.atendimento.cerebro.application.port.out;

/** Idempotência de webhooks Stripe: uma única primeira entrega deve processar cada {@code event_id}. */
public interface StripeWebhookInboxPersistencePort {

    /**
     * Tenta registar este evento. {@code true} se este worker deve executar negócio; {@code false} se já existia
     * (replay).
     */
    boolean tryAcquire(String stripeEventId, String eventType);

    void markDone(String stripeEventId);

    /** Apaga o registo para permitir retry da Stripe após falha técnica (antes de {@link #markDone}). */
    void discardForRetry(String stripeEventId);
}
