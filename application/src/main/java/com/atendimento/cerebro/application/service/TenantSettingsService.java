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

        return new TenantConfiguration(
                base.tenantId(),
                c.systemPrompt(),
                type,
                apiKey,
                instanceId,
                baseUrl,
                base.profileLevel(),
                base.portalPasswordHash());
    }

    /**
     * Texto vazio ou só espaços vira {@code null} para limpar a coluna opcional na base.
     */
    private static String nullIfBlankToNull(String s) {
        return s.isBlank() ? null : s;
    }
}
