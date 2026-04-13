package com.atendimento.cerebro.application.dto;

import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;

/**
 * Atualização parcial de configuração do tenant.
 * <p>
 * Campos WhatsApp {@code null} significam "manter valor existente na base".
 * Strings vazias ({@code ""}) para campos opcionais significam limpar o valor armazenado.
 */
public record TenantSettingsUpdateCommand(
        String systemPrompt,
        WhatsAppProviderType whatsappProviderType,
        String whatsappApiKey,
        String whatsappInstanceId,
        String whatsappBaseUrl,
        /** {@code null} = manter valor na base; string vazia = limpar. */
        String googleCalendarId) {

    public TenantSettingsUpdateCommand {
        if (systemPrompt == null) {
            throw new IllegalArgumentException("systemPrompt must not be null");
        }
    }
}
