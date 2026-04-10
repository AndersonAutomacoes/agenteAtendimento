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
                               profile_level, portal_password_hash
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
        return new TenantConfiguration(
                tenantId,
                rs.getString("system_prompt"),
                providerType,
                rs.getString("whatsapp_api_key"),
                rs.getString("whatsapp_instance_id"),
                rs.getString("whatsapp_base_url"),
                profileLevel,
                rs.getString("portal_password_hash"));
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
                            profile_level, portal_password_hash)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (tenant_id) DO UPDATE SET
                            system_prompt = EXCLUDED.system_prompt,
                            whatsapp_provider_type = EXCLUDED.whatsapp_provider_type,
                            whatsapp_api_key = EXCLUDED.whatsapp_api_key,
                            whatsapp_instance_id = EXCLUDED.whatsapp_instance_id,
                            whatsapp_base_url = EXCLUDED.whatsapp_base_url,
                            profile_level = EXCLUDED.profile_level,
                            portal_password_hash = EXCLUDED.portal_password_hash
                        """)
                .param(configuration.tenantId().value())
                .param(configuration.systemPrompt())
                .param(configuration.whatsappProviderType().name())
                .param(configuration.whatsappApiKey())
                .param(configuration.whatsappInstanceId())
                .param(configuration.whatsappBaseUrl())
                .param(configuration.profileLevel().name())
                .param(configuration.portalPasswordHash())
                .update();
    }
}
