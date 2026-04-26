package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TenantSettingsHttpRequest(
        String tenantId,
        String systemPrompt,
        String whatsappProviderType,
        String whatsappApiKey,
        String whatsappInstanceId,
        String whatsappBaseUrl,
        String googleCalendarId,
        String establishmentName,
        String businessAddress,
        String openingHours,
        String businessContacts,
        String businessFacilities,
        Integer defaultAppointmentMinutes,
        Boolean billingCompliant,
        String calendarAccessNotes,
        String spreadsheetUrl,
        String whatsappBusinessNumber) {}
