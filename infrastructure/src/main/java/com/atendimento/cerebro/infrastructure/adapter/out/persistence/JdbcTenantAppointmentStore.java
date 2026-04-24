package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.dto.TenantAppointmentRecord;
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrita em {@code tenant_appointments}. A listagem de IDs exposta ao modelo e às ferramentas vem de
 * {@link JdbcTenantAppointmentQuery} ({@code SELECT id …} → PK).
 */
@Component
public class JdbcTenantAppointmentStore implements TenantAppointmentStorePort {

    private final JdbcClient jdbcClient;

    public JdbcTenantAppointmentStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public long insert(TenantAppointmentRecord record) {
        return jdbcClient
                .sql(
                        """
                        INSERT INTO tenant_appointments (
                            tenant_id, conversation_id, client_name, service_name,
                            starts_at, ends_at, google_event_id, booking_status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'AGENDADO')
                        RETURNING id
                        """)
                .param(record.tenantId().value())
                .param(record.conversationId())
                .param(record.clientName())
                .param(record.serviceName())
                .param(Timestamp.from(record.startsAt()))
                .param(Timestamp.from(record.endsAt()))
                .param(record.googleEventId())
                .query(Long.class)
                .single();
    }

    @Override
    @Transactional
    public boolean markCancelled(long appointmentId, Instant cancelledAt) {
        int n =
                jdbcClient
                        .sql(
                                """
                                UPDATE tenant_appointments
                                SET booking_status = 'CANCELADO', cancelled_at = ?
                                WHERE id = ? AND booking_status = 'AGENDADO'
                                """)
                        .param(Timestamp.from(cancelledAt))
                        .param(appointmentId)
                        .update();
        return n > 0;
    }

    @Override
    @Transactional
    public void markConfirmationNotificationPending(long appointmentId) {
        jdbcClient
                .sql(
                        """
                        UPDATE tenant_appointments
                        SET notified_confirmation = false, confirmation_message_id = NULL
                        WHERE id = ?
                        """)
                .param(appointmentId)
                .update();
    }

    @Override
    @Transactional
    public boolean markAsNotified(long appointmentId, String messageId) {
        int n =
                jdbcClient
                        .sql(
                                """
                                UPDATE tenant_appointments
                                SET notified_confirmation = true, confirmation_message_id = ?
                                WHERE id = ?
                                """)
                        .param(messageId)
                        .param(appointmentId)
                        .update();
        return n > 0;
    }

    @Override
    @Transactional
    public boolean markReminderSent(long appointmentId) {
        int n =
                jdbcClient
                        .sql(
                                """
                                UPDATE tenant_appointments
                                SET reminder_sent = true
                                WHERE id = ? AND booking_status = 'AGENDADO' AND reminder_sent = false
                                """)
                        .param(appointmentId)
                        .update();
        return n > 0;
    }
}
