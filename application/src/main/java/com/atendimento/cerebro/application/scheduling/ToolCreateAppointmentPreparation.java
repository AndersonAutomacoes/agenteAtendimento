package com.atendimento.cerebro.application.scheduling;

/**
 * Resultado da validação antes de {@code create_appointment} no Gemini — mensagem amigável para o modelo repassar ao
 * cliente se {@link #ok()} for falso.
 */
public record ToolCreateAppointmentPreparation(boolean ok, String messageForGemini, String dateIso, String timeHhMm) {

    public static ToolCreateAppointmentPreparation success(String dateIso, String timeHhMm) {
        return new ToolCreateAppointmentPreparation(true, null, dateIso, timeHhMm);
    }

    public static ToolCreateAppointmentPreparation failure(String messageForGemini) {
        return new ToolCreateAppointmentPreparation(false, messageForGemini, null, null);
    }
}
