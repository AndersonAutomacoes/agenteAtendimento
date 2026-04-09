package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.analytics.AnalyticsSummaryResult;
import com.atendimento.cerebro.application.analytics.HourlyMessageVolumePoint;
import com.atendimento.cerebro.application.port.out.AnalyticsQueryPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcAnalyticsQueryRepository implements AnalyticsQueryPort {

    private final JdbcClient jdbcClient;

    public JdbcAnalyticsQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsSummaryResult summaryLast24Hours(TenantId tenantId) {
        Instant periodEnd = Instant.now();
        Instant periodStart = periodEnd.minus(24, ChronoUnit.HOURS);
        return querySummary(tenantId, periodStart, periodEnd);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HourlyMessageVolumePoint> hourlyMessageVolume(TenantId tenantId, int hours) {
        int safeHours = Math.min(Math.max(hours, 1), 168);
        Instant periodEnd = Instant.now();
        Instant periodStart = periodEnd.minus(safeHours, ChronoUnit.HOURS);
        Instant seriesStart = periodStart.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).toInstant();
        Instant seriesEnd = periodEnd.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).toInstant();

        return jdbcClient
                .sql(
                        """
                        SELECT ts.bucket_utc, COALESCE(agg.cnt, 0)::bigint AS cnt
                        FROM generate_series(
                            date_trunc('hour', CAST(? AS timestamptz) AT TIME ZONE 'UTC') AT TIME ZONE 'UTC',
                            date_trunc('hour', CAST(? AS timestamptz) AT TIME ZONE 'UTC') AT TIME ZONE 'UTC',
                            interval '1 hour'
                        ) AS ts(bucket_utc)
                        LEFT JOIN (
                            SELECT
                                date_trunc('hour', cm.occurred_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AS bucket_utc,
                                COUNT(*)::bigint AS cnt
                            FROM chat_message cm
                            WHERE cm.tenant_id = ?
                              AND cm.occurred_at >= ?
                              AND cm.occurred_at < ?
                            GROUP BY 1
                        ) agg ON agg.bucket_utc = ts.bucket_utc
                        ORDER BY ts.bucket_utc
                        """)
                .param(Timestamp.from(seriesStart))
                .param(Timestamp.from(seriesEnd))
                .param(tenantId.value())
                .param(Timestamp.from(periodStart))
                .param(Timestamp.from(periodEnd))
                .query(
                        (rs, rowNum) -> {
                            OffsetDateTime odt = rs.getObject("bucket_utc", OffsetDateTime.class);
                            Instant bucket =
                                    odt != null ? odt.toInstant() : Instant.EPOCH;
                            return new HourlyMessageVolumePoint(bucket, rs.getLong("cnt"));
                        })
                .list();
    }

    private AnalyticsSummaryResult querySummary(
            TenantId tenantId, Instant periodStartInclusive, Instant periodEndExclusive) {
        var row =
                jdbcClient
                        .sql(
                                """
                                SELECT
                                    COUNT(*)::bigint AS total_messages,
                                    COUNT(DISTINCT phone_number)::bigint AS unique_users,
                                    COUNT(*) FILTER (WHERE role = 'ASSISTANT' AND status = 'SENT')::bigint
                                        AS assistant_sent,
                                    COUNT(*) FILTER (WHERE role = 'ASSISTANT'
                                        AND status IN ('SENT', 'ERROR'))::bigint AS assistant_terminal
                                FROM chat_message
                                WHERE tenant_id = ?
                                  AND occurred_at >= ?
                                  AND occurred_at < ?
                                """)
                        .param(tenantId.value())
                        .param(Timestamp.from(periodStartInclusive))
                        .param(Timestamp.from(periodEndExclusive))
                        .query(
                                (rs, rowNum) -> {
                                    long total = rs.getLong("total_messages");
                                    long unique = rs.getLong("unique_users");
                                    long sent = rs.getLong("assistant_sent");
                                    long terminal = rs.getLong("assistant_terminal");
                                    Double rate = null;
                                    if (terminal > 0) {
                                        rate = 100.0 * sent / terminal;
                                    }
                                    return new AnalyticsSummaryResult(
                                            tenantId, periodStartInclusive, periodEndExclusive, total, unique, rate);
                                })
                        .single();
        return row;
    }
}
