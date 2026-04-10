package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Parâmetros opcionais {@code startDate}/{@code endDate} (ISO-8601 instant). Intervalo meia-aberto {@code [start, end)}. */
public record AnalyticsPeriodQuery(Instant start, Instant end) {

    public static final int MAX_RANGE_DAYS = 366;

    public AnalyticsPeriodQuery {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
    }

    public void validateOrThrow() {
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("endDate deve ser posterior a startDate");
        }
        if (Duration.between(start, end).toDays() > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("intervalo máximo é " + MAX_RANGE_DAYS + " dias");
        }
    }
}
