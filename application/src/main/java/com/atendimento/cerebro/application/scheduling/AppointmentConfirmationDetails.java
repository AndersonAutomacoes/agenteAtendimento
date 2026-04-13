package com.atendimento.cerebro.application.scheduling;

import java.time.LocalDate;

/**
 * Dados do último {@code create_appointment} bem-sucedido, para gerar o card de confirmação no WhatsApp.
 */
public record AppointmentConfirmationDetails(
        String serviceName, String clientDisplayName, LocalDate date, String timeHhMm) {}
