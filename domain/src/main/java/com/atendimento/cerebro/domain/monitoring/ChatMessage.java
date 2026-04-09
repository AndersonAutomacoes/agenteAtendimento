package com.atendimento.cerebro.domain.monitoring;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;

/** Mensagem de monitorização WhatsApp; {@code id} nulo apenas antes da inserção. */
public record ChatMessage(
        Long id,
        TenantId tenantId,
        String phoneNumber,
        ChatMessageRole role,
        String content,
        ChatMessageStatus status,
        Instant timestamp,
        String contactDisplayName,
        String contactProfilePicUrl,
        String detectedIntent) {

    public ChatMessage {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phoneNumber must not be blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp is required");
        }
    }
}
