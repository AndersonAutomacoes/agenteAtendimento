package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AssistantOutputSanitizerTest {

    @Test
    void stripSquareBracketSegments_removesTags() {
        assertThat(AssistantOutputSanitizer.stripSquareBracketSegments("Olá [slot_date:2026-04-14] mundo"))
                .isEqualTo("Olá mundo");
    }

    @Test
    void stripSquareBracketSegments_repeatsUntilStable() {
        assertThat(AssistantOutputSanitizer.stripSquareBracketSegments("a [x] [y] b")).isEqualTo("a b");
    }

    @Test
    void stripSquareBracketSegments_removesSlotOptionsAppendix() {
        assertThat(
                        AssistantOutputSanitizer.stripSquareBracketSegments(
                                "Lista [slot_options:09:00,10:00,11:00] fim"))
                .isEqualTo("Lista fim");
    }

    @Test
    void stripSquareBracketSegments_removesCancelOptionMapForWhatsAppCustomer() {
        String in =
                "Agendamentos AGENDADO:\n1) X — 01/01/2026 10:00\n[cancel_option_map:1=42][slot_date:2026-04-15]";
        assertThat(AssistantOutputSanitizer.stripSquareBracketSegments(in))
                .doesNotContain("cancel_option_map")
                .doesNotContain("[slot_date:")
                .contains("Agendamentos AGENDADO");
    }

    @Test
    void stripInternalAgentDirectivesForCustomer_removesExpandedCreateAppointmentInstruction() {
        String leaked =
                "Ótimo! O horário 16:30 do dia 16/04/2026 está disponível para O cliente confirmou o agendamento. "
                        + "Chame create_appointment com date=2026-04-16 e time=16:30. Posso confirmar o agendamento?";
        assertThat(AssistantOutputSanitizer.stripInternalAgentDirectivesForCustomer(leaked))
                .doesNotContain("Chame create_appointment")
                .doesNotContain("O cliente confirmou o agendamento")
                .contains("16:30")
                .contains("Posso confirmar");
    }

    @Test
    void stripInternalAgentDirectivesForCustomer_removesUseEstaDataEmCreateAppointment() {
        String s =
                "Confirmo o horário 10:00 para o dia 2026-04-16. Use esta data (yyyy-MM-DD: 2026-04-16) em create_appointment.";
        assertThat(AssistantOutputSanitizer.stripInternalAgentDirectivesForCustomer(s))
                .isEqualTo("Confirmo o horário 10:00 para o dia 2026-04-16");
    }

    @Test
    void stripGoogleCalendarUrlsFromCustomerMessage_removesEventLinks() {
        String raw =
                "Agendamento criado. Link: https://www.google.com/calendar/event?eid=abc "
                        + "Extra https://calendar.google.com/calendar/embed?src=x";
        assertThat(AssistantOutputSanitizer.stripGoogleCalendarUrlsFromCustomerMessage(raw))
                .doesNotContain("google.com/calendar")
                .doesNotContain("calendar.google.com");
    }
}

