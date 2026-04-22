package com.atendimento.cerebro.application.scheduling;

/**
 * Resultado de {@link com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort#createAppointment};
 * quando o agendamento é persistido, inclui o {@code id} da linha em {@code tenant_appointments}.
 */
public record CreateAppointmentResult(String message, Long appointmentDatabaseId) {

    public boolean isSuccess() {
        return message != null && SchedulingCreateAppointmentResult.isSuccess(message);
    }

    public static CreateAppointmentResult failure(String message) {
        return new CreateAppointmentResult(message, null);
    }

    public static CreateAppointmentResult success(String message, long appointmentDatabaseId) {
        return new CreateAppointmentResult(message, appointmentDatabaseId);
    }
}
