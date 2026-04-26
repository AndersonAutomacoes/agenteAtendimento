package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SchedulingNaturalLanguageParsingTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Test
    void parseTimeHhMmFromUserText_supportsSpokenHour() {
        assertThat(SchedulingExplicitTimeShortcut.parseTimeHhMmFromUserText("10 horas da manhã"))
                .contains("10:00");
        assertThat(SchedulingExplicitTimeShortcut.parseTimeHhMmFromUserText("11 horas"))
                .contains("11:00");
    }

    @Test
    void parseExplicitDateAndTime_supportsWeekdayAndSpokenHour() {
        assertThat(
                        SchedulingExplicitTimeShortcut.tryParseExplicitDateAndTimeInUserText(
                                "Quero agendar revisão de freios para quinta-feira às 11 horas", ZONE))
                .isPresent()
                .get()
                .satisfies(
                        c -> {
                            assertThat(c.timeHhMm()).isEqualTo("11:00");
                            assertThat(c.date().getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
                        });
    }
}
