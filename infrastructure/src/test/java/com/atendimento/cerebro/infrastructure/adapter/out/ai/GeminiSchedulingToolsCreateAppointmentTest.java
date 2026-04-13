package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.scheduling.SchedulingEnforcedChoice;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeminiSchedulingToolsCreateAppointmentTest {

    private static final TenantId TID = new TenantId("t1");

    @Test
    void createAppointment_overridesModelDateTime_whenBackendResolvedChoicePresent() {
        String[] captured = new String[2];
        AppointmentSchedulingPort port =
                new AppointmentSchedulingPort() {
                    @Override
                    public String checkAvailability(TenantId tenantId, String isoDate) {
                        return "";
                    }

                    @Override
                    public String createAppointment(
                            TenantId tenantId,
                            String isoDate,
                            String localTime,
                            String clientName,
                            String serviceName,
                            String conversationId) {
                        captured[0] = isoDate;
                        captured[1] = localTime;
                        return "created";
                    }
                };
        GeminiSchedulingTools tools =
                new GeminiSchedulingTools(
                        TID,
                        "conv-1",
                        port,
                        ZoneId.of("America/Sao_Paulo"),
                        "Confirmo 15:30.",
                        "",
                        Optional.of(new SchedulingEnforcedChoice(LocalDate.of(2026, 4, 14), "15:30")),
                        false);
        String out = tools.create_appointment("2026-04-15", "15:00", "Cliente", "Alinhamento");
        assertThat(out).isEqualTo("created");
        assertThat(captured[0]).isEqualTo("2026-04-14");
        assertThat(captured[1]).isEqualTo("15:30");
    }
}
