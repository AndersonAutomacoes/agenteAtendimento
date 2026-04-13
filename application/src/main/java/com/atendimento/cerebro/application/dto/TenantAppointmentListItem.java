package com.atendimento.cerebro.application.dto;

import java.time.Instant;

/**
 * Linha de listagem de agendamentos (API dashboard).
 *
 * @param status UPCOMING (futuro), TODAY (ainda hoje no fuso da app), COMPLETED (passado)
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
        AppointmentStatus status) {

    public enum AppointmentStatus {
        TODAY,
        UPCOMING,
        COMPLETED
    }
}
