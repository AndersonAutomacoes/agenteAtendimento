package com.atendimento.cerebro.application.scheduling;

import java.time.LocalDate;
import java.time.LocalTime;

/** Resultado da validação de data/hora antes de criar o evento no calendário. */
public record AppointmentCalendarValidationResult(boolean valid, String userMessage, LocalDate day, LocalTime time) {

    public static AppointmentCalendarValidationResult ok(LocalDate day, LocalTime time) {
        return new AppointmentCalendarValidationResult(true, null, day, time);
    }

    public static AppointmentCalendarValidationResult invalid(String userMessage) {
        return new AppointmentCalendarValidationResult(false, userMessage, null, null);
    }
}
