package com.atendimento.cerebro.application.dto;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;

/** Linha elegível para lembrete na véspera ({@code booking_status = AGENDADO}, {@code reminder_sent = false}). */
public record AppointmentReminderCandidate(
        long id, TenantId tenantId, String conversationId, String clientName, Instant startsAt) {}
