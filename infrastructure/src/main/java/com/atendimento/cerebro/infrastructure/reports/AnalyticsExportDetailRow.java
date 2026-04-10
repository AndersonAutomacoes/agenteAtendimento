package com.atendimento.cerebro.infrastructure.reports;

import java.time.Instant;

public record AnalyticsExportDetailRow(
        Instant classifiedAt, String phone, String primaryIntent, String sentiment, String firstUserMessage) {}
