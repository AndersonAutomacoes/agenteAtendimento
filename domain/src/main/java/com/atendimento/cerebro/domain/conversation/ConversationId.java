package com.atendimento.cerebro.domain.conversation;

public record ConversationId(String value) {
    public ConversationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be null or blank");
        }
    }
}
