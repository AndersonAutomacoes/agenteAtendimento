package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.TenantId;

/**
 * Envio de mensagens de saída para o WhatsApp (Meta, Evolution ou simulação por log), via rotas Camel.
 */
public interface WhatsAppOutboundPort {

    void sendMessage(TenantId tenantId, String to, String text);

    /**
     * Reenvia texto já associado a uma linha ASSISTANT existente ({@code chat_message.id}), sem novo INSERT.
     */
    void sendMessage(TenantId tenantId, String to, String text, long existingAssistantMessageId);
}
