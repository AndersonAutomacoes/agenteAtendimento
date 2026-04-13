package com.atendimento.cerebro.application.dto;

import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import java.time.Instant;

/**
 * Estado mínimo em {@code chat_analytics} para pontuar o lead: intenção atual e âncora temporal
 * (primeira classificação ou fallback a {@code last_updated}).
 */
public record ChatAnalyticsLeadSnapshot(ChatMainIntent mainIntent, Instant engagementAnchor) {}
