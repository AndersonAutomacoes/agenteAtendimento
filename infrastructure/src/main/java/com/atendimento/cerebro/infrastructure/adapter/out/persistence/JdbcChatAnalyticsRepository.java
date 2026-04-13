package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.analytics.ChatAnalyticsAggregates;
import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import com.atendimento.cerebro.application.analytics.ChatSentiment;
import com.atendimento.cerebro.application.dto.ChatAnalyticsLeadSnapshot;
import com.atendimento.cerebro.application.port.out.ChatAnalyticsRepository;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcChatAnalyticsRepository implements ChatAnalyticsRepository {

    private final JdbcClient jdbcClient;

    public JdbcChatAnalyticsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void upsert(TenantId tenantId, String phoneNumber, ChatMainIntent mainIntent, ChatSentiment sentiment) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO chat_analytics (tenant_id, phone_number, main_intent, sentiment, last_updated, first_analytics_at)
                        VALUES (?, ?, ?, ?, NOW(), NOW())
                        ON CONFLICT (tenant_id, phone_number)
                        DO UPDATE SET
                            main_intent = EXCLUDED.main_intent,
                            sentiment = EXCLUDED.sentiment,
                            last_updated = NOW(),
                            first_analytics_at = chat_analytics.first_analytics_at
                        """)
                .param(tenantId.value())
                .param(phoneNumber.strip())
                .param(mainIntent.dbValue())
                .param(sentiment.dbValue())
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> getFirstAnalyticsAt(TenantId tenantId, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient
                .sql(
                        """
                        SELECT first_analytics_at FROM chat_analytics
                        WHERE tenant_id = ? AND phone_number = ?
                        """)
                .param(tenantId.value())
                .param(phoneNumber.strip())
                .query(
                        (rs, rowNum) -> {
                            Timestamp t = rs.getTimestamp("first_analytics_at");
                            return t != null ? t.toInstant() : null;
                        })
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatAnalyticsLeadSnapshot> findLeadSnapshot(TenantId tenantId, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient
                .sql(
                        """
                        SELECT main_intent, first_analytics_at, last_updated
                        FROM chat_analytics
                        WHERE tenant_id = ? AND phone_number = ?
                        """)
                .param(tenantId.value())
                .param(phoneNumber.strip())
                .query(
                        (rs, rowNum) -> {
                            ChatMainIntent intent = ChatMainIntent.fromDbValue(rs.getString("main_intent"));
                            Timestamp first = rs.getTimestamp("first_analytics_at");
                            Timestamp last = rs.getTimestamp("last_updated");
                            Instant anchor =
                                    first != null
                                            ? first.toInstant()
                                            : (last != null ? last.toInstant() : Instant.EPOCH);
                            return new ChatAnalyticsLeadSnapshot(intent, anchor);
                        })
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatAnalyticsAggregates aggregateForTenant(TenantId tenantId) {
        EnumMap<ChatMainIntent, Long> intents = new EnumMap<>(ChatMainIntent.class);
        for (ChatMainIntent v : ChatMainIntent.values()) {
            intents.put(v, 0L);
        }
        EnumMap<ChatSentiment, Long> sentiments = new EnumMap<>(ChatSentiment.class);
        for (ChatSentiment v : ChatSentiment.values()) {
            sentiments.put(v, 0L);
        }
        List<CountRow> iRows =
                jdbcClient
                        .sql(
                                """
                                SELECT main_intent, COUNT(*)::bigint AS c
                                FROM chat_analytics
                                WHERE tenant_id = ?
                                GROUP BY main_intent
                                """)
                        .param(tenantId.value())
                        .query((rs, rowNum) -> new CountRow(rs.getString("main_intent"), rs.getLong("c")))
                        .list();
        for (CountRow r : iRows) {
            intents.merge(ChatMainIntent.fromDbValue(r.label()), r.cnt(), Long::sum);
        }
        List<CountRow> sRows =
                jdbcClient
                        .sql(
                                """
                                SELECT sentiment, COUNT(*)::bigint AS c
                                FROM chat_analytics
                                WHERE tenant_id = ?
                                GROUP BY sentiment
                                """)
                        .param(tenantId.value())
                        .query((rs, rowNum) -> new CountRow(rs.getString("sentiment"), rs.getLong("c")))
                        .list();
        for (CountRow r : sRows) {
            sentiments.merge(ChatSentiment.fromDbValue(r.label()), r.cnt(), Long::sum);
        }
        return new ChatAnalyticsAggregates(Map.copyOf(intents), Map.copyOf(sentiments));
    }

    @Override
    @Transactional(readOnly = true)
    public ChatAnalyticsAggregates aggregateForTenant(TenantId tenantId, Instant start, Instant end) {
        EnumMap<ChatMainIntent, Long> intents = new EnumMap<>(ChatMainIntent.class);
        for (ChatMainIntent v : ChatMainIntent.values()) {
            intents.put(v, 0L);
        }
        EnumMap<ChatSentiment, Long> sentiments = new EnumMap<>(ChatSentiment.class);
        for (ChatSentiment v : ChatSentiment.values()) {
            sentiments.put(v, 0L);
        }
        List<CountRow> iRows =
                jdbcClient
                        .sql(
                                """
                                SELECT main_intent, COUNT(*)::bigint AS c
                                FROM chat_analytics
                                WHERE tenant_id = ? AND last_updated >= ? AND last_updated < ?
                                GROUP BY main_intent
                                """)
                        .param(tenantId.value())
                        .param(Timestamp.from(start))
                        .param(Timestamp.from(end))
                        .query((rs, rowNum) -> new CountRow(rs.getString("main_intent"), rs.getLong("c")))
                        .list();
        for (CountRow r : iRows) {
            intents.merge(ChatMainIntent.fromDbValue(r.label()), r.cnt(), Long::sum);
        }
        List<CountRow> sRows =
                jdbcClient
                        .sql(
                                """
                                SELECT sentiment, COUNT(*)::bigint AS c
                                FROM chat_analytics
                                WHERE tenant_id = ? AND last_updated >= ? AND last_updated < ?
                                GROUP BY sentiment
                                """)
                        .param(tenantId.value())
                        .param(Timestamp.from(start))
                        .param(Timestamp.from(end))
                        .query((rs, rowNum) -> new CountRow(rs.getString("sentiment"), rs.getLong("c")))
                        .list();
        for (CountRow r : sRows) {
            sentiments.merge(ChatSentiment.fromDbValue(r.label()), r.cnt(), Long::sum);
        }
        return new ChatAnalyticsAggregates(Map.copyOf(intents), Map.copyOf(sentiments));
    }

    private record CountRow(String label, long cnt) {}
}
