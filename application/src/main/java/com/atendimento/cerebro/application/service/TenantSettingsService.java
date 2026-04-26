package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.dto.TenantSettingsUpdateCommand;
import com.atendimento.cerebro.application.port.in.UpdateTenantSettingsUseCase;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;

public class TenantSettingsService implements UpdateTenantSettingsUseCase {

    private final TenantConfigurationStorePort tenantConfigurationStore;

    public TenantSettingsService(TenantConfigurationStorePort tenantConfigurationStore) {
        this.tenantConfigurationStore = tenantConfigurationStore;
    }

    @Override
    public void updateTenantSettings(TenantId tenantId, TenantSettingsUpdateCommand command) {
        TenantConfiguration base =
                tenantConfigurationStore.findByTenantId(tenantId).orElseGet(() -> TenantConfiguration.defaults(tenantId));

        TenantConfiguration merged = merge(base, command);
        tenantConfigurationStore.upsert(merged);
    }

    private static TenantConfiguration merge(TenantConfiguration base, TenantSettingsUpdateCommand c) {
        var type = c.whatsappProviderType() != null ? c.whatsappProviderType() : base.whatsappProviderType();
        var apiKey = c.whatsappApiKey() != null ? nullIfBlankToNull(c.whatsappApiKey()) : base.whatsappApiKey();
        var instanceId =
                c.whatsappInstanceId() != null ? nullIfBlankToNull(c.whatsappInstanceId()) : base.whatsappInstanceId();
        var baseUrl = c.whatsappBaseUrl() != null ? nullIfBlankToNull(c.whatsappBaseUrl()) : base.whatsappBaseUrl();

        String googleCal =
                c.googleCalendarId() != null ? nullIfBlankToNull(c.googleCalendarId()) : base.googleCalendarId();

        String est = c.establishmentName() != null ? nullIfBlankToNull(c.establishmentName()) : base.establishmentName();
        String addr = c.businessAddress() != null ? nullIfBlankToNull(c.businessAddress()) : base.businessAddress();
        String hours = c.openingHours() != null ? nullIfBlankToNull(c.openingHours()) : base.openingHours();
        String contacts = c.businessContacts() != null ? nullIfBlankToNull(c.businessContacts()) : base.businessContacts();
        String facilities =
                c.businessFacilities() != null ? nullIfBlankToNull(c.businessFacilities()) : base.businessFacilities();
        int slotMin =
                c.defaultAppointmentMinutes() != null
                        ? clampSlotMinutes(c.defaultAppointmentMinutes())
                        : base.defaultAppointmentMinutes();
        boolean adimpl = c.billingCompliant() != null ? c.billingCompliant() : base.billingCompliant();
        String calNotes =
                c.calendarAccessNotes() != null ? nullIfBlankToNull(c.calendarAccessNotes()) : base.calendarAccessNotes();
        String sheet = c.spreadsheetUrl() != null ? nullIfBlankToNull(c.spreadsheetUrl()) : base.spreadsheetUrl();
        String waBus =
                c.whatsappBusinessNumber() != null
                        ? nullIfBlankToNull(c.whatsappBusinessNumber())
                        : base.whatsappBusinessNumber();

        return new TenantConfiguration(
                base.tenantId(),
                c.systemPrompt(),
                type,
                apiKey,
                instanceId,
                baseUrl,
                base.profileLevel(),
                base.portalPasswordHash(),
                googleCal,
                est,
                addr,
                hours,
                contacts,
                facilities,
                slotMin,
                adimpl,
                calNotes,
                sheet,
                waBus);
    }

    private static int clampSlotMinutes(int raw) {
        return Math.max(5, Math.min(480, raw));
    }

    /**
     * Texto vazio ou só espaços vira {@code null} para limpar a coluna opcional na base.
     */
    private static String nullIfBlankToNull(String s) {
        return s.isBlank() ? null : s;
    }
}
