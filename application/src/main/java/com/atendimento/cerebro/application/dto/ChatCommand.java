package com.atendimento.cerebro.application.dto;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.domain.conversation.ConversationId;
import com.atendimento.cerebro.domain.tenant.TenantId;

public record ChatCommand(
        TenantId tenantId,
        ConversationId conversationId,
        String userMessage,
        Integer topK,
        AiChatProvider chatProvider) {

    public ChatCommand(TenantId tenantId, ConversationId conversationId, String userMessage) {
        this(tenantId, conversationId, userMessage, null, AiChatProvider.GEMINI);
    }

    public ChatCommand {
        if (tenantId == null || conversationId == null) {
            throw new IllegalArgumentException("tenantId and conversationId are required");
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
    }

    public int resolvedTopK() {
        return topK != null && topK > 0 ? topK : 5;
    }
}
