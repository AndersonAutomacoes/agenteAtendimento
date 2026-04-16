package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.service.ChatService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchedulingToolContextTest {

    @Test
    void resetContext_clearsCancelSelectionMapAndWaitingFlag() {
        SchedulingCancelSessionCapture.setOptionToAppointmentId(Map.of(1, 99L));
        SchedulingCancelSessionCapture.setWaitingForCancellationChoice(true);
        assertThat(SchedulingCancelSessionCapture.getSelectionMap()).containsEntry(1, 99L);
        assertThat(SchedulingCancelSessionCapture.isWaitingForCancellationChoice()).isTrue();

        ChatService.resetContext();

        assertThat(SchedulingCancelSessionCapture.getSelectionMap()).isEmpty();
        assertThat(SchedulingCancelSessionCapture.isWaitingForCancellationChoice()).isFalse();
    }
}
