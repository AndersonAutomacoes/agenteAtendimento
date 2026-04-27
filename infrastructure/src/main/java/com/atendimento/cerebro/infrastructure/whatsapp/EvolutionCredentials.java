package com.atendimento.cerebro.infrastructure.whatsapp;

/**
 * Alinha credenciais HTTP da Evolution (global no Spring) com o que está em {@code tenant_configuration}.
 * Valores definidos a nível de aplicação têm prioridade (mesma regra que {@code CEREBRO_WHATSAPP_EVOLUTION_BASE_URL}
 * em relação a {@code whatsapp_base_url}).
 */
public final class EvolutionCredentials {

    private EvolutionCredentials() {}

    /**
     * Chave do header {@code apikey} nas chamadas à Evolution. O valor global (p.ex.
     * {@code CEREBRO_WHATSAPP_EVOLUTION_API_KEY} = {@code AUTHENTICATION_API_KEY} do container Evolution) tem
     * prioridade sobre a chave do tenant, para evitar 401 por drift entre portal e ficheiro .env.
     */
    public static String resolveApiKey(String globalApiKey, String tenantApiKey) {
        return firstNonBlank(globalApiKey, tenantApiKey);
    }

    /**
     * URL base da API Evolution; o override Spring tem prioridade sobre a coluna do tenant.
     */
    public static String resolveBaseUrl(String globalBaseUrl, String tenantBaseUrl) {
        return firstNonBlank(globalBaseUrl, tenantBaseUrl);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.strip();
        }
        if (b != null && !b.isBlank()) {
            return b.strip();
        }
        return "";
    }
}
