package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresTenantConfigurationStore implements TenantConfigurationStorePort {

    private final JdbcClient jdbcClient;

    public PostgresTenantConfigurationStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantConfiguration> findByTenantId(TenantId tenantId) {
        String tid = tenantId.value();
        return jdbcClient
                .sql(
                        """
                        SELECT system_prompt, whatsapp_provider_type, whatsapp_api_key,
                               whatsapp_instance_id, whatsapp_base_url,
                               profile_level, portal_password_hash, google_calendar_id,
                               establishment_name, business_address, opening_hours, business_contacts, business_facilities,
                               default_appointment_minutes, billing_compliant,
                               calendar_access_notes, google_spreadsheet_url, whatsapp_business_number
                        FROM tenant_configuration WHERE tenant_id = ?
                        """)
                .param(tid)
                .query((rs, rowNum) -> mapRow(tenantId, rs))
                .optional();
    }

    private static TenantConfiguration mapRow(TenantId tenantId, ResultSet rs) throws SQLException {
        WhatsAppProviderType providerType;
        String typeStr = rs.getString("whatsapp_provider_type");
        try {
            providerType = WhatsAppProviderType.valueOf(typeStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("whatsapp_provider_type inválido na base: " + typeStr, e);
        }
        ProfileLevel profileLevel = parseProfileLevel(rs.getString("profile_level"));
        int slotMin = rs.getInt("default_appointment_minutes");
        if (rs.wasNull() || slotMin <= 0) {
            slotMin = 30;
        }
        return new TenantConfiguration(
                tenantId,
                rs.getString("system_prompt"),
                providerType,
                rs.getString("whatsapp_api_key"),
                rs.getString("whatsapp_instance_id"),
                rs.getString("whatsapp_base_url"),
                profileLevel,
                rs.getString("portal_password_hash"),
                rs.getString("google_calendar_id"),
                rs.getString("establishment_name"),
                rs.getString("business_address"),
                rs.getString("opening_hours"),
                rs.getString("business_contacts"),
                rs.getString("business_facilities"),
                slotMin,
                rs.getBoolean("billing_compliant"),
                rs.getString("calendar_access_notes"),
                rs.getString("google_spreadsheet_url"),
                rs.getString("whatsapp_business_number"));
    }

    private static ProfileLevel parseProfileLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return ProfileLevel.BASIC;
        }
        try {
            return ProfileLevel.valueOf(raw.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("profile_level inválido na base: " + raw, e);
        }
    }

    @Override
    @Transactional
    public void upsert(TenantConfiguration configuration) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO tenant_configuration (
                            tenant_id, system_prompt, whatsapp_provider_type,
                            whatsapp_api_key, whatsapp_instance_id, whatsapp_base_url,
                            profile_level, portal_password_hash, google_calendar_id,
                            establishment_name, business_address, opening_hours, business_contacts, business_facilities,
                            default_appointment_minutes, billing_compliant,
                            calendar_access_notes, google_spreadsheet_url, whatsapp_business_number)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (tenant_id) DO UPDATE SET
                            system_prompt = EXCLUDED.system_prompt,
                            whatsapp_provider_type = EXCLUDED.whatsapp_provider_type,
                            whatsapp_api_key = EXCLUDED.whatsapp_api_key,
                            whatsapp_instance_id = EXCLUDED.whatsapp_instance_id,
                            whatsapp_base_url = EXCLUDED.whatsapp_base_url,
                            profile_level = EXCLUDED.profile_level,
                            portal_password_hash = EXCLUDED.portal_password_hash,
                            google_calendar_id = EXCLUDED.google_calendar_id,
                            establishment_name = EXCLUDED.establishment_name,
                            business_address = EXCLUDED.business_address,
                            opening_hours = EXCLUDED.opening_hours,
                            business_contacts = EXCLUDED.business_contacts,
                            business_facilities = EXCLUDED.business_facilities,
                            default_appointment_minutes = EXCLUDED.default_appointment_minutes,
                            billing_compliant = EXCLUDED.billing_compliant,
                            calendar_access_notes = EXCLUDED.calendar_access_notes,
                            google_spreadsheet_url = EXCLUDED.google_spreadsheet_url,
                            whatsapp_business_number = EXCLUDED.whatsapp_business_number
                        """)
                .param(configuration.tenantId().value())
                .param(configuration.systemPrompt())
                .param(configuration.whatsappProviderType().name())
                .param(configuration.whatsappApiKey())
                .param(configuration.whatsappInstanceId())
                .param(configuration.whatsappBaseUrl())
                .param(configuration.profileLevel().name())
                .param(configuration.portalPasswordHash())
                .param(configuration.googleCalendarId())
                .param(configuration.establishmentName())
                .param(configuration.businessAddress())
                .param(configuration.openingHours())
                .param(configuration.businessContacts())
                .param(configuration.businessFacilities())
                .param(configuration.defaultAppointmentMinutes())
                .param(configuration.billingCompliant())
                .param(configuration.calendarAccessNotes())
                .param(configuration.spreadsheetUrl())
                .param(configuration.whatsappBusinessNumber())
                .update();
    }
}
