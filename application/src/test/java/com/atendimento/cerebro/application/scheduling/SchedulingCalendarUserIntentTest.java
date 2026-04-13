package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SchedulingCalendarUserIntentTest {

    private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");

    @Test
    void tomorrow_usesZoneClock() {
        LocalDate t = SchedulingCalendarUserIntent.tomorrow(Z);
        assertThat(t).isEqualTo(LocalDate.now(Z).plusDays(1));
    }

    @Test
    void expectedDayIfTomorrowMentioned_detects() {
        assertThat(SchedulingCalendarUserIntent.expectedDayIfTomorrowMentioned("Quero agendar para amanha", Z))
                .contains(SchedulingCalendarUserIntent.tomorrow(Z));
        assertThat(SchedulingCalendarUserIntent.expectedDayIfTomorrowMentioned("dia 14/04", Z)).isEmpty();
    }

    @Test
    void lastBrazilianDateInText_parsesLast() {
        assertThat(SchedulingCalendarUserIntent.lastBrazilianDateInText("ver 10/05 e depois 14/04/2026", 2026))
                .contains(LocalDate.of(2026, 4, 14));
    }

    @Test
    void availabilityLineMatchesRequestedDate() {
        String line = "Calendário (simulado): x. Horários livres em 2026-04-14 (America/Sao_Paulo): 09:00, 10:00";
        assertThat(SchedulingCalendarUserIntent.availabilityLineMatchesRequestedDate(line, LocalDate.of(2026, 4, 14)))
                .isTrue();
        assertThat(SchedulingCalendarUserIntent.availabilityLineMatchesRequestedDate(line, LocalDate.of(2026, 4, 17)))
                .isFalse();
    }
}
