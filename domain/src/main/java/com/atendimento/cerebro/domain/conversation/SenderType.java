package com.atendimento.cerebro.domain.conversation;

/** Origem da mensagem em {@code conversation_message} (sincronização de contexto IA / humano). */
public enum SenderType {
    USER,
    BOT,
    HUMAN_ADMIN;

    public static SenderType fromLegacyRole(MessageRole role) {
        return switch (role) {
            case USER -> USER;
            case ASSISTANT, SYSTEM -> BOT;
        };
    }
}
