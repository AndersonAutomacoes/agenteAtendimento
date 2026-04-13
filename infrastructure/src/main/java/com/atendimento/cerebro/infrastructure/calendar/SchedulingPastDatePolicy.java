package com.atendimento.cerebro.infrastructure.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Rejeita agendamentos em dias civis estritamente anteriores a "hoje" no fuso do calendário
 * ({@code cerebro.google.calendar.zone}).
 */
public final class SchedulingPastDatePolicy {

    private SchedulingPastDatePolicy() {}

    public static String rejectIfDayBeforeToday(LocalDate day, ZoneId zone) {
        return rejectIfDayBeforeToday(day, zone, Clock.system(zone));
    }

    /**
     * @return mensagem de erro para o modelo, ou {@code null} se a data é hoje ou futura (dia civil no fuso).
     */
    public static String rejectIfDayBeforeToday(LocalDate day, ZoneId zone, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (day.isBefore(today)) {
            return "A data "
                    + day
                    + " já passou em relação a hoje ("
                    + today
                    + " no fuso "
                    + zone.getId()
                    + "). Escolha uma data igual ou posterior a hoje, com tom cordial para o cliente.";
        }
        return null;
    }
}
