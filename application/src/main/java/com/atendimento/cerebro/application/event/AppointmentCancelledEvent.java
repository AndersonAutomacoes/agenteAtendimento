package com.atendimento.cerebro.application.event;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;

/**
 * Disparado após cancelamento persistido na base e remoção do evento no calendário externo.
 *
 * @param phoneNumber apenas dígitos (normalizar para Evolution antes do envio)
 */
public record AppointmentCancelledEvent(
        TenantId tenantId,
        Long appointmentId,
        String phoneNumber,
        String clientName,
        String serviceName,
        Instant startsAt,
        String calendarZoneId) {}
