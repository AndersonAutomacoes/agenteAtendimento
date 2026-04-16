package com.atendimento.cerebro.application.dto;

import com.atendimento.cerebro.application.scheduling.AssistantOutputSanitizer;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        assistantMessage = AssistantOutputSanitizer.stripSquareBracketSegments(assistantMessage);
        whatsAppInteractive = whatsAppInteractive != null ? whatsAppInteractive : Optional.empty();
        additionalOutboundMessages =
                additionalOutboundMessages != null
                        ? additionalOutboundMessages.stream()
                                .map(AssistantOutputSanitizer::stripSquareBracketSegments)
                                .collect(Collectors.toUnmodifiableList())
                        : List.of();
    }
}
