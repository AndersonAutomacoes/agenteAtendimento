package com.atendimento.cerebro.application.dto;

import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;

/**
 * Atualização parcial de configuração do tenant.
 * <p>
 * Campos {@code null} (exceto {@code systemPrompt}) significam "manter valor existente na base".
 * Strings vazias ({@code ""}) para campos opcionais significam limpar o valor armazenado.
 */
public record TenantSettingsUpdateCommand(
        String systemPrompt,
        WhatsAppProviderType whatsappProviderType,
        String whatsappApiKey,
        String whatsappInstanceId,
        String whatsappBaseUrl,
        /** {@code null} = manter valor na base; string vazia = limpar. */
        String googleCalendarId,
        String establishmentName,
        String businessAddress,
        String openingHours,
        String businessContacts,
        String businessFacilities,
        /** {@code null} = manter. */
        Integer defaultAppointmentMinutes,
        /** {@code null} = manter. */
        Boolean billingCompliant,
        String calendarAccessNotes,
        String spreadsheetUrl,
        String whatsappBusinessNumber) {

    public TenantSettingsUpdateCommand {
        if (systemPrompt == null) {
            throw new IllegalArgumentException("systemPrompt must not be null");
        }
    }

    /** Atualização só de persona/integração (chamadas existentes de teste). */
    public static TenantSettingsUpdateCommand ofLegacy(
            String systemPrompt,
            WhatsAppProviderType whatsappProviderType,
            String whatsappApiKey,
            String whatsappInstanceId,
            String whatsappBaseUrl,
            String googleCalendarId) {
        return new TenantSettingsUpdateCommand(
                systemPrompt,
                whatsappProviderType,
                whatsappApiKey,
                whatsappInstanceId,
                whatsappBaseUrl,
                googleCalendarId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
