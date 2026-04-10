package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.dto.ChatCommand;
import com.atendimento.cerebro.application.dto.ChatResult;
import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.application.port.out.AIEnginePort;
import com.atendimento.cerebro.application.port.out.ConversationBotStatePort;
import com.atendimento.cerebro.application.port.out.ConversationContextStorePort;
import com.atendimento.cerebro.application.port.out.KnowledgeBasePort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.conversation.ConversationContext;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;
import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ordem do fluxo: (1) contexto, (2) RAG com embeddings Google GenAI ({@code KnowledgeBasePort}),
 * (3) geração da resposta com o motor escolhido em {@link com.atendimento.cerebro.application.dto.ChatCommand#chatProvider()}.
 */
public class ChatService implements ChatUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);

    /** Últimas mensagens em {@code conversation_message} enviadas ao modelo (incl. HUMAN_ADMIN). */
    private static final int CONVERSATION_HISTORY_MAX_MESSAGES = 15;

    private final ConversationContextStorePort conversationContextStore;
    private final KnowledgeBasePort knowledgeBase;
    private final AIEnginePort aiEngine;
    private final TenantConfigurationStorePort tenantConfigurationStore;
    private final ConversationBotStatePort conversationBotStatePort;

    public ChatService(
            ConversationContextStorePort conversationContextStore,
            KnowledgeBasePort knowledgeBase,
            AIEnginePort aiEngine,
            TenantConfigurationStorePort tenantConfigurationStore,
            ConversationBotStatePort conversationBotStatePort) {
        this.conversationContextStore = conversationContextStore;
        this.knowledgeBase = knowledgeBase;
        this.aiEngine = aiEngine;
        this.tenantConfigurationStore = tenantConfigurationStore;
        this.conversationBotStatePort = conversationBotStatePort;
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

        Optional<TenantConfiguration> tenantConfig = tenantConfigurationStore.findByTenantId(tenantId);
        String systemPrompt = tenantConfig.map(tc -> tc.systemPrompt().strip()).orElse("");
        if (tenantConfig.isEmpty()) {
            LOG.warn(
                    "Sem registo em tenant_configuration para tenantId={}; a persona (system prompt) não será aplicada — alinhe o id com o usado no dashboard e grave a configuração.",
                    tenantId.value());
        } else if (systemPrompt.isEmpty()) {
            LOG.warn(
                    "tenant_configuration.system_prompt está vazio para tenantId={}; defina a personalidade via API de settings ou na base de dados.",
                    tenantId.value());
        }

        List<KnowledgeHit> knowledgeHits = knowledgeBase.findTopThreeRelevantFragments(tenantId, userText);

        Message userMessage = Message.userMessage(userText);

        List<Message> fromStore = takeLast(context.getMessages(), CONVERSATION_HISTORY_MAX_MESSAGES);
        List<Message> historyForAi = new ArrayList<>(fromStore);
        if (historyForAi.isEmpty() && !command.whatsAppHistoryPriorTurns().isEmpty()) {
            historyForAi = new ArrayList<>(takeLast(command.whatsAppHistoryPriorTurns(), CONVERSATION_HISTORY_MAX_MESSAGES));
        }

        boolean resumeAfterHuman =
                conversationId.waDigitsIfPresent()
                        .map(
                                digits ->
                                        conversationBotStatePort.consumeResumeAiContextIfPending(
                                                tenantId, digits))
                        .orElse(false);

        var aiRequest = new AICompletionRequest(
                tenantId,
                historyForAi,
                knowledgeHits,
                userText,
                systemPrompt,
                provider,
                resumeAfterHuman);

        var aiResponse = aiEngine.complete(aiRequest);

        Message assistantMessage = Message.assistantMessage(aiResponse.content());

        ConversationContext updated = context.append(userMessage, assistantMessage);
        conversationContextStore.save(updated);

        return new ChatResult(aiResponse.content());
    }

    private static List<Message> takeLast(List<Message> messages, int max) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        if (messages.size() <= max) {
            return List.copyOf(messages);
        }
        return List.copyOf(messages.subList(messages.size() - max, messages.size()));
    }
}
