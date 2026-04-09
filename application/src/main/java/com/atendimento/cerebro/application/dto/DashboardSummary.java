package com.atendimento.cerebro.application.dto;

import java.util.List;

public record DashboardSummary(
        long totalClients,
        long messagesToday,
        Double aiRatePercent,
        String instanceStatus,
        List<DashboardSeriesPoint> series,
        List<DashboardRecentInteraction> recentInteractions) {}
