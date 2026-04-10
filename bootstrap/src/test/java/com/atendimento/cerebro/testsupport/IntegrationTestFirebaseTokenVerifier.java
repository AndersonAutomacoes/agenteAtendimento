package com.atendimento.cerebro.testsupport;

import com.atendimento.cerebro.infrastructure.security.firebase.DecodedFirebaseToken;
import com.atendimento.cerebro.infrastructure.security.firebase.FirebaseTokenVerifier;

/**
 * Substitui o Firebase Admin em testes: aceita apenas tokens {@code integration:<tenantId>}.
 */
public class IntegrationTestFirebaseTokenVerifier implements FirebaseTokenVerifier {

    @Override
    public DecodedFirebaseToken verify(String idToken) throws Exception {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("token vazio");
        }
        if (!idToken.startsWith(IntegrationTestFirebaseSupport.INTEGRATION_TOKEN_PREFIX)) {
            throw new IllegalStateException("use integration:<tenantId> nos testes de integração");
        }
        String tenantId = idToken.substring(IntegrationTestFirebaseSupport.INTEGRATION_TOKEN_PREFIX.length());
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId em falta após integration:");
        }
        return new DecodedFirebaseToken(IntegrationTestFirebaseSupport.uidForTenant(tenantId.strip()));
    }
}
