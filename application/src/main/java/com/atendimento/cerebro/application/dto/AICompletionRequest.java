package com.atendimento.cerebro.application.dto;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;

/**
 * @param systemPrompt persona do tenant (vazio se não configurada na base).
 * @param chatProvider motor de chat a usar; {@code null} usa o default da aplicação ({@code cerebro.ai.default-chat-provider}).
 */
public record AICompletionRequest(
        TenantId tenantId,
        List<Message> conversationHistory,
        List<KnowledgeHit> knowledgeHits,
        String userMessage,
        String systemPrompt,
        AiChatProvider chatProvider) {

    public AICompletionRequest {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
        if (systemPrompt == null) {
            systemPrompt = "";
        }
        if (chatProvider == null) {
            throw new IllegalArgumentException("chatProvider is required");
        }
    }
}
