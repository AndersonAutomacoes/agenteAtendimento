package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulingSlotCaptureTest {

    @Test
    void setSlotsFromToolResult_parsesAvailabilityLine() {
        SchedulingSlotCapture.clear();
        LocalDate day = LocalDate.of(2026, 4, 15);
        SchedulingSlotCapture.setSlotsFromToolResult(
                "Calendário (simulado): x. Horários livres em 2026-04-15 (UTC): 09:00, 10:00, 11:30", day);
        assertThat(SchedulingSlotCapture.peekSlotTimes()).containsExactly("09:00", "10:00", "11:30");
        ZoneId utc = ZoneId.of("UTC");
        var reply = SchedulingSlotCapture.takeWhatsAppInteractive("Olá", utc).orElseThrow();
        assertThat(reply.slotTimes()).containsExactly("09:00", "10:00", "11:30");
        assertThat(reply.title()).contains("Escolha um horário");
        assertThat(reply.verificationText()).isEmpty();
        assertThat(SchedulingSlotCapture.peekSlotTimes()).isEmpty();
    }

    @Test
    void parseSlotTimes_mockAppointmentStyleLine_extractsCommaSeparated() {
        String line =
                "Calendário (simulado): local-cal. Horários livres em 2026-04-13 (America/Sao_Paulo): 08:00, 09:00, 10:00";
        assertThat(SchedulingSlotCapture.parseSlotTimesFromAvailabilityLine(line))
                .containsExactly("08:00", "09:00", "10:00");
    }

    @Test
    void parseSlotTimes_whenNenhumHorario_returnsEmpty() {
        String line =
                "Calendário (simulado): x. Horários livres em 2026-04-13 (UTC): (nenhum horário livre neste dia com as regras atuais)";
        assertThat(SchedulingSlotCapture.parseSlotTimesFromAvailabilityLine(line)).isEmpty();
    }

    @Test
    void normalizeSlotTimes_trimsAndDropsBlankAndDedupes() {
        assertThat(
                        SchedulingSlotCapture.normalizeSlotTimes(
                                List.of(" 09:00 ", "\n10:30\u00a0", "", "  ", "10:30", "9:15")))
                .containsExactly("09:00", "10:30", "09:15");
    }

    @Test
    void formatNumberedSlotLines_usesEmojiPrefixes() {
        String lines = SchedulingSlotCapture.formatNumberedSlotLines(List.of("09:00", "10:30"));
        assertThat(lines).contains("09:00").contains("10:30").contains("1️⃣").contains("2️⃣");
    }

    @Test
    void numberedOptionPrefix_usesKeycapDigitsAfterTen() {
        assertThat(SchedulingSlotCapture.numberedOptionPrefix(11).strip()).isEqualTo("1️⃣1️⃣");
        assertThat(SchedulingSlotCapture.numberedOptionPrefix(12).strip()).isEqualTo("1️⃣2️⃣");
        assertThat(SchedulingSlotCapture.numberedOptionPrefix(20).strip()).isEqualTo("2️⃣0️⃣");
        assertThat(SchedulingSlotCapture.numberedOptionPrefix(21).strip()).isEqualTo("2️⃣1️⃣");
    }

    @Test
    void buildPremiumFormattedSlotList_includesHeaderAndFooter() {
        ZoneId z = ZoneId.of("America/Sao_Paulo");
        LocalDate d = LocalDate.of(2026, 4, 13);
        String text =
                SchedulingSlotCapture.buildPremiumFormattedSlotList(d, z, List.of("09:00", "10:30"));
        assertThat(text).contains("📅").contains("09:00").contains(SchedulingSlotCapture.SLOT_LIST_FOOTER_PT);
    }

    @Test
    void setSlotsFromToolResult_ignoresWithoutHorariosLivres() {
        SchedulingSlotCapture.clear();
        SchedulingSlotCapture.setSlotsFromToolResult("09:00, 10:00", LocalDate.of(2026, 4, 15));
        assertThat(SchedulingSlotCapture.peekSlotTimes()).isEmpty();
    }
}
