package com.atendimento.cerebro.application.dto;

/** Uma interação recente (mensagem USER) para a tabela do dashboard. */
public record DashboardRecentInteraction(
        long messageId,
        String phoneNumber,
        String contactDisplayName,
        String contactProfilePicUrl,
        String detectedIntent,
        String timestamp,
        String content) {}
