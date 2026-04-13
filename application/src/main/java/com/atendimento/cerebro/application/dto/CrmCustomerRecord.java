package com.atendimento.cerebro.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Cliente CRM por tenant e {@code conversation_id} (ex.: {@code wa-...} ou sessão portal).
 */
public record CrmCustomerRecord(
        UUID id,
        String tenantId,
        String conversationId,
        String phoneNumber,
        String fullName,
        String email,
        Instant firstInteraction,
        int totalAppointments,
        String internalNotes,
        /** Espelho legado da coluna {@code last_intent} (mantido em sync com deteção). */
        String lastIntent,
        /** Intenção detetada pela IA (ex.: Orçamento, Agendamento). */
        String lastDetectedIntent,
        int leadScore,
        boolean isConverted,
        /** NONE, OPEN, PENDING_LEAD, HOT_LEAD, ASSIGNED, CONVERTED, DISMISSED */
        String intentStatus,
        Instant lastIntentAt) {}
