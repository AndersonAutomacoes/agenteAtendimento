package com.atendimento.cerebro.infrastructure.config;

import com.atendimento.cerebro.application.port.out.FirebaseCustomClaimsPort;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cobre o caso {@code cerebro.firebase.enabled=true} (ou omisso com default) em que o SDK não expõe
 * {@link FirebaseCustomClaimsPort} (ex.: credenciais em falta). Com {@code enabled=false}, use apenas
 * {@link NoopFirebaseCustomClaimsConfiguration} para evitar dois no-ops.
 */
@Configuration
public class FirebaseCustomClaimsFallbackConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FirebaseCustomClaimsFallbackConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(FirebaseCustomClaimsPort.class)
    @ConditionalOnProperty(prefix = "cerebro.firebase", name = "enabled", havingValue = "true", matchIfMissing = true)
    FirebaseCustomClaimsPort noopFirebaseCustomClaimsWhenMissing() {
        log.warn(
                "Nenhum FirebaseCustomClaimsPort registado: use cerebro.firebase.enabled=false (no-op explícito) ou credenciais válidas para o SDK. "
                        + "Claims não serão sincronizados com o Firebase até haver configuração.");
        return new FirebaseCustomClaimsPort() {
            @Override
            public void setPortalClaims(String firebaseUid, TenantId tenantId, ProfileLevel profileLevel) {
                // Sem SDK / bean real — registo pode concluir na BD sem atualizar claims no IdP.
            }
        };
    }
}
