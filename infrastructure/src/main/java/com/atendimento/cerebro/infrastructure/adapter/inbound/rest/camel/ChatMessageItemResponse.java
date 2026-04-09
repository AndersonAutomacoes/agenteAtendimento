package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

/**
 * DTO JSON do histórico (timestamp em ISO-8601) — evita depender do módulo Java Time no marshaller REST do Camel.
 */
public record ChatMessageItemResponse(
        long id,
        String tenantId,
        String phoneNumber,
        String contactDisplayName,
        String contactProfilePicUrl,
        String detectedIntent,
        String role,
        String content,
        String status,
        String timestamp) {}
