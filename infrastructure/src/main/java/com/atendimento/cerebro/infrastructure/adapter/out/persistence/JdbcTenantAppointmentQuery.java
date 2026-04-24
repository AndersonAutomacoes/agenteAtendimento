package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.dto.AppointmentReminderCandidate;
import com.atendimento.cerebro.application.dto.TenantAppointmentListItem;
import com.atendimento.cerebro.application.port.out.TenantAppointmentQueryPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.LocalDate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcTenantAppointmentQuery implements TenantAppointmentQueryPort {

    private final JdbcClient jdbcClient;

    public JdbcTenantAppointmentQuery(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantAppointmentListItem> list(TenantId tenantId, ListScope scope, String zoneId) {
        ZoneId z = ZoneId.of(zoneId);
        Instant now = Instant.now();
        String tid = tenantId.value();
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT id, tenant_id, conversation_id, client_name, service_name,
                               starts_at, ends_at, google_event_id, created_at, booking_status
                        FROM tenant_appointments
                        WHERE tenant_id = ? AND booking_status = 'AGENDADO'
                        """);
        List<Object> params = new ArrayList<>();
        params.add(tid);
        if (scope == ListScope.TODAY) {
            LocalDate today = LocalDate.now(z);
            ZonedDateTime start = today.atStartOfDay(z);
            ZonedDateTime end = today.plusDays(1).atStartOfDay(z);
            sql.append(" AND starts_at >= ? AND starts_at < ? ");
            params.add(Timestamp.from(start.toInstant()));
            params.add(Timestamp.from(end.toInstant()));
        } else if (scope == ListScope.FUTURE) {
            sql.append(" AND starts_at >= ? ");
            params.add(Timestamp.from(now));
        }
        sql.append(" ORDER BY starts_at DESC LIMIT 500 ");
        return jdbcClient
                .sql(sql.toString())
                .params(params.toArray())
                .query((rs, rowNum) -> mapRow(rs, now, z))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public long countStartsInRange(TenantId tenantId, Instant fromInclusive, Instant toExclusive) {
        Long n =
                jdbcClient
                        .sql(
                                """
                                SELECT COUNT(*) FROM tenant_appointments
                                WHERE tenant_id = ? AND starts_at >= ? AND starts_at < ?
                                  AND booking_status = 'AGENDADO'
                                """)
                        .param(tenantId.value())
                        .param(Timestamp.from(fromInclusive))
                        .param(Timestamp.from(toExclusive))
                        .query(Long.class)
                        .single();
        return n != null ? n : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, TenantAppointmentListItem> findEarliestUpcomingByPhoneDigits(
            TenantId tenantId, List<String> phoneDigits, String zoneId) {
        if (phoneDigits == null || phoneDigits.isEmpty()) {
            return Map.of();
        }
        ZoneId z = ZoneId.of(zoneId);
        Instant now = Instant.now();
        List<TenantAppointmentListItem> rows =
                jdbcClient
                        .sql(
                                """
                                SELECT id, tenant_id, conversation_id, client_name, service_name,
                                       starts_at, ends_at, google_event_id, created_at, booking_status
                                FROM tenant_appointments
                                WHERE tenant_id = ? AND starts_at >= ? AND booking_status = 'AGENDADO'
                                ORDER BY starts_at ASC
                                LIMIT 2000
                                """)
                        .param(tenantId.value())
                        .param(Timestamp.from(now))
                        .query((rs, rowNum) -> mapRow(rs, now, z))
                        .list();
        Map<String, TenantAppointmentListItem> best = new HashMap<>();
        for (TenantAppointmentListItem row : rows) {
            Optional<String> digits = digitsFromConversationId(row.conversationId());
            if (digits.isEmpty()) {
                continue;
            }
            String d = digits.get();
            if (!phoneDigits.contains(d)) {
                continue;
            }
            best.merge(
                    d,
                    row,
                    (a, b) -> a.startsAt().isBefore(b.startsAt()) ? a : b);
        }
        return best;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantAppointmentListItem> listByConversationId(
            TenantId tenantId, String conversationId, String zoneId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        ZoneId z = ZoneId.of(zoneId);
        Instant now = Instant.now();
        String conv = conversationId.strip();
        return jdbcClient
                .sql(
                        """
                        SELECT id, tenant_id, conversation_id, client_name, service_name,
                               starts_at, ends_at, google_event_id, created_at, booking_status
                        FROM tenant_appointments
                        WHERE tenant_id = ? AND conversation_id = ? AND booking_status = 'AGENDADO'
                        ORDER BY starts_at DESC
                        LIMIT 200
                        """)
                .param(tenantId.value())
                .param(conv)
                .query((rs, rowNum) -> mapRow(rs, now, z))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantAppointmentListItem> findMostRecentByConversationId(
            TenantId tenantId, String conversationId, String zoneId) {
        List<TenantAppointmentListItem> list = listByConversationId(tenantId, conversationId, zoneId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsOverlapping(TenantId tenantId, Instant startInclusive, Instant endExclusive) {
        if (tenantId == null || startInclusive == null || endExclusive == null || !endExclusive.isAfter(startInclusive)) {
            return false;
        }
        Long n =
                jdbcClient
                        .sql(
                                """
                                SELECT COUNT(*) FROM tenant_appointments
                                WHERE tenant_id = ?
                                  AND booking_status = 'AGENDADO'
                                  AND starts_at < ?
                                  AND ends_at > ?
                                """)
                        .param(tenantId.value())
                        .param(Timestamp.from(endExclusive))
                        .param(Timestamp.from(startInclusive))
                        .query(Long.class)
                        .single();
        return n != null && n > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantAppointmentListItem> findActiveByConversationAndLocalDate(
            TenantId tenantId, String conversationId, LocalDate day, String zoneId) {
        if (conversationId == null || conversationId.isBlank() || day == null) {
            return Optional.empty();
        }
        ZoneId z = ZoneId.of(zoneId);
        Instant now = Instant.now();
        ZonedDateTime start = day.atStartOfDay(z);
        ZonedDateTime end = day.plusDays(1).atStartOfDay(z);
        String conv = conversationId.strip();
        List<TenantAppointmentListItem> rows =
                jdbcClient
                        .sql(
                                """
                                SELECT id, tenant_id, conversation_id, client_name, service_name,
                                       starts_at, ends_at, google_event_id, created_at, booking_status
                                FROM tenant_appointments
                                WHERE tenant_id = ? AND conversation_id = ?
                                  AND booking_status = 'AGENDADO'
                                  AND starts_at >= ? AND starts_at < ?
                                ORDER BY starts_at DESC
                                LIMIT 1
                                """)
                        .param(tenantId.value())
                        .param(conv)
                        .param(Timestamp.from(start.toInstant()))
                        .param(Timestamp.from(end.toInstant()))
                        .query((rs, rowNum) -> mapRow(rs, now, z))
                        .list();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantAppointmentListItem> findCancelledByConversationAndLocalDate(
            TenantId tenantId, String conversationId, LocalDate day, String zoneId) {
        if (conversationId == null || conversationId.isBlank() || day == null) {
            return Optional.empty();
        }
        ZoneId z = ZoneId.of(zoneId);
        Instant now = Instant.now();
        ZonedDateTime start = day.atStartOfDay(z);
        ZonedDateTime end = day.plusDays(1).atStartOfDay(z);
        String conv = conversationId.strip();
        List<TenantAppointmentListItem> rows =
                jdbcClient
                        .sql(
                                """
                                SELECT id, tenant_id, conversation_id, client_name, service_name,
                                       starts_at, ends_at, google_event_id, created_at, booking_status
                                FROM tenant_appointments
                                WHERE tenant_id = ? AND conversation_id = ?
                                  AND booking_status = 'CANCELADO'
                                  AND starts_at >= ? AND starts_at < ?
                                ORDER BY starts_at DESC
                                LIMIT 1
                                """)
                        .param(tenantId.value())
                        .param(conv)
                        .param(Timestamp.from(start.toInstant()))
                        .param(Timestamp.from(end.toInstant()))
                        .query((rs, rowNum) -> mapRow(rs, now, z))
                        .list();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantAppointmentListItem> listAgendadoByConversationOrderedAscending(
            TenantId tenantId, String conversationId, String zoneId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        ZoneId z = ZoneId.of(zoneId);
        Instant now = Instant.now();
        String conv = conversationId.strip();
        return jdbcClient
                .sql(
                        """
                        SELECT id, tenant_id, conversation_id, client_name, service_name,
                               starts_at, ends_at, google_event_id, created_at, booking_status
                        FROM tenant_appointments
                        WHERE tenant_id = ? AND conversation_id = ? AND booking_status = 'AGENDADO'
                        ORDER BY starts_at ASC
                        LIMIT 200
                        """)
                .param(tenantId.value())
                .param(conv)
                .query((rs, rowNum) -> mapRow(rs, now, z))
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantAppointmentListItem> findByIdForTenantAndConversation(
            TenantId tenantId, long appointmentId, String conversationId, String zoneId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        ZoneId z = ZoneId.of(zoneId);
        Instant now = Instant.now();
        List<TenantAppointmentListItem> rows =
                jdbcClient
                        .sql(
                                """
                                SELECT id, tenant_id, conversation_id, client_name, service_name,
                                       starts_at, ends_at, google_event_id, created_at, booking_status
                                FROM tenant_appointments
                                WHERE tenant_id = ? AND id = ? AND conversation_id = ?
                                """)
                        .param(tenantId.value())
                        .param(appointmentId)
                        .param(conversationId.strip())
                        .query((rs, rowNum) -> mapRow(rs, now, z))
                        .list();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentReminderCandidate> listAgendadoForReminderOnLocalDate(LocalDate localDay, String zoneId) {
        if (localDay == null || zoneId == null || zoneId.isBlank()) {
            return List.of();
        }
        String z = zoneId.strip();
        return jdbcClient
                .sql(
                        """
                        SELECT id, tenant_id, conversation_id, client_name, starts_at
                        FROM tenant_appointments
                        WHERE booking_status = 'AGENDADO'
                          AND reminder_sent = false
                          AND (starts_at AT TIME ZONE ?)::date = ?
                        ORDER BY tenant_id, starts_at
                        LIMIT 5000
                        """)
                .param(z)
                .param(localDay)
                .query(
                        (rs, rowNum) ->
                                new AppointmentReminderCandidate(
                                        rs.getLong("id"),
                                        new TenantId(rs.getString("tenant_id")),
                                        rs.getString("conversation_id"),
                                        rs.getString("client_name"),
                                        rs.getTimestamp("starts_at").toInstant()))
                .list();
    }

    private static Optional<String> digitsFromConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        String s = conversationId.strip();
        if (s.startsWith("wa-")) {
            s = s.substring(3);
        }
        String digits = s.replaceAll("\\D", "");
        return digits.isEmpty() ? Optional.empty() : Optional.of(digits);
    }

    private static TenantAppointmentListItem mapRow(ResultSet rs, Instant now, ZoneId z) throws SQLException {
        Instant starts = rs.getTimestamp("starts_at").toInstant();
        Instant ends = rs.getTimestamp("ends_at").toInstant();
        Instant created = rs.getTimestamp("created_at").toInstant();
        LocalDate today = LocalDate.now(z);
        LocalDate startDay = starts.atZone(z).toLocalDate();
        TenantAppointmentListItem.AppointmentStatus st;
        if (startDay.equals(today)) {
            st = TenantAppointmentListItem.AppointmentStatus.TODAY;
        } else if (starts.isBefore(now)) {
            st = TenantAppointmentListItem.AppointmentStatus.COMPLETED;
        } else {
            st = TenantAppointmentListItem.AppointmentStatus.UPCOMING;
        }
        // id = PK tenant_appointments (usada em cancel_appointment / markCancelled)
        return new TenantAppointmentListItem(
                rs.getLong("id"),
                rs.getString("tenant_id"),
                rs.getString("conversation_id"),
                rs.getString("client_name"),
                rs.getString("service_name"),
                starts,
                ends,
                rs.getString("google_event_id"),
                created,
                st,
                readBookingStatus(rs));
    }

    private static TenantAppointmentListItem.BookingStatus readBookingStatus(ResultSet rs) throws SQLException {
        String raw = rs.getString("booking_status");
        if (raw != null && raw.equalsIgnoreCase("CANCELADO")) {
            return TenantAppointmentListItem.BookingStatus.CANCELADO;
        }
        return TenantAppointmentListItem.BookingStatus.AGENDADO;
    }
}
