package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.domain.tenant.WhatsAppProviderType;
import org.junit.jupiter.api.Test;

class WhatsAppOutboundRoutesEffectiveProviderTest {

    private static final TenantId TID = new TenantId("t1");

    @Test
    void meta_requiresApiKeyAndInstanceId() {
        assertThat(
                        WhatsAppOutboundRoutes.effectiveProvider(
                                new TenantConfiguration(
                                        TID, "", WhatsAppProviderType.META, null, "pnid", null, ProfileLevel.BASIC, null, null)))
                .isEqualTo(WhatsAppProviderType.SIMULATED);
        assertThat(
                        WhatsAppOutboundRoutes.effectiveProvider(
                                new TenantConfiguration(
                                        TID, "", WhatsAppProviderType.META, "tok", null, null, ProfileLevel.BASIC, null, null)))
                .isEqualTo(WhatsAppProviderType.SIMULATED);
        assertThat(
                        WhatsAppOutboundRoutes.effectiveProvider(
                                new TenantConfiguration(
                                        TID, "", WhatsAppProviderType.META, "tok", "pnid", null, ProfileLevel.BASIC, null, null)))
                .isEqualTo(WhatsAppProviderType.META);
    }

    @Test
    void evolution_requiresBaseUrlApiKeyAndInstanceId() {
        assertThat(
                        WhatsAppOutboundRoutes.effectiveProvider(
                                new TenantConfiguration(
                                        TID,
                                        "",
                                        WhatsAppProviderType.EVOLUTION,
                                        "k",
                                        "inst",
                                        null,
                                        ProfileLevel.BASIC,
                                        null,
                                        null)))
                .isEqualTo(WhatsAppProviderType.SIMULATED);
        assertThat(
                        WhatsAppOutboundRoutes.effectiveProvider(
                                new TenantConfiguration(
                                        TID,
                                        "",
                                        WhatsAppProviderType.EVOLUTION,
                                        "k",
                                        "inst",
                                        "https://h",
                                        ProfileLevel.BASIC,
                                        null,
                                        null)))
                .isEqualTo(WhatsAppProviderType.EVOLUTION);
    }

    @Test
    void simulated_staysSimulated() {
        assertThat(
                        WhatsAppOutboundRoutes.effectiveProvider(
                                new TenantConfiguration(
                                        TID, "", WhatsAppProviderType.SIMULATED, null, null, null, ProfileLevel.BASIC, null, null)))
                .isEqualTo(WhatsAppProviderType.SIMULATED);
    }
}
