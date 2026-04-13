package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Corpo JSON de {@code GET /api/v1/tenant/settings} (valores atuais para o dashboard).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantSettingsResponse(
        String tenantId,
        String profileLevel,
        String systemPrompt,
        String whatsappProviderType,
        String whatsappApiKey,
        String whatsappInstanceId,
        String whatsappBaseUrl,
        String googleCalendarId) {}
