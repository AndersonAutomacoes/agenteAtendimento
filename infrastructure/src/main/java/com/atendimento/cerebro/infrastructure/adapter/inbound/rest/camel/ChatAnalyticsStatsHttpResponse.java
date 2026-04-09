package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import java.util.List;

/** Resposta de {@code GET /api/v1/analytics/stats} — contagens por intenção e sentimento (snapshot actual). */
public record ChatAnalyticsStatsHttpResponse(
        String tenantId, String generatedAt, List<IntentCategoryCountHttp> intents, List<IntentCategoryCountHttp> sentiments) {}
