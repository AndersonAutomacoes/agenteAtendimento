package com.atendimento.cerebro.infrastructure.config;

import com.atendimento.cerebro.application.port.out.FirebaseCustomClaimsPort;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "cerebro.firebase", name = "enabled", havingValue = "false")
public class NoopFirebaseCustomClaimsConfiguration {

    @Bean
    FirebaseCustomClaimsPort noopFirebaseCustomClaims() {
        return new FirebaseCustomClaimsPort() {
            @Override
            public void setPortalClaims(String firebaseUid, TenantId tenantId, ProfileLevel profileLevel) {
                // SDK desativado (testes / ambiente sem Firebase)
            }
        };
    }
}
