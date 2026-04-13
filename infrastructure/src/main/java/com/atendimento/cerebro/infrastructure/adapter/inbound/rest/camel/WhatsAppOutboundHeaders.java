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

    /**
     * Se presente (reenvio), reutiliza este {@code chat_message.id} em vez de inserir nova linha ASSISTANT.
     */
    public static final String ASSISTANT_MESSAGE_ID = "assistantMessageId";

    /** {@link com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply} para Evolution (botões de horário). */
    public static final String WHATSAPP_INTERACTIVE = "whatsAppInteractive";

    /** Property Camel: payload interativo opcional (Evolution). */
    public static final String PROP_WA_INTERACTIVE = "waInteractive";

    /** Property Camel: id da linha ASSISTANT em curso (novo insert ou reenvio). */
    public static final String PROP_ASSISTANT_MESSAGE_ID = "assistantMessageRowId";

    private WhatsAppOutboundHeaders() {}
}
