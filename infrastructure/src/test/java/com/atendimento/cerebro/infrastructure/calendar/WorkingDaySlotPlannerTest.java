package com.atendimento.cerebro.infrastructure.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkingDaySlotPlannerTest {

    @Test
    void freeSlotStarts_withoutBusy_fillsWorkday() {
        LocalDate day = LocalDate.of(2025, 6, 2);
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        var slots = WorkingDaySlotPlanner.freeSlotStarts(day, zone, 9, 18, 30, List.of());
        assertThat(slots).hasSize(18);
        assertThat(slots.getFirst().getHour()).isEqualTo(9);
        assertThat(slots.getLast().getHour()).isEqualTo(17);
        assertThat(slots.getLast().getMinute()).isEqualTo(30);
    }
}
