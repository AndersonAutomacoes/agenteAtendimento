package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.TenantId;

/**
 * Envio de mensagens de saída para o WhatsApp (Meta, Evolution ou simulação por log), via rotas Camel.
 */
public interface WhatsAppOutboundPort {

    void sendMessage(TenantId tenantId, String to, String text);
}
