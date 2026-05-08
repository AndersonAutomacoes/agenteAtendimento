package com.atendimento.cerebro.infrastructure.billing.webhook;

import com.atendimento.cerebro.application.port.out.StripeWebhookInboxPersistencePort;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcStripeWebhookInboxPersistence implements StripeWebhookInboxPersistencePort {

    private final JdbcClient jdbcClient;

    public JdbcStripeWebhookInboxPersistence(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public boolean tryAcquire(String stripeEventId, String eventType) {
        Optional<String> inserted =
                jdbcClient
                        .sql(
                                """
                                INSERT INTO stripe_webhook_event (event_id, event_type, received_at)
                                VALUES (?, ?, NOW())
                                ON CONFLICT (event_id) DO NOTHING
                                RETURNING event_id
                                """)
                        .param(stripeEventId)
                        .param(eventType)
                        .query(String.class)
                        .optional();
        return inserted.isPresent();
    }

    @Override
    @Transactional
    public void markDone(String stripeEventId) {
        jdbcClient
                .sql(
                        """
                        UPDATE stripe_webhook_event SET processed_at = NOW(), processing_error = NULL
                        WHERE event_id = ?
                        """)
                .param(stripeEventId)
                .update();
    }

    @Override
    @Transactional
    public void discardForRetry(String stripeEventId) {
        jdbcClient.sql("DELETE FROM stripe_webhook_event WHERE event_id = ?").param(stripeEventId).update();
    }
}
