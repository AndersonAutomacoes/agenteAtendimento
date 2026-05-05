package com.atendimento.cerebro.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.port.out.EvolutionInstanceAdminPort;
import com.atendimento.cerebro.application.port.out.EvolutionInstanceMappingStorePort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvolutionTenantProvisioningReconnectCredentialsTest {

    @Mock
    EvolutionInstanceAdminPort evolutionInstanceAdmin;

    @Mock
    EvolutionInstanceMappingStorePort evolutionInstanceMapping;

    @Mock
    TenantConfigurationStorePort tenantConfigurationStore;

    @Test
    void reconnectUsesGlobalApiKeyAndBaseUrlBeforeTenantStoredValues() {
        TenantId tenantId = new TenantId("acme");
        String instance =
                EvolutionTenantProvisioningService.EvolutionInstanceNameBuilder.fromTenantId(tenantId);
        TenantConfiguration cfg =
                new TenantConfiguration(
                        tenantId,
                        "",
                        WhatsAppProviderType.EVOLUTION,
                        "stale-tenant-api-key-that-would-cause-401",
                        instance,
                        "http://ignored-tenant-evolution/",
                        ProfileLevel.BASIC,
                        null,
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

        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.of(cfg));
        when(evolutionInstanceAdmin.connectAndFetchQrcodeBase64(
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        EvolutionTenantProvisioningService wired =
                new EvolutionTenantProvisioningService(
                        evolutionInstanceAdmin,
                        evolutionInstanceMapping,
                        tenantConfigurationStore,
                        "http://global-evolution/",
                        "global-real-key",
                        "");

        wired.reconnectForTenant(tenantId);

        verify(evolutionInstanceAdmin)
                .connectAndFetchQrcodeBase64(eq("http://global-evolution"), eq("global-real-key"), eq(instance));
    }

    @Test
    void reconnectFallsBackToTenantCredentialsWhenGlobalsUnset() {
        TenantId tenantId = new TenantId("acme2");
        String instance =
                EvolutionTenantProvisioningService.EvolutionInstanceNameBuilder.fromTenantId(tenantId);
        TenantConfiguration cfg =
                new TenantConfiguration(
                        tenantId,
                        "",
                        WhatsAppProviderType.EVOLUTION,
                        "tenant-only-key",
                        instance,
                        "http://tenant-only-base/",
                        ProfileLevel.BASIC,
                        null,
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

        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.of(cfg));
        when(evolutionInstanceAdmin.connectAndFetchQrcodeBase64(
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        EvolutionTenantProvisioningService wired =
                new EvolutionTenantProvisioningService(
                        evolutionInstanceAdmin, evolutionInstanceMapping, tenantConfigurationStore, "", "", "");

        Optional<String> out = wired.reconnectForTenant(tenantId);
        assertThat(out).isEmpty();
        verify(evolutionInstanceAdmin)
                .connectAndFetchQrcodeBase64(
                        eq("http://tenant-only-base"), eq("tenant-only-key"), eq(instance));
    }
}
