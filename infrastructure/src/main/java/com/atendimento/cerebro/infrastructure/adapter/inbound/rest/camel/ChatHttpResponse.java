package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply;
import java.util.List;

/**
 * DTO de resposta HTTP do chat sem Optional, para serialização estável no Camel REST binding.
 */
public record ChatHttpResponse(
        String assistantMessage,
        WhatsAppInteractiveReply whatsAppInteractive,
        List<String> additionalOutboundMessages) {}
