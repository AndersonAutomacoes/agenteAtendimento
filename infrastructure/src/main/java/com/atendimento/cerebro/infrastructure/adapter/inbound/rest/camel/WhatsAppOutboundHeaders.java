package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

/**
 * Cabeçalhos Camel para {@code direct:processWhatsAppResponse} (alinhados ao pedido: {@code provider}).
 */
public final class WhatsAppOutboundHeaders {

    /** Valor {@link com.atendimento.cerebro.domain.tenant.TenantId#value()}. */
    public static final String TENANT_ID = "tenantId";

    /** Destinatário (telefone como veio do webhook). */
    public static final String TO = "waTo";

    /**
     * Provedor efetivo: {@code META}, {@code EVOLUTION} ou {@code SIMULATED} (último → {@code direct:sendToLog}).
     */
    public static final String PROVIDER = "provider";

    public static final String PROP_WA_TEXT = "waText";

    public static final String PROP_WA_TO = "waTo";

    public static final String PROP_WA_TENANT_CONFIG = "waTenantConfig";

    private WhatsAppOutboundHeaders() {}
}
