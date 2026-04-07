package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.dto.ChatCommand;
import com.atendimento.cerebro.application.dto.ChatResult;
import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.application.port.out.AIEnginePort;
import com.atendimento.cerebro.application.port.out.ConversationContextStorePort;
import com.atendimento.cerebro.application.port.out.KnowledgeBasePort;
import com.atendimento.cerebro.domain.conversation.ConversationContext;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;
import java.util.List;

/**
 * Ordem do fluxo: (1) contexto, (2) RAG com embeddings Google GenAI ({@code KnowledgeBasePort}),
 * (3) geração da resposta com o motor escolhido em {@link com.atendimento.cerebro.application.dto.ChatCommand#chatProvider()}.
 */
public class ChatService implements ChatUseCase {

    private final ConversationContextStorePort conversationContextStore;
    private final KnowledgeBasePort knowledgeBase;
    private final AIEnginePort aiEngine;

    public ChatService(
            ConversationContextStorePort conversationContextStore,
            KnowledgeBasePort knowledgeBase,
            AIEnginePort aiEngine) {
        this.conversationContextStore = conversationContextStore;
        this.knowledgeBase = knowledgeBase;
        this.aiEngine = aiEngine;
    }

    @Override
    public ChatResult chat(ChatCommand command) {
        var tenantId = command.tenantId();
        var conversationId = command.conversationId();
        var userText = command.userMessage();

        ConversationContext context = conversationContextStore
                .load(tenantId, conversationId)
                .orElseGet(() -> ConversationContext.builder()
                        .tenantId(tenantId)
                        .conversationId(conversationId)
                        .build());

        AiChatProvider provider =
                command.chatProvider() != null ? command.chatProvider() : AiChatProvider.GEMINI;

        List<KnowledgeHit> knowledgeHits = knowledgeBase.findTopThreeRelevantFragments(tenantId, userText);

        Message userMessage = Message.userMessage(userText);

        var aiRequest = new AICompletionRequest(
                tenantId, context.getMessages(), knowledgeHits, userText, provider);
        var aiResponse = aiEngine.complete(aiRequest);

        Message assistantMessage = Message.assistantMessage(aiResponse.content());

        ConversationContext updated = context.append(userMessage, assistantMessage);
        conversationContextStore.save(updated);

        return new ChatResult(aiResponse.content());
    }
}
