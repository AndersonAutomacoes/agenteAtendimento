package com.atendimento.cerebro.application.port.out;

/**
 * Idempotência de webhooks inbound: o mesmo {@code providerMessageId} por tenant só deve ser processado uma vez.
 *
 * @return {@code true} se o evento deve seguir para chat/envio; {@code false} se for duplicado (responder ignored).
 */
public interface InboundWhatsAppDeduperPort {

    boolean tryClaimInboundMessage(String tenantId, String providerMessageId);
}
