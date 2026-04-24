package com.atendimento.cerebro.application.dto;

import com.atendimento.cerebro.application.scheduling.AssistantOutputSanitizer;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record AICompletionResponse(
        String content,
        Optional<WhatsAppInteractiveReply> whatsAppInteractive,
        List<String> additionalOutboundMessages,
        /**
         * Se presente, texto enviado ao WhatsApp (pode ser vazio {@link String#isBlank()} para não duplicar a
         * notificação assíncrona). O campo {@code content} permanece para o histórico do assistente.
         */
        Optional<String> outboundWhatsappTextOverride) {

    public AICompletionResponse(String content) {
        this(content, Optional.empty(), List.of(), Optional.empty());
    }

    public AICompletionResponse(String content, Optional<WhatsAppInteractiveReply> whatsAppInteractive) {
        this(content, whatsAppInteractive, List.of(), Optional.empty());
    }

    public AICompletionResponse(
            String content, Optional<WhatsAppInteractiveReply> whatsAppInteractive, List<String> additional) {
        this(content, whatsAppInteractive, additional, Optional.empty());
    }

    public AICompletionResponse {
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
        content = AssistantOutputSanitizer.stripSquareBracketSegments(content);
        whatsAppInteractive = whatsAppInteractive != null ? whatsAppInteractive : Optional.empty();
        additionalOutboundMessages =
                additionalOutboundMessages != null
                        ? additionalOutboundMessages.stream()
                                .map(AssistantOutputSanitizer::stripSquareBracketSegments)
                                .collect(Collectors.toUnmodifiableList())
                        : List.of();
        if (outboundWhatsappTextOverride == null) {
            outboundWhatsappTextOverride = Optional.empty();
        } else {
            outboundWhatsappTextOverride =
                    outboundWhatsappTextOverride
                            .map(s -> s == null ? "" : AssistantOutputSanitizer.stripSquareBracketSegments(s));
        }
    }
}
