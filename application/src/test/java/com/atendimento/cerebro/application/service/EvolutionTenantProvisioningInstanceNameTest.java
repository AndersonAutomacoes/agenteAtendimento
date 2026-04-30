package com.atendimento.cerebro.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.domain.tenant.TenantId;
import org.junit.jupiter.api.Test;

class EvolutionTenantProvisioningInstanceNameTest {

    @Test
    void buildsDeterministicSanitizedInstanceName() {
        assertThat(EvolutionTenantProvisioningService.EvolutionInstanceNameBuilder.fromTenantId(new TenantId("loja_centro")))
                .isEqualTo("evo-loja_centro");
        assertThat(EvolutionTenantProvisioningService.EvolutionInstanceNameBuilder.fromTenantId(new TenantId("acme.com.br")))
                .isEqualTo("evo-acme_com_br");
    }
}
