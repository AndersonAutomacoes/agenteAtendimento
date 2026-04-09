package com.atendimento.cerebro.infrastructure.analytics;

import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.application.service.AnalyticsService;
import com.atendimento.cerebro.domain.monitoring.ChatMessage;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.config.ChatAnalyticsProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Enfileira a classificação Gemini + persistência em {@code chat_analytics} sem bloquear o webhook
 * (equivalente a um {@code wireTap}; o circuit breaker Camel não permite {@code wireTap} seguido de
 * {@code onFallback} com dois {@code process}).
 */
@Component
public class ChatAnalyticsAfterTurnNotifier {

    private final AnalyticsService analyticsService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAnalyticsProperties properties;

    public ChatAnalyticsAfterTurnNotifier(
            AnalyticsService analyticsService,
            ChatMessageRepository chatMessageRepository,
            ChatAnalyticsProperties properties) {
        this.analyticsService = analyticsService;
        this.chatMessageRepository = chatMessageRepository;
        this.properties = properties;
    }

    @Async
    public void notifyAfterChatTurn(TenantId tenantId, String phoneNumber, String assistantMessage) {
        if (!properties.isEnabled() || tenantId == null || phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }
        String phone = phoneNumber.strip();
        Instant notBefore = Instant.now().minus(Duration.ofDays(Math.max(properties.getHistoryLookbackDays(), 1)));
        List<ChatMessage> rows =
                chatMessageRepository.findRecentForTenantAndPhone(
                        tenantId, phone, notBefore, properties.getMaxHistoryMessages());
        List<ChatMessage> asc = new ArrayList<>(rows);
        Collections.reverse(asc);
        analyticsService.analyzeAndUpsert(tenantId, phone, asc, assistantMessage);
    }
}
