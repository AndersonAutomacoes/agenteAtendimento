package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AppointmentConfirmationCardFormatterTest {

    @Test
    void formatConfirmationCard_includesBoldMarkersAndWeekdayPt() {
        String card =
                AppointmentConfirmationCardFormatter.formatConfirmationCard(
                        "Alinhamento 3D",
                        "Sr. Anderson",
                        LocalDate.of(2026, 4, 14),
                        "15:30",
                        "Oficina InteliZap - Salvador, BA");
        assertThat(card)
                .contains("*✅ AGENDAMENTO CONFIRMADO*")
                .contains("Alinhamento 3D")
                .contains("Sr. Anderson")
                .contains("Terça-feira")
                .contains("14/04/2026")
                .contains("15:30")
                .contains("Oficina InteliZap")
                .contains("Chegue 5 minutos");
    }

    @Test
    void formatMapsFollowUp_emptyWhenNoUrl() {
        assertThat(AppointmentConfirmationCardFormatter.formatMapsFollowUp("").isBlank()).isTrue();
    }

    @Test
    void formatMapsFollowUp_includesLink() {
        assertThat(AppointmentConfirmationCardFormatter.formatMapsFollowUp("https://maps.app.goo.gl/abc"))
                .contains("https://maps.app.goo.gl/abc")
                .contains("*Como chegar:*");
    }

    @Test
    void stripFormattedConfirmationCards_removesDuplicateBlocks() {
        String card =
                AppointmentConfirmationCardFormatter.formatConfirmationCard(
                        "troca de óleo",
                        "Anderson",
                        LocalDate.of(2026, 4, 16),
                        "17:00",
                        "Oficina X");
        String doubleCard = card + "\n\n" + card;
        assertThat(AppointmentConfirmationCardFormatter.stripFormattedConfirmationCards(doubleCard)).isEmpty();
    }

    @Test
    void stripFormattedConfirmationCards_keepsIntroBeforeCard() {
        String card =
                AppointmentConfirmationCardFormatter.formatConfirmationCard(
                        "Serviço",
                        "Cliente",
                        LocalDate.of(2026, 4, 16),
                        "10:00",
                        "Local");
        String combined = "OK. Agendamento criado.\n\n" + card;
        assertThat(AppointmentConfirmationCardFormatter.stripFormattedConfirmationCards(combined))
                .isEqualTo("OK. Agendamento criado.");
    }
}
