package com.atendimento.cerebro.application.analytics;

import java.time.Instant;

public record HourlyMessageVolumePoint(Instant bucketStartUtc, long messageCount) {}
