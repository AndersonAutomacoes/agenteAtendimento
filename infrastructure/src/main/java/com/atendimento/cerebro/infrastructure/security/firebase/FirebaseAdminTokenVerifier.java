package com.atendimento.cerebro.infrastructure.security.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;

/**
 * Verificação de ID tokens via Admin SDK. Registado como {@code @Bean} em
 * {@link com.atendimento.cerebro.infrastructure.config.FirebaseAppConfiguration} quando o Firebase está ativo,
 * para não interferir com {@code @ConditionalOnMissingBean(FirebaseTokenVerifier)} do fallback.
 */
public class FirebaseAdminTokenVerifier implements FirebaseTokenVerifier {

    @Override
    public DecodedFirebaseToken verify(String idToken) throws FirebaseAuthException {
        var decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
        return new DecodedFirebaseToken(decoded.getUid());
    }
}
