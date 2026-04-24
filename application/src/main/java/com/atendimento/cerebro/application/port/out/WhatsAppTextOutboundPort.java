package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.WhatsAppTextPayload;
import com.atendimento.cerebro.application.dto.WhatsAppTextSendResult;

/**
 * Envio de mensagem de texto via {@code direct:processWhatsAppResponse} com resultado estruturado (não propaga falhas
 * de rede/API como excepção).
 */
public interface WhatsAppTextOutboundPort {

    WhatsAppTextSendResult sendText(WhatsAppTextPayload payload);
}
