package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.analytics.AnalyticsIntentTrigger;
import com.atendimento.cerebro.application.analytics.ConversationSentiment;
import com.atendimento.cerebro.application.analytics.PrimaryIntentCategory;
import com.atendimento.cerebro.application.analytics.PrimaryIntentCount;
import com.atendimento.cerebro.application.analytics.SentimentCount;
import com.atendimento.cerebro.application.port.out.AnalyticsIntentsRepository;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcAnalyticsIntentsRepository implements AnalyticsIntentsRepository {

    private final JdbcClient jdbcClient;

    public JdbcAnalyticsIntentsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsMessageThresholdClassification(TenantId tenantId, String phoneNumber, int turnCount) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return false;
        }
        Long n =
                jdbcClient
                        .sql(
                                """
                                SELECT COUNT(*)::bigint FROM analytics_intents
                                WHERE tenant_id = ? AND phone_number = ? AND turn_count = ?
                                  AND trigger_type = 'MESSAGE_THRESHOLD'
                                """)
                        .param(tenantId.value())
                        .param(phoneNumber.strip())
                        .param(turnCount)
                        .query(Long.class)
                        .single();
        return n != null && n > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsInactivityClassification(
            TenantId tenantId, String phoneNumber, Instant conversationEndAt) {
        if (phoneNumber == null || phoneNumber.isBlank() || conversationEndAt == null) {
            return false;
        }
        Long n =
                jdbcClient
                        .sql(
                                """
                                SELECT COUNT(*)::bigint FROM analytics_intents
                                WHERE tenant_id = ? AND phone_number = ?
                                  AND trigger_type = 'INACTIVITY_CLOSE'
                                  AND conversation_end_at = ?
                                """)
                        .param(tenantId.value())
                        .param(phoneNumber.strip())
                        .param(Timestamp.from(conversationEndAt))
                        .query(Long.class)
                        .single();
        return n != null && n > 0;
    }

    @Override
    @Transactional
    public void insert(
            TenantId tenantId,
            String phoneNumber,
            PrimaryIntentCategory category,
            ConversationSentiment sentiment,
            AnalyticsIntentTrigger trigger,
            int turnCount,
            Instant conversationEndAt,
            String modelLabel) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO analytics_intents
                            (tenant_id, phone_number, primary_intent, conversation_sentiment, trigger_type, turn_count, conversation_end_at, model_label)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .param(tenantId.value())
                .param(phoneNumber.strip())
                .param(category.name())
                .param(sentiment.name())
                .param(trigger.name())
                .param(turnCount)
                .param(conversationEndAt != null ? Timestamp.from(conversationEndAt) : null)
                .param(modelLabel)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PrimaryIntentCount> countByCategoryInRange(
            TenantId tenantId, Instant startInclusive, Instant endExclusive) {
        var rows =
                jdbcClient
                        .sql(
                                """
                                SELECT primary_intent, COUNT(*)::bigint AS c
                                FROM analytics_intents
                                WHERE tenant_id = ?
                                  AND classified_at >= ?
                                  AND classified_at < ?
                                GROUP BY primary_intent
                                ORDER BY primary_intent
                                """)
                        .param(tenantId.value())
                        .param(Timestamp.from(startInclusive))
                        .param(Timestamp.from(endExclusive))
                        .query(
                                (rs, rowNum) ->
                                        new PrimaryIntentCountRow(rs.getString("primary_intent"), rs.getLong("c")))
                        .list();
        List<PrimaryIntentCount> out = new ArrayList<>();
        for (PrimaryIntentCountRow r : rows) {
            out.add(new PrimaryIntentCount(parseCategory(r.intentName()), r.cnt()));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SentimentCount> countBySentimentInRange(
            TenantId tenantId, Instant startInclusive, Instant endExclusive) {
        var rows =
                jdbcClient
                        .sql(
                                """
                                SELECT conversation_sentiment, COUNT(*)::bigint AS c
                                FROM analytics_intents
                                WHERE tenant_id = ?
                                  AND classified_at >= ?
                                  AND classified_at < ?
                                  AND conversation_sentiment IS NOT NULL
                                GROUP BY conversation_sentiment
                                ORDER BY conversation_sentiment
                                """)
                        .param(tenantId.value())
                        .param(Timestamp.from(startInclusive))
                        .param(Timestamp.from(endExclusive))
                        .query(
                                (rs, rowNum) ->
                                        new SentimentCountRow(rs.getString("conversation_sentiment"), rs.getLong("c")))
                        .list();
        List<SentimentCount> out = new ArrayList<>();
        for (SentimentCountRow r : rows) {
            out.add(new SentimentCount(parseSentiment(r.sentimentName()), r.cnt()));
        }
        return out;
    }

    private static PrimaryIntentCategory parseCategory(String name) {
        if (name == null || name.isBlank()) {
            return PrimaryIntentCategory.OUTRO;
        }
        try {
            return PrimaryIntentCategory.valueOf(name.strip());
        } catch (IllegalArgumentException e) {
            return PrimaryIntentCategory.OUTRO;
        }
    }

    private static ConversationSentiment parseSentiment(String name) {
        if (name == null || name.isBlank()) {
            return ConversationSentiment.NEUTRO;
        }
        try {
            return ConversationSentiment.valueOf(name.strip());
        } catch (IllegalArgumentException e) {
            return ConversationSentiment.NEUTRO;
        }
    }

    private record PrimaryIntentCountRow(String intentName, long cnt) {}

    private record SentimentCountRow(String sentimentName, long cnt) {}
}
