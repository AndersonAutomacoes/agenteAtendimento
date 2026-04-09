package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import java.util.List;

/**
 * Resposta de {@code GET /api/v1/analytics/intents} — contagem por categoria no período atual, idem no período
 * anterior imediato (mesma duração), e contagem por sentimento no período atual.
 */
public record AnalyticsIntentsHttpResponse(
        String tenantId,
        int days,
        String periodStart,
        String periodEnd,
        List<IntentCategoryCountHttp> counts,
        List<IntentCategoryCountHttp> previousCounts,
        String previousPeriodStart,
        String previousPeriodEnd,
        List<SentimentCountHttp> sentimentCounts) {}
