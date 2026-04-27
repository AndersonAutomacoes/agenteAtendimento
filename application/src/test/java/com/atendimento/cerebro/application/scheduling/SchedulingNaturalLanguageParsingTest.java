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
        assertThat(SchedulingExplicitTimeShortcut.parseTimeHhMmFromUserText("12 horas da manhã"))
                .contains("12:00");
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

    @Test
    void lastBrazilianDateInText_supportsColloquialDayMonthYear() {
        assertThat(SchedulingCalendarUserIntent.lastBrazilianDateInText("27 do 4 de 2026", 2026))
                .hasValueSatisfying(
                        d -> {
                            assertThat(d.getYear()).isEqualTo(2026);
                            assertThat(d.getMonthValue()).isEqualTo(4);
                            assertThat(d.getDayOfMonth()).isEqualTo(27);
                        });
    }

    @Test
    void parseExplicitDateAndTime_supportsLeadingListIdWithColloquialDate() {
        assertThat(
                        SchedulingExplicitTimeShortcut.tryParseExplicitDateAndTimeInUserText(
                                "66, 27 do 4 de 2026, 12 horas", ZONE))
                .isPresent()
                .get()
                .satisfies(
                        c -> {
                            assertThat(c.timeHhMm()).isEqualTo("12:00");
                            assertThat(c.date().getDayOfMonth()).isEqualTo(27);
                            assertThat(c.date().getMonthValue()).isEqualTo(4);
                        });
    }

    @Test
    void rescheduleIdExtractor_parsesVerbsAndLeadingComma() {
        assertThat(SchedulingRescheduleIdExtractor.extractFromUserText("reagendar o 66, amanhã 10:00", ZONE))
                .hasValue(66L);
        assertThat(
                        SchedulingRescheduleIdExtractor.extractFromUserText(
                                "Reagende o atendimento 69, 27 do 4 de 2026, 12 horas.", ZONE))
                .hasValue(69L);
        assertThat(
                        SchedulingRescheduleIdExtractor.extractFromUserText(
                                "Reagende o atendimento 7027 do 4 de 2026, 12 horas.", ZONE))
                .hasValue(70L);
        assertThat(
                        SchedulingExplicitTimeShortcut.tryParseExplicitDateAndTimeInUserText(
                                "70, 27, 4, 2026, 12 horas", ZONE))
                .isPresent()
                .get()
                .satisfies(
                        c -> {
                            assertThat(c.timeHhMm()).isEqualTo("12:00");
                            assertThat(c.date().getDayOfMonth()).isEqualTo(27);
                        });
        assertThat(SchedulingRescheduleIdExtractor.extractFromUserText("66, 27 do 4 de 2026, 12 horas", ZONE))
                .hasValue(66L);
        assertThat(SchedulingRescheduleIdExtractor.extractFromUserText("só 66, sem data", ZONE)).isEmpty();
        assertThat(
                        SchedulingRescheduleIdExtractor.extractFromUserText(
                                "Por favor agende amanhã troca de pastilhas às 11 horas da manhã", ZONE))
                .isEmpty();
    }
}
