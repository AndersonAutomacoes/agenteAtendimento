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
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AppointmentServiceTest {

    private static final TenantId TID = new TenantId("tenant-a");
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String ZONE_STR = ZONE.getId();

    private TenantAppointmentQueryPort query;
    private TenantAppointmentStorePort store;
    private AppointmentSchedulingPort scheduling;
    private CrmCustomerQueryPort crm;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        query = Mockito.mock(TenantAppointmentQueryPort.class);
        store = Mockito.mock(TenantAppointmentStorePort.class);
        scheduling = Mockito.mock(AppointmentSchedulingPort.class);
        crm = Mockito.mock(CrmCustomerQueryPort.class);
        service = new AppointmentService(query, store, scheduling, crm);
        when(scheduling.deleteCalendarEvent(any(), any())).thenReturn(true);
        when(store.markCancelled(any(Long.class), any(Instant.class))).thenReturn(true);
    }

    @Test
    void cancelAppointment_succeedsWhenIdValid_contactOptionalOrMismatchDoesNotBlock() {
        TenantAppointmentListItem row = sampleRowAgendado("wa-5511888777666", "Serviço X", "google-ev-x", 42L);
        when(query.findByIdForTenantAndConversation(eq(TID), eq(42L), eq("wa-5511888777666"), eq(ZONE_STR)))
                .thenReturn(Optional.of(row));

        assertThat(service.cancelAppointment(TID, "wa-5511888777666", "", "42", ZONE))
                .contains("liberado com sucesso")
                .contains("Serviço X");
        verify(scheduling).deleteCalendarEvent(TID, "google-ev-x");
        verify(store).markCancelled(eq(42L), any(Instant.class));

        Mockito.reset(scheduling, store);
        when(scheduling.deleteCalendarEvent(any(), any())).thenReturn(true);
        when(store.markCancelled(any(Long.class), any(Instant.class))).thenReturn(true);
        when(query.findByIdForTenantAndConversation(eq(TID), eq(42L), eq("wa-5511888777666"), eq(ZONE_STR)))
                .thenReturn(Optional.of(row));
        assertThat(service.cancelAppointment(TID, "wa-5511888777666", "(11) 99999-0000", "42", ZONE))
                .contains("liberado com sucesso");
        verify(scheduling).deleteCalendarEvent(TID, "google-ev-x");
        verify(store).markCancelled(eq(42L), any(Instant.class));
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

        assertThat(out).contains("liberado com sucesso").contains("Alinhamento").contains("20/04/2026");
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

        assertThat(out).contains("liberado com sucesso").contains("Serviço Z");
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
                .contains("Google Calendar")
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
                .contains("base de dados")
                .doesNotContain("liberado com sucesso");
        verify(scheduling).deleteCalendarEvent(TID, "ev-2");
    }

    @Test
    void isSuccessfulCancellationReply_detectsPrefix() {
        assertThat(
                        AppointmentService.isSuccessfulCancellationReply(
                                AppointmentService.CANCELLATION_SUCCESS_MESSAGE_PREFIX
                                        + " Serviço: X. Data: 01/01/2026. A vaga já está disponível."))
                .isTrue();
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
    void getActiveAppointments_listsMultipleWithInstruction() {
        TenantAppointmentListItem a =
                sampleRowAgendado("wa-5511", "A", "g1", 2L);
        TenantAppointmentListItem b =
                sampleRowAgendado("wa-5511", "B", "g2", 5L);
        when(query.listAgendadoByConversationOrderedAscending(eq(TID), eq("wa-5511"), eq(ZONE_STR)))
                .thenReturn(List.of(a, b));

        String out = service.getActiveAppointments(TID, "wa-5511", ZONE);
        assertThat(out)
                .contains("vários")
                .contains("2) ")
                .contains("5) ")
                .contains("[cancel_option_map:2=2,5=5]");
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
}
