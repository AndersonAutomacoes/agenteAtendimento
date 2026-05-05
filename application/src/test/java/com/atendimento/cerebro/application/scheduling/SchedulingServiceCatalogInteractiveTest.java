package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.dto.WhatsAppInteractiveKind;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulingServiceCatalogInteractiveTest {

    @Test
    void buildsInteractiveFromServiceOptionMap() {
        String content =
                "Serviços disponíveis para agendamento:\n1) A\n2) B\n\n[service_option_map:1=A|2=B]";
        var opt = SchedulingServiceCatalogInteractive.fromAssistantText(content);
        assertThat(opt).isPresent();
        assertThat(opt.get().kind()).isEqualTo(WhatsAppInteractiveKind.SERVICES);
        assertThat(opt.get().customRows()).hasSize(2);
        assertThat(opt.get().customRows().get(0).rowId()).isEqualTo("service_1");
        assertThat(opt.get().customRows().get(0).title()).isEqualTo("A");
        assertThat(opt.get().customRows().get(1).rowId()).isEqualTo("service_2");
    }

    @Test
    void merge_prefersSlotsWhenBothPresent() {
        SchedulingSlotCapture.setStructuredAvailability(
                "msg", List.of("10:00", "10:30"), LocalDate.now(ZoneId.of("America/Bahia")));
        String content = "text\n\n[service_option_map:1=X|2=Y]";
        var merged =
                SchedulingServiceCatalogInteractive.mergeWithSlotInteractive(content, ZoneId.of("America/Bahia"));
        assertThat(merged).isPresent();
        assertThat(merged.get().kind()).isEqualTo(WhatsAppInteractiveKind.SLOTS);
        assertThat(merged.get().slotTimes()).isNotEmpty();
    }

    @Test
    void merge_includesAppointmentPickListWhenCancelMapPresent() {
        String content =
                "*Agendamentos*\n\n1) *Serviço* — 06/05/2026 09:00\n\n[cancel_option_map:1=55]";
        var merged =
                SchedulingServiceCatalogInteractive.mergeWithSlotInteractive(content, ZoneId.of("America/Bahia"));
        assertThat(merged).isPresent();
        assertThat(merged.get().kind()).isEqualTo(WhatsAppInteractiveKind.APPOINTMENT_LIST);
        assertThat(merged.get().customRows()).anyMatch(r -> r.rowId().equals("pick_appt_55"));
    }
}
