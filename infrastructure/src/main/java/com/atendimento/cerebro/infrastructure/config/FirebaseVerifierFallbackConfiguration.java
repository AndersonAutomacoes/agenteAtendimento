package com.atendimento.cerebro.infrastructure.config;

import com.atendimento.cerebro.infrastructure.security.firebase.FirebaseTokenVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirebaseVerifierFallbackConfiguration {

    /**
     * Evita falha de arranque quando o SDK Firebase não está configurado; substituível por um bean real ou
     * {@code @MockBean} em testes.
     */
    @Bean
    @ConditionalOnMissingBean(FirebaseTokenVerifier.class)
    FirebaseTokenVerifier unconfiguredVerifier() {
        return token -> {
            throw new IllegalStateException(
                    "FirebaseTokenVerifier não configurado: ative cerebro.firebase.enabled e credenciais, ou forneça um bean de teste.");
        };
    }
}
