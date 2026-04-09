package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

public record AnalyticsSummaryHttpResponse(
        String tenantId,
        String periodStart,
        String periodEnd,
        long totalMessages,
        long uniqueUsers,
        Double successRatePercent) {}
