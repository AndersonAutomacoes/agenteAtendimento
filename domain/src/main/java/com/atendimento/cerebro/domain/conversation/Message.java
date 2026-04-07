package com.atendimento.cerebro.domain.conversation;

import java.time.Instant;

public record Message(MessageRole role, String content, Instant timestamp) {
    public Message {
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be null or blank");
        }
        timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static Message userMessage(String content) {
        return new Message(MessageRole.USER, content, Instant.now());
    }

    public static Message assistantMessage(String content) {
        return new Message(MessageRole.ASSISTANT, content, Instant.now());
    }
}
