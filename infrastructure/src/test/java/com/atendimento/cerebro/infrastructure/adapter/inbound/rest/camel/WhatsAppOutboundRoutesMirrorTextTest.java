package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.scheduling.SchedulingSlotCapture;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class WhatsAppOutboundRoutesMirrorTextTest {

    @Test
    void premiumList_matchesExpectedLayout() {
        ZoneId z = ZoneId.of("America/Sao_Paulo");
        LocalDate d = LocalDate.of(2026, 4, 13);
        String t =
                SchedulingSlotCapture.buildPremiumFormattedSlotList(
                        d, z, List.of("09:00", "10:30", "14:00", "16:30"));
        assertThat(t)
                .contains("13/04")
                .contains("1) 09:00")
                .contains("2) 10:30")
                .contains("3) 14:00")
                .contains("4) 16:30");
    }
}
