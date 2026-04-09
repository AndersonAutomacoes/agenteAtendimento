package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import java.util.List;

public record AnalyticsHourlyMessagesHttpResponse(
        String tenantId,
        int hours,
        String periodStart,
        String periodEnd,
        List<HourlyPointHttpResponse> points) {}
