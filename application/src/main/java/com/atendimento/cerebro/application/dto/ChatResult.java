package com.atendimento.cerebro.application.dto;

import java.util.List;
import java.util.Optional;

public record ChatResult(
        String assistantMessage,
        Optional<WhatsAppInteractiveReply> whatsAppInteractive,
        List<String> additionalOutboundMessages) {

    public ChatResult(String assistantMessage) {
        this(assistantMessage, Optional.empty(), List.of());
    }

    public ChatResult(String assistantMessage, Optional<WhatsAppInteractiveReply> whatsAppInteractive) {
        this(assistantMessage, whatsAppInteractive, List.of());
    }

    public ChatResult {
        if (assistantMessage == null) {
            throw new IllegalArgumentException("assistantMessage is required");
        }
        whatsAppInteractive = whatsAppInteractive != null ? whatsAppInteractive : Optional.empty();
        additionalOutboundMessages =
                additionalOutboundMessages != null ? List.copyOf(additionalOutboundMessages) : List.of();
    }
}
