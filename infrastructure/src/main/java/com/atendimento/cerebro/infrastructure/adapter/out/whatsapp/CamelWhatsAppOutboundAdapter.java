package com.atendimento.cerebro.infrastructure.adapter.out.whatsapp;

import com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply;
import com.atendimento.cerebro.application.scheduling.AssistantOutputSanitizer;
import com.atendimento.cerebro.application.port.out.WhatsAppOutboundPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.WhatsAppOutboundHeaders;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

@Component
public class CamelWhatsAppOutboundAdapter implements WhatsAppOutboundPort {

    private final ProducerTemplate producerTemplate;

    public CamelWhatsAppOutboundAdapter(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    @Override
    public void sendMessage(TenantId tenantId, String to, String text) {
        sendMessage(tenantId, to, text, Optional.empty());
    }

    @Override
    public void sendMessage(
            TenantId tenantId, String to, String text, Optional<WhatsAppInteractiveReply> whatsAppInteractive) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(WhatsAppOutboundHeaders.TENANT_ID, tenantId.value());
        headers.put(WhatsAppOutboundHeaders.TO, to);
        headers.put(WhatsAppOutboundHeaders.WHATSAPP_INTERACTIVE, whatsAppInteractive.orElse(null));
        String safe = AssistantOutputSanitizer.stripSquareBracketSegments(text);
        producerTemplate.sendBodyAndHeaders("direct:processWhatsAppResponse", safe, headers);
    }

    @Override
    public void sendMessage(TenantId tenantId, String to, String text, long existingAssistantMessageId) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(WhatsAppOutboundHeaders.TENANT_ID, tenantId.value());
        headers.put(WhatsAppOutboundHeaders.TO, to);
        headers.put(WhatsAppOutboundHeaders.ASSISTANT_MESSAGE_ID, existingAssistantMessageId);
        String safe = AssistantOutputSanitizer.stripSquareBracketSegments(text);
        producerTemplate.sendBodyAndHeaders("direct:processWhatsAppResponse", safe, headers);
    }
}
