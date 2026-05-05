package com.atendimento.cerebro.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WhatsAppInteractiveReplyAppointmentPickTest {

    @Test
    void forAppointmentPickList_mapsCancelOptionAppendixToPickRows() {
        String blob = "*Agendamentos*\n\n1) *Revisão* — 06/05/2026 09:00\n\n[cancel_option_map:1=55]";
        var opt = WhatsAppInteractiveReply.forAppointmentPickListIfMapped(blob);
        assertThat(opt).isPresent();
        assertThat(opt.get().kind()).isEqualTo(WhatsAppInteractiveKind.APPOINTMENT_LIST);
        assertThat(opt.get().customRows()).hasSize(1);
        assertThat(opt.get().customRows().get(0).rowId()).isEqualTo("pick_appt_55");
        assertThat(opt.get().customRows().get(0).title()).isEqualTo("Revisão");
        assertThat(opt.get().customRows().get(0).description()).isEqualTo("06/05/2026 09:00");
    }

    @Test
    void forAppointmentActions_buildsRescheduleAndCancelButtons() {
        var r = WhatsAppInteractiveReply.forAppointmentActions(12L);
        assertThat(r.kind()).isEqualTo(WhatsAppInteractiveKind.APPOINTMENT_ACTION);
        assertThat(r.customRows()).hasSize(2);
        assertThat(r.customRows().get(0).rowId()).isEqualTo("appt_reschedule_12");
        assertThat(r.customRows().get(1).rowId()).isEqualTo("appt_cancel_12");
    }
}
