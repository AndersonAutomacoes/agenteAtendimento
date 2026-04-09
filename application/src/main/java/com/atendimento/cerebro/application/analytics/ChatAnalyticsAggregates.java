package com.atendimento.cerebro.application.analytics;

import java.util.Map;

/** Contagens agregadas para o dashboard (uma linha por conversa em {@code chat_analytics}). */
public record ChatAnalyticsAggregates(
        Map<ChatMainIntent, Long> intentsByCategory, Map<ChatSentiment, Long> sentimentsByCategory) {}
