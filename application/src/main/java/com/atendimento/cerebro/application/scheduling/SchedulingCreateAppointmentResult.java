package com.atendimento.cerebro.application.scheduling;

/**
 * Interpreta o texto devolvido por {@link com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort#createAppointment}
 * para saber se o agendamento foi persistido com sucesso.
 */
public final class SchedulingCreateAppointmentResult {

    private SchedulingCreateAppointmentResult() {}

    public static boolean isSuccess(String toolReturn) {
        if (toolReturn == null) {
            return false;
        }
        String t = toolReturn.strip();
        // Mock/local: "Agendamento criado (simulado)…"; Google Calendar: "Agendamento confirmado para …"
        return t.startsWith("Agendamento criado") || t.startsWith("Agendamento confirmado");
    }
}
