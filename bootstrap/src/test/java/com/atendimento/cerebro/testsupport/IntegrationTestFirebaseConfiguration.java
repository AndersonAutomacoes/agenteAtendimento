package com.atendimento.cerebro.testsupport;

import com.atendimento.cerebro.infrastructure.security.firebase.FirebaseTokenVerifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class IntegrationTestFirebaseConfiguration {

    @Bean
    @Primary
    FirebaseTokenVerifier integrationTestFirebaseTokenVerifier() {
        return new IntegrationTestFirebaseTokenVerifier();
    }
}
