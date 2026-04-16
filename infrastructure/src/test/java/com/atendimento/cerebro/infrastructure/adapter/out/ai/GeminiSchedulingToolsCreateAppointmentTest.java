package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.scheduling.SchedulingEnforcedChoice;
import com.atendimento.cerebro.application.service.AppointmentService;
import com.atendimento.cerebro.application.service.AppointmentValidationService;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiSchedulingToolsCreateAppointmentTest {

    private static final TenantId TID = new TenantId("t1");

    @Test
    void createAppointment_overridesModelDateTime_whenBackendResolvedChoicePresent() {
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate enforcedDay = LocalDate.now(zone).plusDays(1);
        LocalDate modelDay = enforcedDay.plusDays(1);
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

                    @Override
                    public boolean deleteCalendarEvent(TenantId tenantId, String googleEventId) {
                        return true;
                    }
                };
        GeminiSchedulingTools tools =
                new GeminiSchedulingTools(
                        TID,
                        "conv-1",
                        port,
                        new AppointmentValidationService(),
                        Mockito.mock(AppointmentService.class),
                        zone,
                        "Confirmo 15:30.",
                        "",
                        Optional.of(new SchedulingEnforcedChoice(enforcedDay, "15:30")),
                        false,
                        Optional.empty());
        String out = tools.create_appointment(modelDay.toString(), "15:00", "Cliente", "Alinhamento");
        assertThat(out).isEqualTo("created");
        assertThat(captured[0]).isEqualTo(enforcedDay.toString());
        assertThat(captured[1]).isEqualTo("15:30");
    }

    @Test
    void createAppointment_proceedsWhenTranscriptMentionsCancel_butBackendEnforcedChoicePresent() {
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate enforcedDay = LocalDate.now(zone).plusDays(1);
        boolean[] called = {false};
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
                        called[0] = true;
                        return "Agendamento criado (simulado) ok";
                    }

                    @Override
                    public boolean deleteCalendarEvent(TenantId tenantId, String googleEventId) {
                        return true;
                    }
                };
        GeminiSchedulingTools tools =
                new GeminiSchedulingTools(
                        TID,
                        "conv-1",
                        port,
                        new AppointmentValidationService(),
                        Mockito.mock(AppointmentService.class),
                        zone,
                        "sim",
                        "Quero cancelar o agendamento anterior\n",
                        Optional.of(new SchedulingEnforcedChoice(enforcedDay, "17:30")),
                        false,
                        Optional.empty());
        String out = tools.create_appointment(enforcedDay.toString(), "17:30", "Cliente", "Revisão");
        assertThat(called[0]).isTrue();
        assertThat(out).contains("Agendamento criado");
    }

    @Test
    void createAppointment_rejectsWhenModelDateDiffersFromSlotListDay() {
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate anchorDay = LocalDate.now(zone).plusDays(1);
        LocalDate modelDay = anchorDay.plusDays(1);
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
                        throw new AssertionError("não deve chamar create");
                    }

                    @Override
                    public boolean deleteCalendarEvent(TenantId tenantId, String googleEventId) {
                        return true;
                    }
                };
        GeminiSchedulingTools tools =
                new GeminiSchedulingTools(
                        TID,
                        "conv-1",
                        port,
                        new AppointmentValidationService(),
                        Mockito.mock(AppointmentService.class),
                        zone,
                        "sim",
                        "",
                        Optional.empty(),
                        false,
                        Optional.of(anchorDay));
        String out = tools.create_appointment(modelDay.toString(), "10:30", "Cliente", "Serviço");
        assertThat(out).contains(anchorDay.toString()).contains("não corresponde");
    }

    @Test
    void cancelAppointment_delegatesToAppointmentService() {
        AppointmentService appointmentService = Mockito.mock(AppointmentService.class);
        when(appointmentService.cancelAppointment(any(), any(), any(), any(), any())).thenReturn("delegated");
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
                        return "";
                    }

                    @Override
                    public boolean deleteCalendarEvent(TenantId tenantId, String googleEventId) {
                        return true;
                    }
                };
        GeminiSchedulingTools tools =
                new GeminiSchedulingTools(
                        TID,
                        "wa-5511",
                        port,
                        new AppointmentValidationService(),
                        appointmentService,
                        ZoneId.of("America/Sao_Paulo"),
                        "",
                        "",
                        Optional.empty(),
                        false,
                        Optional.empty());
        assertThat(tools.cancel_appointment("5511", "42")).isEqualTo("delegated");
        verify(appointmentService)
                .cancelAppointment(eq(TID), eq("wa-5511"), eq("5511"), eq("42"), eq(ZoneId.of("America/Sao_Paulo")));
    }

    @Test
    void getActiveAppointments_delegatesToAppointmentService() {
        AppointmentService appointmentService = Mockito.mock(AppointmentService.class);
        when(appointmentService.getActiveAppointments(any(), any(), any())).thenReturn("listed");
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
                        return "";
                    }

                    @Override
                    public boolean deleteCalendarEvent(TenantId tenantId, String googleEventId) {
                        return true;
                    }
                };
        GeminiSchedulingTools tools =
                new GeminiSchedulingTools(
                        TID,
                        "wa-5511",
                        port,
                        new AppointmentValidationService(),
                        appointmentService,
                        ZoneId.of("America/Sao_Paulo"),
                        "",
                        "",
                        Optional.empty(),
                        false,
                        Optional.empty());
        assertThat(tools.get_active_appointments()).isEqualTo("listed");
        verify(appointmentService).getActiveAppointments(TID, "wa-5511", ZoneId.of("America/Sao_Paulo"));
    }
}
