package com.atendimento.cerebro.application.event;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.LocalDate;

/**
 * Disparado após agendamento confirmado no calendário externo e persistência na base.
 *
 * @param phoneNumber apenas dígitos (ex.: envio Evolution/WhatsApp)
 */
public record AppointmentConfirmedEvent(
        TenantId tenantId,
        Long appointmentId,
        String phoneNumber,
        String clientName,
        String serviceName,
        LocalDate date,
        String timeHhMm) {}
