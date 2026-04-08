package com.atendimento.cerebro.infrastructure.adapter.out.whatsapp;

import com.atendimento.cerebro.application.port.out.WhatsAppOutboundPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.WhatsAppOutboundHeaders;
import java.util.HashMap;
import java.util.Map;
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
        Map<String, Object> headers = new HashMap<>();
        headers.put(WhatsAppOutboundHeaders.TENANT_ID, tenantId.value());
        headers.put(WhatsAppOutboundHeaders.TO, to);
        producerTemplate.sendBodyAndHeaders("direct:processWhatsAppResponse", text, headers);
    }
}
