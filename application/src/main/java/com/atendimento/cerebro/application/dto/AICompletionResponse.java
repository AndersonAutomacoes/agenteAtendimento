package com.atendimento.cerebro.application.dto;

import java.util.List;
import java.util.Optional;

public record AICompletionResponse(
        String content,
        Optional<WhatsAppInteractiveReply> whatsAppInteractive,
        List<String> additionalOutboundMessages) {

    public AICompletionResponse(String content) {
        this(content, Optional.empty(), List.of());
    }

    public AICompletionResponse(String content, Optional<WhatsAppInteractiveReply> whatsAppInteractive) {
        this(content, whatsAppInteractive, List.of());
    }

    public AICompletionResponse {
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
        whatsAppInteractive = whatsAppInteractive != null ? whatsAppInteractive : Optional.empty();
        additionalOutboundMessages =
                additionalOutboundMessages != null ? List.copyOf(additionalOutboundMessages) : List.of();
    }
}
