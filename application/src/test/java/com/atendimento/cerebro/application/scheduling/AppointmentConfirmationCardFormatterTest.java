package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AppointmentConfirmationCardFormatterTest {

    @Test
    void formatConfirmationCard_includesBoldMarkersAndWeekdayPt() {
        String card =
                AppointmentConfirmationCardFormatter.formatConfirmationCard(
                        42L,
                        "Alinhamento 3D",
                        "Sr. Anderson",
                        LocalDate.of(2026, 4, 14),
                        "15:30",
                        "Oficina AxeZap - Salvador, BA",
                        "https://maps.app.goo.gl/oficina");
        assertThat(card)
                .contains("*Agendamento confirmado* *#42*")
                .contains("Olá, *Sr. Anderson*!")
                .contains("Alinhamento 3D")
                .contains("Terça-feira")
                .contains("14/04/2026")
                .contains("15:30")
                .contains("Oficina AxeZap")
                .contains("*Como chegar:* https://maps.app.goo.gl/oficina")
                .contains("alterar ou cancelar");
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
                        7L,
                        "troca de óleo",
                        "Anderson",
                        LocalDate.of(2026, 4, 16),
                        "17:00",
                        "Oficina X",
                        "");
        String doubleCard = card + "\n\n" + card;
        assertThat(AppointmentConfirmationCardFormatter.stripFormattedConfirmationCards(doubleCard)).isEmpty();
    }

    @Test
    void stripEchoOfSchedulingCreateToolReturn_removesGoogleSuccessLines() {
        String raw =
                "Perfeito.\n"
                        + "Agendamento confirmado para 24/04/2026 às 11:00. O horário foi registado na agenda da oficina.";
        assertThat(AppointmentConfirmationCardFormatter.stripEchoOfSchedulingCreateToolReturn(raw))
                .isEqualTo("Perfeito.");
    }

    @Test
    void stripFormattedConfirmationCards_keepsIntroBeforeCard() {
        String card =
                AppointmentConfirmationCardFormatter.formatConfirmationCard(
                        1L,
                        "Serviço",
                        "Cliente",
                        LocalDate.of(2026, 4, 16),
                        "10:00",
                        "Local",
                        "");
        String combined = "OK. Agendamento criado.\n\n" + card;
        assertThat(AppointmentConfirmationCardFormatter.stripFormattedConfirmationCards(combined))
                .isEqualTo("OK. Agendamento criado.");
    }
}
