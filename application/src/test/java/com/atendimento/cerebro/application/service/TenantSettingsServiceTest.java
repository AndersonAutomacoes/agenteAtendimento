package com.atendimento.cerebro.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.dto.TenantSettingsUpdateCommand;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantSettingsServiceTest {

    @Mock
    private TenantConfigurationStorePort tenantConfigurationStore;

    @InjectMocks
    private TenantSettingsService tenantSettingsService;

    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        tenantId = new TenantId("t-1");
    }

    @Test
    void primeira_gravacao_usa_defaults_para_whatsapp() {
        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());

        tenantSettingsService.updateTenantSettings(
                tenantId,
                new TenantSettingsUpdateCommand("hello", WhatsAppProviderType.META, "k", "i", "https://evol", null));

        ArgumentCaptor<TenantConfiguration> cap = ArgumentCaptor.forClass(TenantConfiguration.class);
        verify(tenantConfigurationStore).upsert(cap.capture());
        TenantConfiguration saved = cap.getValue();
        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.systemPrompt()).isEqualTo("hello");
        assertThat(saved.whatsappProviderType()).isEqualTo(WhatsAppProviderType.META);
        assertThat(saved.whatsappApiKey()).isEqualTo("k");
        assertThat(saved.whatsappInstanceId()).isEqualTo("i");
        assertThat(saved.whatsappBaseUrl()).isEqualTo("https://evol");
    }

    @Test
    void merge_so_persona_preserva_whatsapp_existente() {
        TenantConfiguration existing =
                new TenantConfiguration(
                        tenantId,
                        "old",
                        WhatsAppProviderType.EVOLUTION,
                        "secret",
                        "inst",
                        "http://b",
                        ProfileLevel.BASIC,
                        null,
                        null);
        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        tenantSettingsService.updateTenantSettings(
                tenantId, new TenantSettingsUpdateCommand("new persona", null, null, null, null, null));

        ArgumentCaptor<TenantConfiguration> cap = ArgumentCaptor.forClass(TenantConfiguration.class);
        verify(tenantConfigurationStore).upsert(cap.capture());
        TenantConfiguration saved = cap.getValue();
        assertThat(saved.systemPrompt()).isEqualTo("new persona");
        assertThat(saved.whatsappProviderType()).isEqualTo(WhatsAppProviderType.EVOLUTION);
        assertThat(saved.whatsappApiKey()).isEqualTo("secret");
        assertThat(saved.whatsappInstanceId()).isEqualTo("inst");
        assertThat(saved.whatsappBaseUrl()).isEqualTo("http://b");
    }

    @Test
    void string_vazia_limpa_campo_opcional() {
        TenantConfiguration existing =
                new TenantConfiguration(
                        tenantId, "p", WhatsAppProviderType.META, "k", "i", "u", ProfileLevel.BASIC, null, null);
        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        tenantSettingsService.updateTenantSettings(
                tenantId, new TenantSettingsUpdateCommand("p", null, "", "  ", "\t", null));

        ArgumentCaptor<TenantConfiguration> cap = ArgumentCaptor.forClass(TenantConfiguration.class);
        verify(tenantConfigurationStore).upsert(cap.capture());
        TenantConfiguration saved = cap.getValue();
        assertThat(saved.whatsappApiKey()).isNull();
        assertThat(saved.whatsappInstanceId()).isNull();
        assertThat(saved.whatsappBaseUrl()).isNull();
    }

    @Test
    void merge_altera_apenas_provider_quando_informado() {
        TenantConfiguration existing =
                new TenantConfiguration(
                        tenantId, "p", WhatsAppProviderType.SIMULATED, null, null, null, ProfileLevel.BASIC, null, null);
        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        tenantSettingsService.updateTenantSettings(
                tenantId, new TenantSettingsUpdateCommand("p", WhatsAppProviderType.META, null, null, null, null));

        ArgumentCaptor<TenantConfiguration> cap = ArgumentCaptor.forClass(TenantConfiguration.class);
        verify(tenantConfigurationStore).upsert(cap.capture());
        assertThat(cap.getValue().whatsappProviderType()).isEqualTo(WhatsAppProviderType.META);
    }

    @Test
    void merge_preserva_profile_e_password_hash() {
        TenantConfiguration existing =
                new TenantConfiguration(
                        tenantId,
                        "old",
                        WhatsAppProviderType.SIMULATED,
                        null,
                        null,
                        null,
                        ProfileLevel.PRO,
                        "{bcrypt}x",
                        null);
        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        tenantSettingsService.updateTenantSettings(
                tenantId, new TenantSettingsUpdateCommand("newp", WhatsAppProviderType.META, "k", "i", "http://x", null));

        ArgumentCaptor<TenantConfiguration> cap = ArgumentCaptor.forClass(TenantConfiguration.class);
        verify(tenantConfigurationStore).upsert(cap.capture());
        TenantConfiguration saved = cap.getValue();
        assertThat(saved.profileLevel()).isEqualTo(ProfileLevel.PRO);
        assertThat(saved.portalPasswordHash()).isEqualTo("{bcrypt}x");
    }
}
