package com.atendimento.cerebro.domain.conversation;

import java.util.Optional;

public record ConversationId(String value) {
    public ConversationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be null or blank");
        }
    }

    /** Dígitos após o prefixo {@code wa-} (WhatsApp), para alinhar com {@code conversation.phone_number}. */
    public Optional<String> waDigitsIfPresent() {
        String v = value();
        if (v != null && v.startsWith("wa-") && v.length() > 3) {
            return Optional.of(v.substring(3));
        }
        return Optional.empty();
    }
}
