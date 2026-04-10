package com.atendimento.cerebro.infrastructure.security.firebase;

@FunctionalInterface
public interface FirebaseTokenVerifier {

    DecodedFirebaseToken verify(String idToken) throws Exception;
}
