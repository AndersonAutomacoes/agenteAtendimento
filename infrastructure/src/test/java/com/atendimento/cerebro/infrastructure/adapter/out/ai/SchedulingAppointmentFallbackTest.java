package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.scheduling.CreateAppointmentResult;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SchedulingAppointmentFallbackTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Test
    void lastDateInTranscript_picksLatestOccurrenceInText() {
        String blob = "Falamos de 2026-04-08 e depois 13/04/2026.";
        assertThat(SchedulingAppointmentFallback.lastDateInTranscript(blob, ZONE))
                .contains(LocalDate.of(2026, 4, 13));
    }

    @Test
    void lastDateInTranscript_isoAfterBr_prefersLaterEndIndex() {
        String blob = "dia 14/04/2026 à tarde, confirmado em 2026-04-15.";
        assertThat(SchedulingAppointmentFallback.lastDateInTranscript(blob, ZONE))
                .contains(LocalDate.of(2026, 4, 15));
    }

    @Test
    void lastDateInTranscript_parsesPortugueseSpokenDate() {
        String blob = "Para sábado, 18 de abril de 2026, aditivo de radiador.";
        assertThat(SchedulingAppointmentFallback.lastDateInTranscript(blob, ZONE))
                .contains(LocalDate.of(2026, 4, 18));
    }

    @Test
    void lastTimeInTranscript_picksLastClockTime() {
        String blob = "08:00 não serve, prefiro 14:30.";
        assertThat(SchedulingAppointmentFallback.lastTimeInTranscript(blob)).contains(LocalTime.of(14, 30));
    }

    @Test
    void tryPersist_doesNotCallPort() {
        AppointmentSchedulingPort port =
                new AppointmentSchedulingPort() {
                    @Override
                    public String checkAvailability(TenantId tenantId, String isoDate) {
                        return "";
                    }

                    @Override
                    public CreateAppointmentResult createAppointment(
                            TenantId tenantId,
                            String isoDate,
                            String localTime,
                            String clientName,
                            String serviceName,
                            String conversationId) {
                        throw new AssertionError("persistência fallback desactivada");
                    }

                    @Override
                    public boolean deleteCalendarEvent(TenantId tenantId, String googleEventId) {
                        return true;
                    }
                };

        AICompletionRequest req =
                new AICompletionRequest(
                        new TenantId("t1"),
                        List.of(
                                Message.userMessage("Quero revisão dia 16/04/2026"),
                                Message.assistantMessage("Posso às 09:00 ou 11:00?")),
                        List.of(),
                        "confirmado, 11:00 então",
                        "",
                        AiChatProvider.GEMINI);

        assertThat(SchedulingAppointmentFallback.tryPersist(req, port, ZONE)).isEmpty();
    }

    @Test
    void tryPersist_emptyWhenNoSchedulingIntent() {
        AppointmentSchedulingPort port =
                new AppointmentSchedulingPort() {
                    @Override
                    public String checkAvailability(TenantId tenantId, String isoDate) {
                        return "";
                    }

                    @Override
                    public CreateAppointmentResult createAppointment(
                            TenantId tenantId,
                            String isoDate,
                            String localTime,
                            String clientName,
                            String serviceName,
                            String conversationId) {
                        throw new AssertionError("should not call");
                    }

                    @Override
                    public boolean deleteCalendarEvent(TenantId tenantId, String googleEventId) {
                        return true;
                    }
                };

        AICompletionRequest req =
                new AICompletionRequest(
                        new TenantId("t1"), List.of(), List.of(), "oi, tudo bem?", "", AiChatProvider.GEMINI);

        assertThat(SchedulingAppointmentFallback.tryPersist(req, port, ZONE)).isEmpty();
    }
}
