package com.atendimento.cerebro.testsupport;

/**
 * Tokens de integração (perfil {@code test}): o verificador aceita {@code integration:<tenantId>}
 * e associa ao {@code firebase_uid} {@link #uidForTenant(String)} (deve existir em {@code portal_user}).
 */
public final class IntegrationTestFirebaseSupport {

    public static final String INTEGRATION_TOKEN_PREFIX = "integration:";

    private IntegrationTestFirebaseSupport() {}

    /** Alinhado com os INSERTs em {@code portal-users-analytics.sql}. */
    public static String uidForTenant(String tenantId) {
        return "it-" + tenantId;
    }

    public static String bearerTokenForTenant(String tenantId) {
        return INTEGRATION_TOKEN_PREFIX + tenantId;
    }
}
