package com.atendimento.cerebro.domain.tenant;

/**
 * Configuração por tenant (persona / system prompt e integração WhatsApp).
 */
public record TenantConfiguration(
        TenantId tenantId,
        String systemPrompt,
        WhatsAppProviderType whatsappProviderType,
        String whatsappApiKey,
        String whatsappInstanceId,
        String whatsappBaseUrl) {

    public TenantConfiguration {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (systemPrompt == null) {
            throw new IllegalArgumentException("systemPrompt must not be null");
        }
        if (whatsappProviderType == null) {
            throw new IllegalArgumentException("whatsappProviderType must not be null");
        }
    }

    /**
     * Configuração inicial quando ainda não existe linha em {@code tenant_configuration}.
     */
    public static TenantConfiguration defaults(TenantId tenantId) {
        return new TenantConfiguration(tenantId, "", WhatsAppProviderType.SIMULATED, null, null, null);
    }

    public TenantConfiguration withSystemPrompt(String newSystemPrompt) {
        if (newSystemPrompt == null) {
            throw new IllegalArgumentException("systemPrompt must not be null");
        }
        return new TenantConfiguration(
                tenantId, newSystemPrompt, whatsappProviderType, whatsappApiKey, whatsappInstanceId, whatsappBaseUrl);
    }
}
