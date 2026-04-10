package com.atendimento.cerebro.infrastructure.security.firebase;

/** Resultado da verificação de um ID Token Firebase (apenas uid necessário ao filtro). */
public record DecodedFirebaseToken(String uid) {

    public DecodedFirebaseToken {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("uid is required");
        }
    }
}
