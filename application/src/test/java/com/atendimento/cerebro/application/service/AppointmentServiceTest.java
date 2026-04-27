package com.atendimento.cerebro.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.dto.CrmCustomerRecord;
import com.atendimento.cerebro.application.dto.TenantAppointmentListItem;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.port.out.CrmCustomerQueryPort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentQueryPort;
import com.atendimento.cerebro.application.port.out.TenantServiceCatalogPort;
import com.atendimento.cerebro.application.event.AppointmentCancelledEvent;
import com.atendimento.cerebro.application.event.AppointmentConfirmedEvent;
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import com.atendimento.cerebro.application.scheduling.CreateAppointmentResult;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class AppointmentServiceTest {

    private static final TenantId TID = new TenantId("tenant-a");
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String ZONE_STR = ZONE.getId();

    private TenantAppointmentQueryPort query;
    private TenantAppointmentStorePort store;
    private AppointmentSchedulingPort scheduling;
    private CrmCustomerQueryPort crm;
    private TenantServiceCatalogPort tenantServiceCatalog;
    private ApplicationEventPublisher eventPublisher;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        query = Mockito.mock(TenantAppointmentQueryPort.class);
        store = Mockito.mock(TenantAppointmentStorePort.class);
        scheduling = Mockito.mock(AppointmentSchedulingPort.class);
        crm = Mockito.mock(CrmCustomerQueryPort.class);
        tenantServiceCatalog = Mockito.mock(TenantServiceCatalogPort.class);
        eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        service = new AppointmentService(query, store, scheduling, tenantServiceCatalog, crm, eventPublisher);
        when(tenantServiceCatalog.findServiceIdByName(any(), any())).thenReturn(Optional.of(1L));
        when(scheduling.deleteCalendarEvent(any(), any())).thenReturn(true);
        when(store.markCancelled(any(Long.class), any(Instant.class))).thenReturn(true);
    }

    @Test
    void cancelAppointment_succeedsWhenIdValid_contactOptionalOrMismatchDoesNotBlock() {
        TenantAppointmentListItem row = sampleRowAgendado("wa-5511888777666", "Serviço X", "google-ev-x", 42L);
        when(query.findByIdForTenantAndConversation(eq(TID), eq(42L), eq("wa-5511888777666"), eq(ZONE_STR)))
                .thenReturn(Optional.of(row));

        assertThat(service.cancelAppointment(TID, "wa-5511888777666", "", "42", ZONE)).isEmpty();
        verify(scheduling).deleteCalendarEvent(TID, "google-ev-x");
        verify(store).markCancelled(eq(42L), any(Instant.class));

        Mockito.reset(scheduling, store);
        when(scheduling.deleteCalendarEvent(any(), any())).thenReturn(true);
        when(store.markCancelled(any(Long.class), any(Instant.class))).thenReturn(true);
        when(query.findByIdForTenantAndConversation(eq(TID), eq(42L), eq("wa-5511888777666"), eq(ZONE_STR)))
                .thenReturn(Optional.of(row));
        assertThat(service.cancelAppointment(TID, "wa-5511888777666", "(11) 99999-0000", "42", ZONE)).isEmpty();
        verify(scheduling).deleteCalendarEvent(TID, "google-ev-x");
        verify(store).markCancelled(eq(42L), any(Instant.class));
    }

    @Test
    void cancelAppointment_onSuccess_publishesCancelledEvent() {
        TenantAppointmentListItem row = sampleRowAgendado("wa-5511999887766", "Serviço X", "google-ev-x", 42L);
        when(query.findByIdForTenantAndConversation(eq(TID), eq(42L), eq("wa-5511999887766"), eq(ZONE_STR)))
                .thenReturn(Optional.of(row));

        assertThat(service.cancelAppointment(TID, "wa-5511999887766", "", "42", ZONE)).isEmpty();

        ArgumentCaptor<AppointmentCancelledEvent> cap = ArgumentCaptor.forClass(AppointmentCancelledEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().appointmentId()).isEqualTo(42L);
        assertThat(cap.getValue().phoneNumber()).isEqualTo("5511999887766");
        assertThat(cap.getValue().calendarZoneId()).isEqualTo(ZONE.getId());
    }

    @Test
    void cancelAppointment_rejectsWhenIdNotFoundForConversation() {
        when(query.findByIdForTenantAndConversation(eq(TID), eq(42L), eq("wa-5511999887766"), eq(ZONE_STR)))
                .thenReturn(Optional.empty());

        String out = service.cancelAppointment(TID, "wa-5511999887766", "+55 11 99988-7766", "42", ZONE);

        assertThat(out).contains("Não encontrámos").contains("ID").contains("lista");
        verify(scheduling, never()).deleteCalendarEvent(any(), any());
        verify(store, never()).markCancelled(any(Long.class), any());
    }

    @Test
    void cancelAppointment_acceptsMatchingPhoneDigits() {
        TenantAppointmentListItem row = sampleRowAgendado("wa-5511999887766", "Alinhamento", "google-ev-1", 42L);
        when(query.findByIdForTenantAndConversation(eq(TID), eq(42L), eq("wa-5511999887766"), eq(ZONE_STR)))
                .thenReturn(Optional.of(row));

        String out =
                service.cancelAppointment(TID, "wa-5511999887766", "+55 11 99988-7766", "42", ZONE);

        assertThat(out).isEmpty();
        verify(scheduling).deleteCalendarEvent(TID, "google-ev-1");
        ArgumentCaptor<Long> idCap = ArgumentCaptor.forClass(Long.class);
        verify(store).markCancelled(idCap.capture(), any(Instant.class));
        assertThat(idCap.getValue()).isEqualTo(42L);
    }

    @Test
    void cancelAppointment_idempotentWhenAlreadyCancelled() {
        TenantAppointmentListItem cancelled =
                sampleRow("wa-5511999887766", "Revisão", "google-ev-2", 77L, TenantAppointmentListItem.BookingStatus.CANCELADO);
        when(query.findByIdForTenantAndConversation(eq(TID), eq(77L), eq("wa-5511999887766"), eq(ZONE_STR)))
                .thenReturn(Optional.of(cancelled));

        String out = service.cancelAppointment(TID, "wa-5511999887766", "5511999887766", "77", ZONE);

        assertThat(out).contains("já constava como cancelado").contains("Revisão");
        verify(scheduling, never()).deleteCalendarEvent(any(), any());
        verify(store, never()).markCancelled(any(Long.class), any());
    }

    @Test
    void cancelAppointment_acceptsEmailWhenCrmMatches() {
        UUID id = UUID.randomUUID();
        CrmCustomerRecord crmRow =
                new CrmCustomerRecord(
                        id,
                        TID.value(),
                        "portal-conv-1",
                        null,
                        "Nome",
                        "cliente@example.com",
                        Instant.parse("2026-01-01T12:00:00Z"),
                        0,
                        "",
                        "",
                        "",
                        0,
                        false,
                        "NONE",
                        null);
        when(crm.findByTenantAndConversationId(TID, "portal-conv-1")).thenReturn(Optional.of(crmRow));
        TenantAppointmentListItem row = sampleRowAgendado("portal-conv-1", "Serviço Z", "ev-z", 99L);
        when(query.findByIdForTenantAndConversation(eq(TID), eq(99L), eq("portal-conv-1"), eq(ZONE_STR)))
                .thenReturn(Optional.of(row));

        String out = service.cancelAppointment(TID, "portal-conv-1", "Cliente@Example.com", "99", ZONE);

        assertThat(out).isEmpty();
        verify(scheduling).deleteCalendarEvent(TID, "ev-z");
    }

    @Test
    void cancelAppointment_rejectsNonNumericAppointmentId() {
        String out = service.cancelAppointment(TID, "wa-5511999887766", "5511999887766", "o primeiro", ZONE);
        assertThat(out).contains("ID mostrado");
        verify(scheduling, never()).deleteCalendarEvent(any(), any());
    }

    @Test
    void cancelAppointment_failsWhenGoogleEventIdMissing() {
        TenantAppointmentListItem row =
                sampleRow("wa-5511999887766", "S", null, 55L, TenantAppointmentListItem.BookingStatus.AGENDADO);
        when(query.findByIdForTenantAndConversation(eq(TID), eq(55L), eq("wa-5511999887766"), eq(ZONE_STR)))
                .thenReturn(Optional.of(row));

        assertThat(service.cancelAppointment(TID, "wa-5511999887766", "", "55", ZONE))
                .contains("problema técnico")
                .doesNotContain("liberado com sucesso");
        verify(scheduling, never()).deleteCalendarEvent(any(), any());
        verify(store, never()).markCancelled(any(Long.class), any());
    }

    @Test
    void cancelAppointment_failsWhenDeleteCalendarReturnsFalse() {
        TenantAppointmentListItem row = sampleRowAgendado("wa-5511999887766", "X", "ev-1", 12L);
        when(query.findByIdForTenantAndConversation(eq(TID), eq(12L), eq("wa-5511999887766"), eq(ZONE_STR)))
                .thenReturn(Optional.of(row));
        when(scheduling.deleteCalendarEvent(eq(TID), eq("ev-1"))).thenReturn(false);

        assertThat(service.cancelAppointment(TID, "wa-5511999887766", "", "12", ZONE))
                .contains("problema técnico na agenda")
                .doesNotContain("liberado com sucesso");
        verify(store, never()).markCancelled(any(Long.class), any());
    }

    @Test
    void cancelAppointment_failsWhenMarkCancelledReturnsFalse() {
        TenantAppointmentListItem row = sampleRowAgendado("wa-5511999887766", "Y", "ev-2", 13L);
        when(query.findByIdForTenantAndConversation(eq(TID), eq(13L), eq("wa-5511999887766"), eq(ZONE_STR)))
                .thenReturn(Optional.of(row));
        when(store.markCancelled(eq(13L), any(Instant.class))).thenReturn(false);

        assertThat(service.cancelAppointment(TID, "wa-5511999887766", "", "13", ZONE))
                .contains("falha ao atualizar o sistema")
                .doesNotContain("liberado com sucesso");
        verify(scheduling).deleteCalendarEvent(TID, "ev-2");
    }

    @Test
    void createAppointment_onSchedulingSuccess_publishesEvent() {
        when(scheduling.createAppointment(
                        eq(TID),
                        eq("2026-06-10"),
                        eq("14:00"),
                        eq("Nome"),
                        eq(1L),
                        eq("Serviço"),
                        eq("wa-5511999000000")))
                .thenReturn(
                        CreateAppointmentResult.success(
                                "Agendamento confirmado para 10/06/2026 às 14:00. O horário foi registado na agenda da oficina.",
                                9001L));

        String out =
                service.createAppointment(
                        TID, "2026-06-10", "14:00", "Nome", "Serviço", "wa-5511999000000", ZONE);

        assertThat(out).contains("Agendamento confirmado");
        verify(store).markConfirmationNotificationPending(9001L);
        ArgumentCaptor<AppointmentConfirmedEvent> cap = ArgumentCaptor.forClass(AppointmentConfirmedEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().appointmentId()).isEqualTo(9001L);
        assertThat(cap.getValue().phoneNumber()).isEqualTo("5511999000000");
        assertThat(cap.getValue().startsAt())
                .isEqualTo(LocalDateTime.of(2026, 6, 10, 14, 0).atZone(ZONE).toInstant());
        assertThat(cap.getValue().calendarZoneId()).isEqualTo(ZONE.getId());
    }

    @Test
    void createAppointment_onSchedulingFailure_doesNotPublish() {
        when(scheduling.createAppointment(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(CreateAppointmentResult.failure("O calendário ainda não está ligado"));

        String out = service.createAppointment(TID, "2026-06-10", "14:00", "N", "S", "wa-5511", ZONE);

        assertThat(out).contains("calendário");
        verify(store, never()).markConfirmationNotificationPending(any(Long.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void createAppointment_whenServiceIsNotFromTenant_returnsUnsupportedWithAvailableOptions() {
        when(tenantServiceCatalog.findServiceIdByName(eq(TID), eq("Funilaria")))
                .thenReturn(Optional.empty());
        when(tenantServiceCatalog.listActiveServiceNames(eq(TID)))
                .thenReturn(List.of("Alinhamento", "Troca de óleo", "Balanceamento"));

        String out =
                service.createAppointment(
                        TID, "2026-06-10", "14:00", "Nome", "Funilaria", "wa-5511999000000", ZONE);

        assertThat(out)
                .contains("não é atendido")
                .contains("Funilaria")
                .contains("Alinhamento")
                .contains("Troca de óleo")
                .contains("Balanceamento")
                .contains("[service_option_map:1=Alinhamento|2=Troca de óleo|3=Balanceamento]");
        verify(scheduling, never()).createAppointment(any(), any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void listTenantServicesForScheduling_returnsCatalogOptions() {
        when(tenantServiceCatalog.listActiveServiceNames(eq(TID)))
                .thenReturn(List.of("Alinhamento", "Troca de óleo"));

        String out = service.listTenantServicesForScheduling(TID);

        assertThat(out)
                .contains("Serviços disponíveis")
                .contains("1) Alinhamento")
                .contains("2) Troca de óleo")
                .contains("Responda com o número")
                .contains("[service_option_map:1=Alinhamento|2=Troca de óleo]");
    }

    @Test
    void buildUnknownServiceReplyWithOptions_usesSameOptionMapAsListing() {
        when(tenantServiceCatalog.listActiveServiceNames(eq(TID)))
                .thenReturn(List.of("Alinhamento", "Troca de óleo"));

        String out = service.buildUnknownServiceReplyWithOptions(TID, "Lavagem");

        assertThat(out)
                .contains("\"Lavagem\"")
                .contains("não é atendido")
                .contains("1) Alinhamento")
                .contains("Responda com o número")
                .contains("[service_option_map:1=Alinhamento|2=Troca de óleo]");
    }

    @Test
    void createAppointment_publishException_stillReturnsSchedulingResult() {
        when(scheduling.createAppointment(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        CreateAppointmentResult.success(
                                "Agendamento confirmado para 10/06/2026 às 14:00. O horário foi registado na agenda da oficina.",
                                1L));
        Mockito.doThrow(new RuntimeException("event bus down")).when(eventPublisher).publishEvent(any());

        String out =
                service.createAppointment(
                        TID, "2026-06-10", "14:00", "N", "S", "wa-5511999000000", ZONE);

        assertThat(out).contains("Agendamento confirmado");
    }

    @Test
    void isSuccessfulCancellationReply_detectsPrefix() {
        assertThat(
                        AppointmentService.isSuccessfulCancellationReply(
                                AppointmentService.CANCELLATION_SUCCESS_MESSAGE_PREFIX
                                        + " Serviço: X. Data: 01/01/2026. A vaga já está disponível."))
                .isTrue();
        assertThat(AppointmentService.isSuccessfulCancellationReply("")).isTrue();
        assertThat(AppointmentService.isSuccessfulCancellationReply("Não foi possível concluir o cancelamento."))
                .isFalse();
        assertThat(AppointmentService.isSuccessfulCancellationReply(null)).isFalse();
        assertThat(
                        AppointmentService.isSuccessfulCancellationReply(
                                AppointmentService.CANCELLATION_ALREADY_CANCELLED_MESSAGE_PREFIX + " Serviço: X."))
                .isTrue();
    }

    @Test
    void normalizePhoneDigitsForComparison_alignsCountryCode() {
        assertThat(AppointmentService.normalizePhoneDigitsForComparison("5511999887766"))
                .isEqualTo(AppointmentService.normalizePhoneDigitsForComparison("+55 (11) 99988-7766"))
                .isEqualTo(AppointmentService.normalizePhoneDigitsForComparison("11999887766"));
    }

    @Test
    void resolveActiveAppointmentIdForReschedule_withHint_matchesSlotAmongSeveral() {
        Instant at11 = ZonedDateTime.of(LocalDate.of(2026, 4, 24), LocalTime.of(11, 0), ZONE).toInstant();
        Instant at11End = at11.plusSeconds(30 * 60);
        Instant at15 = ZonedDateTime.of(LocalDate.of(2026, 4, 24), LocalTime.of(15, 0), ZONE).toInstant();
        Instant at15End = at15.plusSeconds(30 * 60);
        TenantAppointmentListItem morning = sampleRowAt("wa-5511", "Polimento", "g1", 7L, at11, at11End);
        TenantAppointmentListItem afternoon = sampleRowAt("wa-5511", "Corte", "g2", 8L, at15, at15End);
        when(query.listAgendadoByConversationOrderedAscending(eq(TID), eq("wa-5511"), eq(ZONE_STR)))
                .thenReturn(List.of(morning, afternoon));
        String msg = "Gostaria de reagendar o serviço do dia 24/04/2026 11:00 para as 15:00";
        assertThat(service.resolveActiveAppointmentIdForReschedule(TID, "wa-5511", ZONE, msg))
                .hasValue(7L);
    }

    @Test
    void resolveActiveAppointmentIdForReschedule_fallbackByDayWhenListEmpty() {
        when(query.listAgendadoByConversationOrderedAscending(eq(TID), eq("wa-5511"), eq(ZONE_STR)))
                .thenReturn(List.of());
        Instant at11 = ZonedDateTime.of(LocalDate.of(2026, 4, 24), LocalTime.of(11, 0), ZONE).toInstant();
        TenantAppointmentListItem row = sampleRowAt("wa-5511", "Polimento", "g1", 77L, at11, at11.plusSeconds(1800));
        when(query.findActiveByConversationAndLocalDate(
                        eq(TID), eq("wa-5511"), eq(LocalDate.of(2026, 4, 24)), eq(ZONE_STR)))
                .thenReturn(Optional.of(row));

        String msg = "Gostaria de reagendar o serviço do dia 24/04/2026 11:00 para as 12:00";
        assertThat(service.resolveActiveAppointmentIdForReschedule(TID, "wa-5511", ZONE, msg))
                .hasValue(77L);
    }

    @Test
    void resolveActiveAppointmentIdForReschedule_fallbackToSingleAppointmentInDayWhenTimeDiffers() {
        Instant at1030 = ZonedDateTime.of(LocalDate.of(2026, 4, 24), LocalTime.of(10, 30), ZONE).toInstant();
        TenantAppointmentListItem row =
                sampleRowAt("wa-5511", "Polimento", "g1", 88L, at1030, at1030.plusSeconds(1800));
        when(query.listAgendadoByConversationOrderedAscending(eq(TID), eq("wa-5511"), eq(ZONE_STR)))
                .thenReturn(List.of(row));
        when(query.findActiveByConversationAndLocalDate(
                        eq(TID), eq("wa-5511"), eq(LocalDate.of(2026, 4, 24)), eq(ZONE_STR)))
                .thenReturn(Optional.of(row));

        String msg = "Gostaria de reagendar o serviço do dia 24/04/2026 11:00 para as 12:00";
        assertThat(service.resolveActiveAppointmentIdForReschedule(TID, "wa-5511", ZONE, msg))
                .hasValue(88L);
    }

    @Test
    void getActiveAppointments_listsMultipleWithInstruction() {
        TenantAppointmentListItem a =
                sampleRowAgendado("wa-5511", "A", "g1", 2L);
        TenantAppointmentListItem b =
                sampleRowAgendado("wa-5511", "B", "g2", 5L);
        when(query.listAgendadoByConversationOrderedAscending(eq(TID), eq("wa-5511"), eq(ZONE_STR)))
                .thenReturn(List.of(a, b));

        String out = service.getActiveAppointments(TID, "wa-5511", ZONE);
        assertThat(out)
                .contains("*Agendamentos*")
                .contains("Quais dos atendimentos abaixo gostaria de cancelar?")
                .contains("2) *A*")
                .contains("5) *B*")
                .contains(AppointmentService.LIST_APPOINTMENTS_RESCHEDULE_HINT_FOOTER_PT)
                .contains("[cancel_option_map:2=2,5=5]");
    }

    @Test
    void getActiveAppointments_singleRow_includesRescheduleAndCancelHintFooter() {
        TenantAppointmentListItem one = sampleRowAgendado("wa-5511", "Serviço", "g1", 24L);
        when(query.listAgendadoByConversationOrderedAscending(eq(TID), eq("wa-5511"), eq(ZONE_STR)))
                .thenReturn(List.of(one));

        String out = service.getActiveAppointments(TID, "wa-5511", ZONE);
        assertThat(out)
                .contains("Segue o seu agendamento ativo:")
                .contains("24) *Serviço*")
                .contains(AppointmentService.LIST_APPOINTMENTS_RESCHEDULE_HINT_FOOTER_PT);
    }

    @Test
    void getActiveAppointments_whenEmpty_returnsFriendlyNewBookingPrompt() {
        when(query.listAgendadoByConversationOrderedAscending(eq(TID), eq("wa-5511"), eq(ZONE_STR)))
                .thenReturn(List.of());

        String out = service.getActiveAppointments(TID, "wa-5511", ZONE);
        assertThat(out).isEqualTo(AppointmentService.NO_ACTIVE_APPOINTMENTS_FRIENDLY_MESSAGE);
    }

    private static TenantAppointmentListItem sampleRowAgendado(String conv, String service, String googleId, long id) {
        return sampleRow(conv, service, googleId, id, TenantAppointmentListItem.BookingStatus.AGENDADO);
    }

    private static TenantAppointmentListItem sampleRow(
            String conv,
            String service,
            String googleId,
            long id,
            TenantAppointmentListItem.BookingStatus booking) {
        return new TenantAppointmentListItem(
                id,
                TID.value(),
                conv,
                "Cliente",
                service,
                Instant.parse("2026-04-20T17:00:00Z"),
                Instant.parse("2026-04-20T17:30:00Z"),
                googleId,
                Instant.parse("2026-04-01T10:00:00Z"),
                TenantAppointmentListItem.AppointmentStatus.UPCOMING,
                booking);
    }

    private static TenantAppointmentListItem sampleRowAt(
            String conv, String service, String googleId, long id, Instant startsAt, Instant endsAt) {
        return new TenantAppointmentListItem(
                id,
                TID.value(),
                conv,
                "Cliente",
                service,
                startsAt,
                endsAt,
                googleId,
                Instant.parse("2026-04-01T10:00:00Z"),
                TenantAppointmentListItem.AppointmentStatus.UPCOMING,
                TenantAppointmentListItem.BookingStatus.AGENDADO);
    }
}
