package com.atendimento.cerebro.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.dto.AICompletionResponse;
import com.atendimento.cerebro.application.dto.ChatCommand;
import com.atendimento.cerebro.application.port.out.AIEnginePort;
import com.atendimento.cerebro.application.port.out.ConversationContextStorePort;
import com.atendimento.cerebro.application.port.out.KnowledgeBasePort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.domain.conversation.ConversationContext;
import com.atendimento.cerebro.domain.conversation.ConversationId;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.conversation.MessageRole;
import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationContextStorePort conversationContextStore;

    @Mock
    private KnowledgeBasePort knowledgeBase;

    @Mock
    private AIEnginePort aiEngine;

    @Mock
    private TenantConfigurationStorePort tenantConfigurationStore;

    @InjectMocks
    private ChatService chatService;

    private TenantId tenantId;
    private ConversationId conversationId;

    @BeforeEach
    void setUp() {
        tenantId = new TenantId("tenant-1");
        conversationId = new ConversationId("conv-1");
    }

    @Test
    void chat_createsContextWhenMissing_loadsKb_callsAi_savesWithBothMessages() {
        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(conversationContextStore.load(tenantId, conversationId)).thenReturn(Optional.empty());
        when(knowledgeBase.findTopThreeRelevantFragments(eq(tenantId), eq("hello")))
                .thenReturn(List.of(new KnowledgeHit("k1", "snippet", 0.9)));
        when(aiEngine.complete(any(AICompletionRequest.class)))
                .thenReturn(new AICompletionResponse("hi there"));

        var result = chatService.chat(new ChatCommand(tenantId, conversationId, "hello"));

        assertThat(result.assistantMessage()).isEqualTo("hi there");

        ArgumentCaptor<ConversationContext> saved = ArgumentCaptor.forClass(ConversationContext.class);
        verify(conversationContextStore).save(saved.capture());
        ConversationContext ctx = saved.getValue();
        assertThat(ctx.getMessages()).hasSize(2);
        assertThat(ctx.getMessages().get(0).role()).isEqualTo(MessageRole.USER);
        assertThat(ctx.getMessages().get(0).content()).isEqualTo("hello");
        assertThat(ctx.getMessages().get(1).role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(ctx.getMessages().get(1).content()).isEqualTo("hi there");

        var ordered = inOrder(tenantConfigurationStore, conversationContextStore, knowledgeBase, aiEngine);
        ordered.verify(conversationContextStore).load(tenantId, conversationId);
        ordered.verify(knowledgeBase).findTopThreeRelevantFragments(tenantId, "hello");
        ordered.verify(aiEngine).complete(any(AICompletionRequest.class));
        ordered.verify(conversationContextStore).save(any(ConversationContext.class));
    }

    @Test
    void chat_appendsToExistingHistory() {
        ConversationContext existing = ConversationContext.builder()
                .tenantId(tenantId)
                .conversationId(conversationId)
                .messages(List.of(Message.userMessage("prev")))
                .build();
        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(conversationContextStore.load(tenantId, conversationId)).thenReturn(Optional.of(existing));
        when(knowledgeBase.findTopThreeRelevantFragments(tenantId, "next")).thenReturn(List.of());
        when(aiEngine.complete(any(AICompletionRequest.class)))
                .thenReturn(new AICompletionResponse("ok"));

        chatService.chat(new ChatCommand(tenantId, conversationId, "next", 3, AiChatProvider.GEMINI));

        ArgumentCaptor<AICompletionRequest> aiReq = ArgumentCaptor.forClass(AICompletionRequest.class);
        verify(aiEngine).complete(aiReq.capture());
        assertThat(aiReq.getValue().conversationHistory())
                .extracting(Message::role, Message::content)
                .containsExactly(tuple(MessageRole.USER, "prev"));

        ArgumentCaptor<ConversationContext> saved = ArgumentCaptor.forClass(ConversationContext.class);
        verify(conversationContextStore).save(saved.capture());
        assertThat(saved.getValue().getMessages()).hasSize(3);
        assertThat(saved.getValue().getMessages().get(2).content()).isEqualTo("ok");
    }

    @Test
    void chat_whenWhatsAppHistoryProvided_usesItForAiInsteadOfContextStore() {
        ConversationContext existing = ConversationContext.builder()
                .tenantId(tenantId)
                .conversationId(conversationId)
                .messages(List.of(Message.userMessage("from-store")))
                .build();
        List<Message> wa = List.of(Message.userMessage("wa-a"), Message.assistantMessage("wa-b"));
        when(tenantConfigurationStore.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(conversationContextStore.load(tenantId, conversationId)).thenReturn(Optional.of(existing));
        when(knowledgeBase.findTopThreeRelevantFragments(tenantId, "next")).thenReturn(List.of());
        when(aiEngine.complete(any(AICompletionRequest.class)))
                .thenReturn(new AICompletionResponse("ok"));

        chatService.chat(
                new ChatCommand(
                        tenantId, conversationId, "next", null, AiChatProvider.GEMINI, wa));

        ArgumentCaptor<AICompletionRequest> aiReq = ArgumentCaptor.forClass(AICompletionRequest.class);
        verify(aiEngine).complete(aiReq.capture());
        assertThat(aiReq.getValue().conversationHistory()).isEqualTo(wa);
    }
}
