package com.atendimento.cerebro.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.analytics.ConversationAnalyticsCategory;
import com.atendimento.cerebro.application.analytics.TenantPhonePair;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.dto.AICompletionResponse;
import com.atendimento.cerebro.application.port.out.AIEnginePort;
import com.atendimento.cerebro.application.port.out.AnalyticsStatsRepository;
import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationCategoryAnalyticsServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private AnalyticsStatsRepository analyticsStatsRepository;

    @Mock
    private AIEnginePort aiEngine;

    private ConversationCategoryAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new ConversationCategoryAnalyticsService(
                chatMessageRepository, analyticsStatsRepository, aiEngine, 100, 4000);
    }

    @Test
    void parseCategory_mapsSynonymsAndFallback() {
        assertThat(ConversationCategoryAnalyticsService.parseCategory("VENDAS")).isEqualTo(ConversationAnalyticsCategory.VENDAS);
        assertThat(ConversationCategoryAnalyticsService.parseCategory("  suporte "))
                .isEqualTo(ConversationAnalyticsCategory.SUPORTE);
        assertThat(ConversationCategoryAnalyticsService.parseCategory("FINANCEIRO."))
                .isEqualTo(ConversationAnalyticsCategory.FINANCEIRO);
        assertThat(ConversationCategoryAnalyticsService.parseCategory("não sei"))
                .isEqualTo(ConversationAnalyticsCategory.OUTRO);
    }

    @Test
    void classifyForWindow_skipsWhenAlreadyExists() {
        Instant start = Instant.parse("2026-04-09T10:00:00Z");
        Instant end = Instant.parse("2026-04-09T11:00:00Z");
        var tenant = new TenantId("t1");
        when(chatMessageRepository.findDistinctTenantPhonesWithUserMessages(start, end))
                .thenReturn(List.of(new TenantPhonePair(tenant, "5511")));
        when(analyticsStatsRepository.existsForBucketAndPhone(tenant, start, "5511")).thenReturn(true);

        service.classifyForWindow(start, end);

        verify(aiEngine, never()).complete(any(AICompletionRequest.class));
        verify(analyticsStatsRepository, never()).insertIfAbsent(any(), any(), any(), any(), any());
    }

    @Test
    void classifyForWindow_callsGeminiAndPersists() {
        Instant start = Instant.parse("2026-04-09T10:00:00Z");
        Instant end = Instant.parse("2026-04-09T11:00:00Z");
        var tenant = new TenantId("t1");
        when(chatMessageRepository.findDistinctTenantPhonesWithUserMessages(start, end))
                .thenReturn(List.of(new TenantPhonePair(tenant, "5511")));
        when(analyticsStatsRepository.existsForBucketAndPhone(tenant, start, "5511")).thenReturn(false);
        when(chatMessageRepository.aggregateUserMessageTextForRange(tenant, "5511", start, end))
                .thenReturn("Quero comprar pneus");
        when(aiEngine.complete(any(AICompletionRequest.class))).thenReturn(new AICompletionResponse("VENDAS"));
        when(analyticsStatsRepository.insertIfAbsent(
                        eq(tenant),
                        eq(start),
                        eq("5511"),
                        eq(ConversationAnalyticsCategory.VENDAS),
                        eq("VENDAS")))
                .thenReturn(true);

        service.classifyForWindow(start, end);

        var captor = ArgumentCaptor.forClass(AICompletionRequest.class);
        verify(aiEngine).complete(captor.capture());
        AICompletionRequest req = captor.getValue();
        assertThat(req.tenantId()).isEqualTo(tenant);
        assertThat(req.chatProvider()).isEqualTo(AiChatProvider.GEMINI);
        assertThat(req.userMessage()).contains("pneus");
        verify(analyticsStatsRepository)
                .insertIfAbsent(
                        tenant, start, "5511", ConversationAnalyticsCategory.VENDAS, "VENDAS");
    }
}
