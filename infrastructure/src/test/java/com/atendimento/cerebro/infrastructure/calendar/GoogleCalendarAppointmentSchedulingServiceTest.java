package com.atendimento.cerebro.infrastructure.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class GoogleCalendarAppointmentSchedulingServiceTest {

    @Test
    void formatCalendarEventSummary_usesUppercaseServiceTimeClientPattern() {
        String summary =
                GoogleCalendarAppointmentSchedulingService.formatCalendarEventSummary(
                        "Troca de Pastilhas", LocalTime.of(9, 0), "Anderson Nunes");

        assertThat(summary).isEqualTo("TROCA DE PASTILHAS - 09:00 - ANDERSON NUNES");
    }

    @Test
    void formatCalendarEventDescription_includesWhatsAppLinkWhenConversationHasWaPrefix() {
        String description =
                GoogleCalendarAppointmentSchedulingService.formatCalendarEventDescription("wa-5511999887766");

        assertThat(description)
                .isEqualTo(
                        "Agendamento via agente de atendimento AxeZap.\nWhatsApp cliente: https://wa.me/5511999887766");
    }

    @Test
    void formatCalendarEventDescription_keepsDefaultTextWhenNoWhatsAppDigits() {
        String description =
                GoogleCalendarAppointmentSchedulingService.formatCalendarEventDescription("portal-abc");

        assertThat(description).isEqualTo("Agendamento via agente de atendimento AxeZap.");
    }
}
