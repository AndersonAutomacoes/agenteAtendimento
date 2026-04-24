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

    /**
     * Texto de mensagem do assistente (pode incluir card, várias linhas) indica agendamento já concluído com sucesso.
     * Usado para invalidar {@code [slot_options:…]} de turnos anteriores.
     */
    public static boolean historyTextIndicatesSuccessfulBooking(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (text.contains(AppointmentConfirmationCardFormatter.CONFIRMATION_CARD_HEADLINE)) {
            return true;
        }
        if (text.contains("O horário foi registado na agenda da oficina.")) {
            return true;
        }
        if (text.contains("Confirmação Realizada!")) {
            return true;
        }
        for (String line : text.split("\n")) {
            String s = line.strip();
            if (s.isEmpty()) {
                continue;
            }
            if (isSuccess(s)) {
                return true;
            }
        }
        return false;
    }
}
