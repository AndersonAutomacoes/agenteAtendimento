package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.dto.DashboardRange;
import com.atendimento.cerebro.application.dto.DashboardRecentInteraction;
import com.atendimento.cerebro.application.dto.DashboardSeriesPoint;
import com.atendimento.cerebro.application.dto.DashboardSummary;
import com.atendimento.cerebro.application.port.out.DashboardSummaryPort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcDashboardSummaryRepository implements DashboardSummaryPort {

    private static final DateTimeFormatter ISO_INSTANT_MS = DateTimeFormatter.ISO_INSTANT;

    private final JdbcClient jdbcClient;
    private final TenantConfigurationStorePort tenantConfigurationStore;

    public JdbcDashboardSummaryRepository(
            JdbcClient jdbcClient, TenantConfigurationStorePort tenantConfigurationStore) {
        this.jdbcClient = jdbcClient;
        this.tenantConfigurationStore = tenantConfigurationStore;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummary load(TenantId tenantId, DashboardRange range) {
        String tenant = tenantId.value();
        Instant now = Instant.now();
        Window w = windowFor(range, now);

        long totalClients = countDistinctPhones(tenant, w.start(), w.endInclusive());
        long messagesToday = countUserMessagesToday(tenant, startOfTodayUtc(now), now);
        Double aiRate = computeAiRate(tenant, w.start(), w.endInclusive());
        String instanceStatus =
                deriveInstanceStatus(tenantConfigurationStore.findByTenantId(tenantId).orElse(null));
        List<DashboardSeriesPoint> series = loadSeries(tenant, range, w, now);
        List<DashboardRecentInteraction> recent = loadRecent(tenant);

        return new DashboardSummary(totalClients, messagesToday, aiRate, instanceStatus, series, recent);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummary loadForPeriod(TenantId tenantId, Instant start, Instant end) {
        String tenant = tenantId.value();
        long totalClients = countDistinctPhonesHalfOpen(tenant, start, end);
        long userMessagesInRange = countUserMessagesHalfOpen(tenant, start, end);
        Double aiRate = computeAiRateHalfOpen(tenant, start, end);
        String instanceStatus =
                deriveInstanceStatus(tenantConfigurationStore.findByTenantId(tenantId).orElse(null));
        List<DashboardSeriesPoint> series = loadSeriesForExplicitWindow(tenant, start, end);
        List<DashboardRecentInteraction> recent = loadRecent(tenant);
        return new DashboardSummary(
                totalClients, userMessagesInRange, aiRate, instanceStatus, series, recent);
    }

    private Window windowFor(DashboardRange range, Instant now) {
        return switch (range) {
            case day -> {
                Instant start = now.minus(24, ChronoUnit.HOURS);
                yield new Window(start, now);
            }
            case week -> {
                LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
                Instant start = today.minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant();
                yield new Window(start, now);
            }
            case month -> {
                LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
                Instant start = today.minusDays(29).atStartOfDay(ZoneOffset.UTC).toInstant();
                yield new Window(start, now);
            }
        };
    }

    private List<DashboardSeriesPoint> loadSeries(
            String tenant, DashboardRange range, Window w, Instant now) {
        return switch (range) {
            case day -> hourlySeries(tenant, w.start(), w.endInclusive(), now);
            case week, month -> dailySeries(tenant, w.start(), w.endInclusive(), range);
        };
    }

    private List<DashboardSeriesPoint> hourlySeries(String tenant, Instant start, Instant end, Instant now) {
        Map<Long, Long> counts = new HashMap<>();
        jdbcClient
                .sql(
                        """
                        SELECT date_trunc('hour', occurred_at) AS b, COUNT(*)::bigint AS c
                        FROM chat_message
                        WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at <= ?
                        GROUP BY b ORDER BY b
                        """)
                .param(tenant)
                .param(Timestamp.from(start))
                .param(Timestamp.from(end))
                .query(
                        (rs, rowNum) -> {
                            Timestamp t = rs.getTimestamp(1);
                            if (t != null) {
                                counts.put(t.toInstant().getEpochSecond(), rs.getLong(2));
                            }
                            return rowNum;
                        })
                .list();

        List<DashboardSeriesPoint> out = new ArrayList<>(24);
        Instant endHour = now.truncatedTo(ChronoUnit.HOURS);
        Instant first = endHour.minus(23, ChronoUnit.HOURS);
        for (int i = 0; i < 24; i++) {
            Instant bucket = first.plus(i, ChronoUnit.HOURS);
            long c = counts.getOrDefault(bucket.getEpochSecond(), 0L);
            out.add(new DashboardSeriesPoint(ISO_INSTANT_MS.format(bucket), c));
        }
        return out;
    }

    private List<DashboardSeriesPoint> dailySeries(
            String tenant, Instant start, Instant end, DashboardRange range) {
        int days = range == DashboardRange.week ? 7 : 30;
        Map<String, Long> counts = new HashMap<>();
        jdbcClient
                .sql(
                        """
                        SELECT (occurred_at AT TIME ZONE 'UTC')::date AS d, COUNT(*)::bigint AS c
                        FROM chat_message
                        WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at <= ?
                        GROUP BY d ORDER BY d
                        """)
                .param(tenant)
                .param(Timestamp.from(start))
                .param(Timestamp.from(end))
                .query(
                        (rs, rowNum) -> {
                            java.sql.Date d = rs.getDate(1);
                            if (d != null) {
                                counts.put(d.toLocalDate().toString(), rs.getLong(2));
                            }
                            return rowNum;
                        })
                .list();

        List<DashboardSeriesPoint> out = new ArrayList<>(days);
        LocalDate endDay = LocalDate.ofInstant(end, ZoneOffset.UTC);
        LocalDate firstDay = endDay.minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            LocalDate d = firstDay.plusDays(i);
            long c = counts.getOrDefault(d.toString(), 0L);
            Instant bucketStart = d.atStartOfDay(ZoneOffset.UTC).toInstant();
            out.add(new DashboardSeriesPoint(ISO_INSTANT_MS.format(bucketStart), c));
        }
        return out;
    }

    private List<DashboardSeriesPoint> loadSeriesForExplicitWindow(String tenant, Instant start, Instant end) {
        if (!end.isAfter(start)) {
            return List.of();
        }
        long hours = Duration.between(start, end).toHours();
        if (hours <= 48L) {
            return hourlySeriesHalfOpen(tenant, start, end);
        }
        return dailySeriesHalfOpen(tenant, start, end);
    }

    private List<DashboardSeriesPoint> hourlySeriesHalfOpen(String tenant, Instant start, Instant end) {
        Map<Long, Long> counts = new HashMap<>();
        jdbcClient
                .sql(
                        """
                        SELECT date_trunc('hour', occurred_at) AS b, COUNT(*)::bigint AS c
                        FROM chat_message
                        WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at < ?
                        GROUP BY b ORDER BY b
                        """)
                .param(tenant)
                .param(Timestamp.from(start))
                .param(Timestamp.from(end))
                .query(
                        (rs, rowNum) -> {
                            Timestamp t = rs.getTimestamp(1);
                            if (t != null) {
                                counts.put(t.toInstant().getEpochSecond(), rs.getLong(2));
                            }
                            return rowNum;
                        })
                .list();

        List<DashboardSeriesPoint> out = new ArrayList<>();
        Instant bucket = start.truncatedTo(ChronoUnit.HOURS);
        while (bucket.isBefore(end)) {
            long c = counts.getOrDefault(bucket.getEpochSecond(), 0L);
            out.add(new DashboardSeriesPoint(ISO_INSTANT_MS.format(bucket), c));
            bucket = bucket.plus(1, ChronoUnit.HOURS);
        }
        return out;
    }

    private List<DashboardSeriesPoint> dailySeriesHalfOpen(String tenant, Instant start, Instant end) {
        Map<String, Long> counts = new HashMap<>();
        jdbcClient
                .sql(
                        """
                        SELECT (occurred_at AT TIME ZONE 'UTC')::date AS d, COUNT(*)::bigint AS c
                        FROM chat_message
                        WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at < ?
                        GROUP BY d ORDER BY d
                        """)
                .param(tenant)
                .param(Timestamp.from(start))
                .param(Timestamp.from(end))
                .query(
                        (rs, rowNum) -> {
                            java.sql.Date d = rs.getDate(1);
                            if (d != null) {
                                counts.put(d.toLocalDate().toString(), rs.getLong(2));
                            }
                            return rowNum;
                        })
                .list();

        LocalDate first = LocalDate.ofInstant(start, ZoneOffset.UTC);
        LocalDate last = LocalDate.ofInstant(end.minusNanos(1), ZoneOffset.UTC);
        if (last.isBefore(first)) {
            last = first;
        }
        List<DashboardSeriesPoint> out = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            long c = counts.getOrDefault(d.toString(), 0L);
            Instant bucketStart = d.atStartOfDay(ZoneOffset.UTC).toInstant();
            out.add(new DashboardSeriesPoint(ISO_INSTANT_MS.format(bucketStart), c));
        }
        return out;
    }

    private long countDistinctPhonesHalfOpen(String tenant, Instant start, Instant end) {
        Long n =
                jdbcClient
                        .sql(
                                """
                                SELECT COUNT(DISTINCT phone_number)::bigint
                                FROM chat_message
                                WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at < ?
                                """)
                        .param(tenant)
                        .param(Timestamp.from(start))
                        .param(Timestamp.from(end))
                        .query(Long.class)
                        .optional()
                        .orElse(0L);
        return n;
    }

    private long countUserMessagesHalfOpen(String tenant, Instant start, Instant end) {
        Long n =
                jdbcClient
                        .sql(
                                """
                                SELECT COUNT(*)::bigint FROM chat_message
                                WHERE tenant_id = ? AND role = 'USER'
                                  AND occurred_at >= ? AND occurred_at < ?
                                """)
                        .param(tenant)
                        .param(Timestamp.from(start))
                        .param(Timestamp.from(end))
                        .query(Long.class)
                        .optional()
                        .orElse(0L);
        return n;
    }

    private Double computeAiRateHalfOpen(String tenant, Instant start, Instant end) {
        record Counts(long users, long assistants) {}

        Counts row =
                jdbcClient
                        .sql(
                                """
                                SELECT
                                  COALESCE(SUM(CASE WHEN role = 'USER' THEN 1 ELSE 0 END), 0)::bigint AS u,
                                  COALESCE(SUM(CASE WHEN role = 'ASSISTANT' AND status = 'SENT' THEN 1 ELSE 0 END), 0)::bigint AS a
                                FROM chat_message
                                WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at < ?
                                """)
                        .param(tenant)
                        .param(Timestamp.from(start))
                        .param(Timestamp.from(end))
                        .query(
                                (rs, rowNum) ->
                                        new Counts(rs.getLong("u"), rs.getLong("a")))
                        .single();

        if (row.users() == 0) {
            return null;
        }
        double p = 100.0 * row.assistants() / row.users();
        return Math.min(100.0, Math.round(p * 10.0) / 10.0);
    }

    private long countDistinctPhones(String tenant, Instant start, Instant end) {
        Long n =
                jdbcClient
                        .sql(
                                """
                                SELECT COUNT(DISTINCT phone_number)::bigint
                                FROM chat_message
                                WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at <= ?
                                """)
                        .param(tenant)
                        .param(Timestamp.from(start))
                        .param(Timestamp.from(end))
                        .query(Long.class)
                        .optional()
                        .orElse(0L);
        return n;
    }

    private long countUserMessagesToday(String tenant, Instant startDay, Instant now) {
        Long n =
                jdbcClient
                        .sql(
                                """
                                SELECT COUNT(*)::bigint FROM chat_message
                                WHERE tenant_id = ? AND role = 'USER'
                                  AND occurred_at >= ? AND occurred_at <= ?
                                """)
                        .param(tenant)
                        .param(Timestamp.from(startDay))
                        .param(Timestamp.from(now))
                        .query(Long.class)
                        .optional()
                        .orElse(0L);
        return n;
    }

    private Double computeAiRate(String tenant, Instant start, Instant end) {
        record Counts(long users, long assistants) {}

        Counts row =
                jdbcClient
                        .sql(
                                """
                                SELECT
                                  COALESCE(SUM(CASE WHEN role = 'USER' THEN 1 ELSE 0 END), 0)::bigint AS u,
                                  COALESCE(SUM(CASE WHEN role = 'ASSISTANT' AND status = 'SENT' THEN 1 ELSE 0 END), 0)::bigint AS a
                                FROM chat_message
                                WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at <= ?
                                """)
                        .param(tenant)
                        .param(Timestamp.from(start))
                        .param(Timestamp.from(end))
                        .query(
                                (rs, rowNum) ->
                                        new Counts(rs.getLong("u"), rs.getLong("a")))
                        .single();

        if (row.users() == 0) {
            return null;
        }
        double p = 100.0 * row.assistants() / row.users();
        return Math.min(100.0, Math.round(p * 10.0) / 10.0);
    }

    private List<DashboardRecentInteraction> loadRecent(String tenant) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, phone_number, contact_display_name, contact_profile_pic_url, detected_intent,
                               occurred_at, content
                        FROM chat_message
                        WHERE tenant_id = ? AND role = 'USER'
                        ORDER BY occurred_at DESC, id DESC
                        LIMIT 5
                        """)
                .param(tenant)
                .query(this::mapRecent)
                .list();
    }

    private DashboardRecentInteraction mapRecent(ResultSet rs, int rowNum) throws SQLException {
        Timestamp ts = rs.getTimestamp("occurred_at");
        String tsIso = ts != null ? ISO_INSTANT_MS.format(ts.toInstant()) : "";
        return new DashboardRecentInteraction(
                rs.getLong("id"),
                rs.getString("phone_number"),
                rs.getString("contact_display_name"),
                rs.getString("contact_profile_pic_url"),
                rs.getString("detected_intent"),
                tsIso,
                rs.getString("content"));
    }

    private static Instant startOfTodayUtc(Instant now) {
        LocalDate d = LocalDate.ofInstant(now, ZoneOffset.UTC);
        return d.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    static String deriveInstanceStatus(TenantConfiguration c) {
        if (c == null) {
            return "INCOMPLETE";
        }
        if (c.whatsappProviderType() == WhatsAppProviderType.SIMULATED) {
            return "SIMULATED";
        }
        if (c.whatsappProviderType() == WhatsAppProviderType.META) {
            return nonBlank(c.whatsappInstanceId()) && nonBlank(c.whatsappApiKey())
                    ? "CONFIGURED"
                    : "INCOMPLETE";
        }
        if (c.whatsappProviderType() == WhatsAppProviderType.EVOLUTION) {
            return nonBlank(c.whatsappBaseUrl()) && nonBlank(c.whatsappInstanceId()) && nonBlank(c.whatsappApiKey())
                    ? "CONFIGURED"
                    : "INCOMPLETE";
        }
        return "INCOMPLETE";
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }

    private record Window(Instant start, Instant endInclusive) {}
}
