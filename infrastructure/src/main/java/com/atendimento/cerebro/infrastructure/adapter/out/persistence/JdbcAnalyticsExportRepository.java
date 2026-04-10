package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.reports.AnalyticsExportDetailRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcAnalyticsExportRepository {

    private final JdbcClient jdbcClient;

    public JdbcAnalyticsExportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsExportDetailRow> listIntentRowsInRange(
            TenantId tenantId, Instant startInclusive, Instant endExclusive) {
        return jdbcClient
                .sql(
                        """
                        SELECT ai.classified_at, ai.phone_number, ai.primary_intent, ai.conversation_sentiment,
                          (SELECT m.content FROM chat_message m
                           WHERE m.tenant_id = ai.tenant_id AND m.phone_number = ai.phone_number
                             AND m.role = 'USER'
                           ORDER BY m.occurred_at ASC, m.id ASC LIMIT 1) AS first_msg
                        FROM analytics_intents ai
                        WHERE ai.tenant_id = ? AND ai.classified_at >= ? AND ai.classified_at < ?
                        ORDER BY ai.classified_at ASC, ai.id ASC
                        """)
                .param(tenantId.value())
                .param(Timestamp.from(startInclusive))
                .param(Timestamp.from(endExclusive))
                .query(this::mapRow)
                .list();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsExportDetailRow> listLatestDistinctConversations(
            TenantId tenantId, Instant startInclusive, Instant endExclusive, int limit) {
        return jdbcClient
                .sql(
                        """
                        SELECT classified_at, phone_number, primary_intent, conversation_sentiment, first_msg FROM (
                          SELECT DISTINCT ON (ai.tenant_id, ai.phone_number)
                            ai.classified_at, ai.phone_number, ai.primary_intent, ai.conversation_sentiment,
                            (SELECT m.content FROM chat_message m
                             WHERE m.tenant_id = ai.tenant_id AND m.phone_number = ai.phone_number
                               AND m.role = 'USER'
                             ORDER BY m.occurred_at ASC, m.id ASC LIMIT 1) AS first_msg
                          FROM analytics_intents ai
                          WHERE ai.tenant_id = ? AND ai.classified_at >= ? AND ai.classified_at < ?
                          ORDER BY ai.tenant_id, ai.phone_number, ai.classified_at DESC
                        ) t
                        ORDER BY classified_at DESC
                        LIMIT ?
                        """)
                .param(tenantId.value())
                .param(Timestamp.from(startInclusive))
                .param(Timestamp.from(endExclusive))
                .param(limit)
                .query(this::mapRow)
                .list();
    }

    private AnalyticsExportDetailRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp ts = rs.getTimestamp("classified_at");
        Instant classifiedAt = ts != null ? ts.toInstant() : Instant.EPOCH;
        return new AnalyticsExportDetailRow(
                classifiedAt,
                rs.getString("phone_number"),
                rs.getString("primary_intent"),
                rs.getString("conversation_sentiment"),
                rs.getString("first_msg"));
    }
}
