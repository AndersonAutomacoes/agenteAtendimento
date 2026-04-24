package com.atendimento.cerebro.infrastructure.adapter.out.whatsapp;

import com.atendimento.cerebro.application.dto.WhatsAppTextPayload;
import com.atendimento.cerebro.application.dto.WhatsAppTextSendResult;
import com.atendimento.cerebro.application.port.out.WhatsAppOutboundPort;
import com.atendimento.cerebro.application.port.out.WhatsAppTextOutboundPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Encapsula {@link WhatsAppOutboundPort#sendMessageCapturingEvolutionMessageId} para o fluxo de notificações: falhas de
 * rede/API devolvem {@link WhatsAppTextSendResult#fail} em vez de propagar excepção.
 */
@Component
public class CamelWhatsAppTextOutboundAdapter implements WhatsAppTextOutboundPort {

    private final WhatsAppOutboundPort whatsAppOutboundPort;

    public CamelWhatsAppTextOutboundAdapter(WhatsAppOutboundPort whatsAppOutboundPort) {
        this.whatsAppOutboundPort = whatsAppOutboundPort;
    }

    @Override
    public WhatsAppTextSendResult sendText(WhatsAppTextPayload payload) {
        try {
            Optional<String> mid =
                    whatsAppOutboundPort.sendMessageCapturingEvolutionMessageId(
                            payload.tenantId(), payload.number(), payload.text());
            return WhatsAppTextSendResult.ok(mid.orElse(null));
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = e.getClass().getSimpleName();
            }
            return WhatsAppTextSendResult.fail(msg);
        }
    }
}
