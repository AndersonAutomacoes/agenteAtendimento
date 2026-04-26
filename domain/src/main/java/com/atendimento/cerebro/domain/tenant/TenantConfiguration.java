package com.atendimento.cerebro.domain.tenant;

/**
 * Configuração por tenant (persona, integração WhatsApp, perfil de negócio, calendário).
 */
public record TenantConfiguration(
        TenantId tenantId,
        String systemPrompt,
        WhatsAppProviderType whatsappProviderType,
        String whatsappApiKey,
        String whatsappInstanceId,
        String whatsappBaseUrl,
        ProfileLevel profileLevel,
        String portalPasswordHash,
        /** ID do calendário Google (ex.: e-mail ou {@code xxx@group.calendar.google.com}); partilhado com a SA global. */
        String googleCalendarId,
        String establishmentName,
        String businessAddress,
        String openingHours,
        String businessContacts,
        String businessFacilities,
        int defaultAppointmentMinutes,
        boolean billingCompliant,
        String calendarAccessNotes,
        String spreadsheetUrl,
        String whatsappBusinessNumber) {

    public TenantConfiguration {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (systemPrompt == null) {
            throw new IllegalArgumentException("systemPrompt must not be null");
        }
        if (whatsappProviderType == null) {
            throw new IllegalArgumentException("whatsappProviderType must not be null");
        }
        if (profileLevel == null) {
            throw new IllegalArgumentException("profileLevel must not be null");
        }
        if (defaultAppointmentMinutes <= 0) {
            throw new IllegalArgumentException("defaultAppointmentMinutes must be positive");
        }
    }

    /**
     * Configuração inicial quando ainda não existe linha em {@code tenant_configuration}.
     */
    public static TenantConfiguration defaults(TenantId tenantId) {
        return new TenantConfiguration(
                tenantId,
                "",
                WhatsAppProviderType.SIMULATED,
                null,
                null,
                null,
                ProfileLevel.BASIC,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                30,
                true,
                null,
                null,
                null);
    }

    public TenantConfiguration withSystemPrompt(String newSystemPrompt) {
        if (newSystemPrompt == null) {
            throw new IllegalArgumentException("systemPrompt must not be null");
        }
        return new TenantConfiguration(
                tenantId,
                newSystemPrompt,
                whatsappProviderType,
                whatsappApiKey,
                whatsappInstanceId,
                whatsappBaseUrl,
                profileLevel,
                portalPasswordHash,
                googleCalendarId,
                establishmentName,
                businessAddress,
                openingHours,
                businessContacts,
                businessFacilities,
                defaultAppointmentMinutes,
                billingCompliant,
                calendarAccessNotes,
                spreadsheetUrl,
                whatsappBusinessNumber);
    }
}
