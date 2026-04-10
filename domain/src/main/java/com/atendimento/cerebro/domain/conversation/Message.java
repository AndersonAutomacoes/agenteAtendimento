package com.atendimento.cerebro.domain.conversation;

import java.time.Instant;

public record Message(MessageRole role, String content, Instant timestamp, SenderType senderType) {

    public Message {
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be null or blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp is required");
        }
        if (senderType == null) {
            throw new IllegalArgumentException("senderType is required");
        }
    }

    public Message(MessageRole role, String content, Instant timestamp) {
        this(role, content, timestamp, SenderType.fromLegacyRole(role));
    }

    public static Message userMessage(String content) {
        return new Message(MessageRole.USER, content, Instant.now(), SenderType.USER);
    }

    public static Message assistantMessage(String content) {
        return new Message(MessageRole.ASSISTANT, content, Instant.now(), SenderType.BOT);
    }

    /** Mensagem enviada pelo operador no monitor (histórico híbrido para o Gemini). */
    public static Message humanAdminMessage(String content) {
        return new Message(MessageRole.ASSISTANT, content, Instant.now(), SenderType.HUMAN_ADMIN);
    }
}
