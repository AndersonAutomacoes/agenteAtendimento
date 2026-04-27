package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.service.AppointmentService;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulingExplicitTimeShortcutTest {

    private static final TenantId TENANT = new TenantId("t1");
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Mock
    private AppointmentSchedulingPort scheduling;

    @Mock
    private AppointmentService appointmentService;

    @Test
    void tryExpand_availableTime_returnsConfirmationDraft() {
        LocalDate day = LocalDate.of(2026, 6, 20);
        String iso = "2026-06-20";
        String line =
                "Calendário (sim). Horários livres em "
                        + iso
                        + " (America/Sao_Paulo): 09:00, 10:00, 10:30";
        when(scheduling.checkAvailability(eq(TENANT), eq(iso))).thenReturn(line);

        Optional<SlotChoiceExpansion> r =
                SchedulingExplicitTimeShortcut.tryExpand(
                        TENANT,
                        "Revisão, 20/06/2026 às 10:00",
                        List.of(Message.assistantMessage("Para qual serviço deseja agendar?")),
                        ZONE,
                        scheduling);

        assertThat(r).isPresent();
        SlotChoiceExpansion e = r.get();
        assertThat(e.hardcodedAssistantReply()).isPresent();
        assertThat(e.hardcodedAssistantReply().get()).contains("10:00").contains("Posso confirmar");
        assertThat(e.pendingConfirmationDraft()).isPresent();
        assertThat(e.pendingConfirmationDraft().get().date()).isEqualTo(day);
        assertThat(e.pendingConfirmationDraft().get().timeHhMm()).isEqualTo("10:00");
    }

    @Test
    void tryExpand_unavailableTime_returnsListWithoutCallingExpand() {
        String iso = "2026-06-21";
        String line =
                "Calendário (sim). Horários livres em "
                        + iso
                        + " (America/Sao_Paulo): 09:00, 09:30";
        when(scheduling.checkAvailability(eq(TENANT), eq(iso))).thenReturn(line);

        Optional<SlotChoiceExpansion> r =
                SchedulingExplicitTimeShortcut.tryExpand(
                        TENANT,
                        "Balanceamento, 21/06/2026 às 11:00",
                        List.of(),
                        ZONE,
                        scheduling);

        assertThat(r).isPresent();
        assertThat(r.get().hardcodedAssistantReply().get())
                .contains("não está disponível")
                .contains("09:00");
        assertThat(r.get().pendingConfirmationDraft()).isEmpty();
    }

    @Test
    void tryExpand_skipsWhenSlotOptionsAlreadyInHistory() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Lista\n[slot_options:09:00,10:00]\n[slot_date:2026-06-20]"));
        Optional<SlotChoiceExpansion> r =
                SchedulingExplicitTimeShortcut.tryExpand(
                        TENANT, "Revisão, 20/06/2026 às 10:00", hist, ZONE, scheduling);
        assertThat(r).isEmpty();
    }

    @Test
    void tryExpand_skipsWithoutTime() {
        Optional<SlotChoiceExpansion> r =
                SchedulingExplicitTimeShortcut.tryExpand(
                        TENANT, "Só revisão amanhã", List.of(), ZONE, scheduling);
        assertThat(r).isEmpty();
    }

    @Test
    void tryExpand_skipsWhenOnlyDateAndTimeWithoutRealService() {
        Optional<SlotChoiceExpansion> r =
                SchedulingExplicitTimeShortcut.tryExpand(
                        TENANT,
                        "Gostaria de agendar para amanhã as 11:00",
                        List.of(),
                        ZONE,
                        scheduling);
        assertThat(r).isEmpty();
    }

    @Test
    void tryExpand_serviceNotInCatalog_listsOptionsAndDoesNotCheckCalendar() {
        when(appointmentService.isServiceInTenantCatalog(eq(TENANT), eq("lavagem de carro"))).thenReturn(false);
        when(appointmentService.buildUnknownServiceReplyWithOptions(eq(TENANT), eq("lavagem de carro")))
                .thenReturn(
                        "O serviço \"lavagem de carro\" não é atendido. Estes são os serviços disponíveis:\n"
                                + "1) Revisão\n\nResponda com o número do serviço desejado (ex.: 1).\n\n"
                                + "[service_option_map:1=Revisão]");

        Optional<SlotChoiceExpansion> r =
                SchedulingExplicitTimeShortcut.tryExpand(
                        TENANT,
                        "lavagem de carro, 22/06/2026 às 15:00",
                        List.of(Message.assistantMessage("Em que posso ajudar?")),
                        ZONE,
                        scheduling,
                        appointmentService);

        assertThat(r).isPresent();
        assertThat(r.get().hardcodedAssistantReply())
                .contains("lavagem de carro")
                .contains("[service_option_map:1=Revisão]");
        assertThat(r.get().pendingConfirmationDraft()).isEmpty();
        verify(scheduling, never()).checkAvailability(any(), any());
    }

    @Test
    void tryExpand_longVoiceSentence_usesCatalogMatchInsteadOfTreatingFullTextAsService() {
        String iso = "2026-04-27";
        String line =
                "Calendário (sim). Horários livres em "
                        + iso
                        + " (America/Sao_Paulo): 10:00, 11:00, 11:30";
        when(appointmentService.resolveCatalogServiceMentionFromText(eq(TENANT), any()))
                .thenReturn(Optional.of("Troca de Pastilhas"));
        when(appointmentService.isServiceInTenantCatalog(eq(TENANT), eq("Troca de Pastilhas")))
                .thenReturn(true);
        when(scheduling.checkAvailability(eq(TENANT), eq(iso))).thenReturn(line);

        Optional<SlotChoiceExpansion> r =
                SchedulingExplicitTimeShortcut.tryExpand(
                        TENANT,
                        "Por favor, agende para amanhã, troca de pastilhas às 11 horas da manhã.",
                        List.of(),
                        ZONE,
                        scheduling,
                        appointmentService);

        assertThat(r).isPresent();
        assertThat(r.get().hardcodedAssistantReply()).hasValueSatisfying(
                reply ->
                        assertThat(reply)
                                .contains("Troca de Pastilhas")
                                .doesNotContain("Por favor, agende"));
    }

    @Test
    void sanitizeServiceHint_stripsPortugueseBookingBoilerplate() {
        assertThat(SchedulingExplicitTimeShortcut.sanitizeServiceHintAfterStrip("quero agendar uma revisão para"))
                .isEqualTo("revisão");
        assertThat(SchedulingExplicitTimeShortcut.sanitizeServiceHintAfterStrip("  agendar o alinhamento para "))
                .isEqualTo("alinhamento");
    }

    @Test
    void parseServiceNameFromHistory_extractsAndSanitizesMarkdownLine() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Ótimo! O horário *17:30* do dia *16/04/2026* está disponível para *quero agendar uma revisão para*. Posso confirmar?\n\n[scheduling_draft:2026-04-16|17:30]"));
        assertThat(SchedulingExplicitTimeShortcut.parseServiceNameForCreateFromHistory(hist)).contains("revisão");
    }

    @Test
    void tryParseExplicitDateAndTimeInUserText_parsesDayAndTime() {
        assertThat(
                        SchedulingExplicitTimeShortcut.tryParseExplicitDateAndTimeInUserText(
                                "Lavagem 25/04/2026 as 16:30", ZONE))
                .isPresent()
                .get()
                .satisfies(
                        c -> {
                            assertThat(c.timeHhMm()).isEqualTo("16:30");
                            assertThat(c.date().getDayOfMonth()).isEqualTo(25);
                        });
    }

    @Test
    void recoverEnforcedChoiceFromUserHistory_skipsSoloIndexAndReusesRequestWithTime() {
        List<Message> hist =
                List.of(
                        Message.userMessage("Gostaria de agendar par amanhã uma lavagem de carro as 16:30"),
                        Message.assistantMessage("msg"),
                        Message.userMessage("3"));
        assertThat(SchedulingExplicitTimeShortcut.recoverEnforcedChoiceFromUserHistory(hist, ZONE))
                .isPresent()
                .get()
                .satisfies(c -> assertThat(c.timeHhMm()).isEqualTo("16:30"));
    }

    @Test
    void tryParseExplicitDateAndTimeInUserText_parsesSpokenHourAndWeekday() {
        assertThat(
                        SchedulingExplicitTimeShortcut.tryParseExplicitDateAndTimeInUserText(
                                "Quero agendar revisão de freios para quinta-feira às 11 horas", ZONE))
                .isPresent()
                .get()
                .satisfies(
                        c -> {
                            assertThat(c.timeHhMm()).isEqualTo("11:00");
                            assertThat(c.date().getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
                        });
    }
}
