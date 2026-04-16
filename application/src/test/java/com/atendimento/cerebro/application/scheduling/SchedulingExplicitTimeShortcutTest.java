package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.tenant.TenantId;
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
}
