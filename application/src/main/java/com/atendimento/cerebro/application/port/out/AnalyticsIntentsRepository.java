package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.analytics.AnalyticsIntentTrigger;
import com.atendimento.cerebro.application.analytics.ConversationSentiment;
import com.atendimento.cerebro.application.analytics.PrimaryIntentCategory;
import com.atendimento.cerebro.application.analytics.PrimaryIntentCount;
import com.atendimento.cerebro.application.analytics.SentimentCount;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.util.List;

public interface AnalyticsIntentsRepository {

    boolean existsMessageThresholdClassification(TenantId tenantId, String phoneNumber, int turnCount);

    boolean existsInactivityClassification(TenantId tenantId, String phoneNumber, Instant conversationEndAt);

    void insert(
            TenantId tenantId,
            String phoneNumber,
            PrimaryIntentCategory category,
            ConversationSentiment sentiment,
            AnalyticsIntentTrigger trigger,
            int turnCount,
            Instant conversationEndAt,
            String modelLabel);

    /**
     * Conta por categoria com {@code classified_at} em {@code [startInclusive, endExclusive)}.
     */
    List<PrimaryIntentCount> countByCategoryInRange(
            TenantId tenantId, Instant startInclusive, Instant endExclusive);

    /**
     * Conta por sentimento com {@code classified_at} em {@code [startInclusive, endExclusive)}; ignora linhas
     * sem sentimento (dados legados).
     */
    List<SentimentCount> countBySentimentInRange(
            TenantId tenantId, Instant startInclusive, Instant endExclusive);
}
