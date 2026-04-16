package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CancelOptionMapTest {

    @Test
    void buildAppendix_roundTrip() {
        String a = CancelOptionMap.buildAppendix(Map.of(1, 42L, 2, 99L));
        assertThat(a).isEqualTo("[cancel_option_map:1=42,2=99]");
        assertThat(CancelOptionMap.parseLastFromText("prefix " + a + " tail")).containsExactlyInAnyOrderEntriesOf(Map.of(1, 42L, 2, 99L));
    }

    @Test
    void resolve_mapsSoloDigitAndOpcao() {
        String blob = "Agendamentos\n[cancel_option_map:1=7,2=8]";
        assertThat(CancelOptionMap.resolveAppointmentIdForCancel("1", blob)).isEqualTo("7");
        assertThat(CancelOptionMap.resolveAppointmentIdForCancel("2", blob)).isEqualTo("8");
        assertThat(CancelOptionMap.resolveAppointmentIdForCancel("opção 1", blob)).isEqualTo("7");
        assertThat(CancelOptionMap.resolveAppointmentIdForCancel("opcao 2", blob)).isEqualTo("8");
    }

    @Test
    void resolve_passesThroughWhenNoMap() {
        assertThat(CancelOptionMap.resolveAppointmentIdForCancel("42", "sem mapa")).isEqualTo("42");
    }

    @Test
    void resolve_directIdWhenNotOptionKey() {
        String blob = "[cancel_option_map:1=100]";
        assertThat(CancelOptionMap.resolveAppointmentIdForCancel("100", blob)).isEqualTo("100");
    }

    @Test
    void resolve_fallsBackToSchedulingCancelSessionWhenBlobHasNoMap() {
        try {
            SchedulingCancelSessionCapture.setOptionToAppointmentId(Map.of(1, 55L));
            assertThat(CancelOptionMap.resolveAppointmentIdForCancel("1", "sem apêndice")).isEqualTo("55");
        } finally {
            SchedulingCancelSessionCapture.clear();
        }
    }
}
