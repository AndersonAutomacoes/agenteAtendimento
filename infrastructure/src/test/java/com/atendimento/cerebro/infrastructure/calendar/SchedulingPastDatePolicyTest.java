package com.atendimento.cerebro.infrastructure.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class SchedulingPastDatePolicyTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Test
    void rejectsDayStrictlyBeforeTodayInZone() {
        Clock clock = Clock.fixed(ZonedDateTime.of(2026, 4, 10, 12, 0, 0, 0, SP).toInstant(), SP);
        assertThat(SchedulingPastDatePolicy.rejectIfDayBeforeToday(LocalDate.of(2026, 4, 9), SP, clock))
                .isNotNull()
                .contains("já passou")
                .contains("2026-04-10");
    }

    @Test
    void allowsTodayAndFutureDays() {
        Clock clock = Clock.fixed(ZonedDateTime.of(2026, 4, 10, 12, 0, 0, 0, SP).toInstant(), SP);
        assertThat(SchedulingPastDatePolicy.rejectIfDayBeforeToday(LocalDate.of(2026, 4, 10), SP, clock))
                .isNull();
        assertThat(SchedulingPastDatePolicy.rejectIfDayBeforeToday(LocalDate.of(2026, 4, 13), SP, clock))
                .isNull();
    }
}
