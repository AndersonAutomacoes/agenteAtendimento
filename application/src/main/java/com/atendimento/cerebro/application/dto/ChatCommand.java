package com.atendimento.cerebro.application.dto;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.domain.conversation.ConversationId;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;

public record ChatCommand(
        TenantId tenantId,
        ConversationId conversationId,
        String userMessage,
        Integer topK,
        AiChatProvider chatProvider,
        /**
         * Mensagens anteriores (ex.: WhatsApp em {@code chat_message}) a injetar como histórico no modelo
         * em ordem cronológica crescente. Vazio quando não aplicável.
         */
        List<Message> whatsAppHistoryPriorTurns) {

    public ChatCommand(TenantId tenantId, ConversationId conversationId, String userMessage) {
        this(tenantId, conversationId, userMessage, null, AiChatProvider.GEMINI, List.of());
    }

    public ChatCommand(
            TenantId tenantId,
            ConversationId conversationId,
            String userMessage,
            Integer topK,
            AiChatProvider chatProvider) {
        this(tenantId, conversationId, userMessage, topK, chatProvider, List.of());
    }

    public ChatCommand {
        if (tenantId == null || conversationId == null) {
            throw new IllegalArgumentException("tenantId and conversationId are required");
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
        whatsAppHistoryPriorTurns =
                whatsAppHistoryPriorTurns == null ? List.of() : List.copyOf(whatsAppHistoryPriorTurns);
    }

    public int resolvedTopK() {
        return topK != null && topK > 0 ? topK : 5;
    }
}
