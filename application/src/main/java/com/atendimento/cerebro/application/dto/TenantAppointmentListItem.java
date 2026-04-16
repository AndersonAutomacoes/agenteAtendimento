package com.atendimento.cerebro.application.dto;

import java.time.Instant;

/**
 * Linha de listagem de agendamentos (API dashboard).
 *
 * @param status UPCOMING (futuro), TODAY (ainda hoje no fuso da app), COMPLETED (passado)
 * @param bookingStatus AGENDADO ou CANCELADO (persistido em {@code tenant_appointments.booking_status})
 */
public record TenantAppointmentListItem(
        long id,
        String tenantId,
        String conversationId,
        String clientName,
        String serviceName,
        Instant startsAt,
        Instant endsAt,
        String googleEventId,
        Instant createdAt,
        AppointmentStatus status,
        BookingStatus bookingStatus) {

    public enum AppointmentStatus {
        TODAY,
        UPCOMING,
        COMPLETED
    }

    /** Estado de negócio do agendamento na base (cancelamento lógico). */
    public enum BookingStatus {
        AGENDADO,
        CANCELADO
    }
}
