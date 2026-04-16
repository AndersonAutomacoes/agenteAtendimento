package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SchedulingCreateAppointmentResultTest {

    @Test
    void isSuccess_trueForMockStyleMessage() {
        assertThat(
                        SchedulingCreateAppointmentResult.isSuccess(
                                "Agendamento criado (simulado) no calendário cal-1 para 16/04/2026 17:30."))
                .isTrue();
    }

    @Test
    void isSuccess_trueForGoogleCalendarConfirmationMessage() {
        assertThat(
                        SchedulingCreateAppointmentResult.isSuccess(
                                "Agendamento confirmado para 16/04/2026 às 17:30. O horário foi registado na agenda da oficina."))
                .isTrue();
    }

    @Test
    void isSuccess_falseForErrorsAndNull() {
        assertThat(SchedulingCreateAppointmentResult.isSuccess(null)).isFalse();
        assertThat(SchedulingCreateAppointmentResult.isSuccess("Erro ao criar evento")).isFalse();
        assertThat(SchedulingCreateAppointmentResult.isSuccess("Não foi possível confirmar")).isFalse();
    }
}
