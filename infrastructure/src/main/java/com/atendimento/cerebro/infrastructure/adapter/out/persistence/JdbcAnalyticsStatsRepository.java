package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.analytics.ConversationAnalyticsCategory;
import com.atendimento.cerebro.application.port.out.AnalyticsStatsRepository;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcAnalyticsStatsRepository implements AnalyticsStatsRepository {

    private final JdbcClient jdbcClient;

    public JdbcAnalyticsStatsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsForBucketAndPhone(TenantId tenantId, Instant bucketHourUtc, String phoneNumber) {
        Integer one =
                jdbcClient
                        .sql(
                                """
                                SELECT 1
                                FROM analytics_stats
                                WHERE tenant_id = ? AND bucket_hour = ? AND phone_number = ?
                                LIMIT 1
                                """)
                        .param(tenantId.value())
                        .param(Timestamp.from(bucketHourUtc))
                        .param(phoneNumber.strip())
                        .query(Integer.class)
                        .optional()
                        .orElse(null);
        return one != null;
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(
            TenantId tenantId,
            Instant bucketHourUtc,
            String phoneNumber,
            ConversationAnalyticsCategory category,
            String modelLabel) {
        int n =
                jdbcClient
                        .sql(
                                """
                                INSERT INTO analytics_stats
                                    (tenant_id, bucket_hour, phone_number, category, model_label)
                                VALUES (?, ?, ?, ?, ?)
                                ON CONFLICT (tenant_id, bucket_hour, phone_number) DO NOTHING
                                """)
                        .param(tenantId.value())
                        .param(Timestamp.from(bucketHourUtc))
                        .param(phoneNumber.strip())
                        .param(category.name())
                        .param(modelLabel)
                        .update();
        return n > 0;
    }
}
