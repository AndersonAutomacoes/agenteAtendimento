package com.atendimento.cerebro.application.analytics;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;

public record AnalyticsSummaryResult(
        TenantId tenantId,
        Instant periodStart,
        Instant periodEnd,
        long totalMessages,
        long uniqueUsers,
        Double successRatePercent) {}
