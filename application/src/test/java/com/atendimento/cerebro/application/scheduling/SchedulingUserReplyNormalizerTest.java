package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.domain.conversation.Message;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulingUserReplyNormalizerTest {

    @Test
    void expand_mapsSoloDigitToSlotFromAppendix_hardcodedReply() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Segue a lista.\n\n[slot_options:09:00,09:30,10:00]\n[slot_date:2026-04-14]"));
        var e1 = SchedulingUserReplyNormalizer.expandNumericSlotChoice("1", hist);
        assertThat(e1.hardcodedAssistantReply()).isPresent();
        assertThat(e1.hardcodedAssistantReply().get())
                .contains("opção 1")
                .contains("09:00")
                .contains("14/04/2026");
        assertThat(e1.optionNumber()).isEqualTo(1);
        assertThat(e1.enforcedChoice()).isEmpty();
        assertThat(e1.pendingConfirmationDraft())
                .hasValueSatisfying(
                        c -> {
                            assertThat(c.date()).isEqualTo(LocalDate.of(2026, 4, 14));
                            assertThat(c.timeHhMm()).isEqualTo("09:00");
                        });
        assertThat(e1.blockCreateAppointmentThisTurn()).isTrue();

        var e2 = SchedulingUserReplyNormalizer.expandNumericSlotChoice("2", hist);
        assertThat(e2.hardcodedAssistantReply().get()).contains("opção 2").contains("09:30");
        assertThat(e2.pendingConfirmationDraft().orElseThrow().timeHhMm()).isEqualTo("09:30");
    }

    @Test
    void expand_mapsOpcaoPhrase_hardcodedReply() {
        List<Message> hist = List.of(Message.assistantMessage("Ok.\n\n[slot_options:09:00,10:00]\n[slot_date:2026-04-13]"));
        var e = SchedulingUserReplyNormalizer.expandNumericSlotChoice("opção 2", hist);
        assertThat(e.hardcodedAssistantReply()).isPresent();
        assertThat(e.hardcodedAssistantReply().get()).contains("opção 2").contains("10:00");
        assertThat(e.pendingConfirmationDraft().orElseThrow().timeHhMm()).isEqualTo("10:00");
        assertThat(e.blockCreateAppointmentThisTurn()).isTrue();
    }

    @Test
    void expand_keepsExplicitTime_enforcedDirectly() {
        List<Message> hist = List.of(Message.assistantMessage("x\n\n[slot_options:09:00,10:00]\n[slot_date:2026-04-14]"));
        var e = SchedulingUserReplyNormalizer.expandNumericSlotChoice("às 10:00 por favor", hist);
        assertThat(e.expandedUserMessage()).contains("10:00").contains("2026-04-14").contains("create_appointment");
        assertThat(e.enforcedChoice().orElseThrow().timeHhMm()).isEqualTo("10:00");
        assertThat(e.blockCreateAppointmentThisTurn()).isFalse();
    }

    @Test
    void expand_rejectsTimeNotInOfferedList() {
        List<Message> hist = List.of(Message.assistantMessage("x\n\n[slot_options:09:00,10:00]"));
        assertThat(SchedulingUserReplyNormalizer.expandNumericSlotChoice("15:00", hist).expandedUserMessage())
                .isEqualTo("15:00");
    }

    @Test
    void stripInternalSlotAppendix_removesMarker() {
        String raw = "Olá\n\n[slot_options:09:00,10:00]\n[slot_date:2026-04-14]\n\n[scheduling_draft:2026-04-14|15:30]";
        assertThat(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(raw)).isEqualTo("Olá");
    }

    @Test
    void resolveChoiceToTime_optionIndexBeyondList_empty() {
        assertThat(
                        SchedulingUserReplyNormalizer.resolveChoiceToTime(
                                "99", List.of("09:00", "10:00")))
                .isEmpty();
    }

    @Test
    void expand_option12_mapsToTwelfthSlot_hardcoded() {
        String slots =
                "09:00,09:30,10:00,10:30,11:00,11:30,12:00,12:30,13:00,13:30,14:00,14:30,15:00,15:30,16:00,16:30,17:00,17:30";
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "lista\n\n[slot_options:"
                                        + slots
                                        + "]\n[slot_date:2026-04-14]"));
        var e12 = SchedulingUserReplyNormalizer.expandNumericSlotChoice("12", hist);
        assertThat(e12.hardcodedAssistantReply().get()).contains("opção 12").contains("14:30");
        assertThat(e12.pendingConfirmationDraft().orElseThrow().timeHhMm()).isEqualTo("14:30");
        var e11 = SchedulingUserReplyNormalizer.expandNumericSlotChoice("11", hist);
        assertThat(e11.hardcodedAssistantReply().get()).contains("opção 11").contains("14:00");
        assertThat(e11.pendingConfirmationDraft().orElseThrow().timeHhMm()).isEqualTo("14:00");
    }

    @Test
    void expand_option14_hardcodedReply_fifteenthSlot() {
        String slots =
                "09:00,09:30,10:00,10:30,11:00,11:30,12:00,12:30,13:00,13:30,14:00,14:30,15:00,15:30,16:00,16:30,17:00,17:30";
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "lista\n\n[slot_options:"
                                        + slots
                                        + "]\n[slot_date:2026-04-14]"));
        var exp = SchedulingUserReplyNormalizer.expandNumericSlotChoice("14", hist);
        assertThat(exp.hardcodedAssistantReply()).isPresent();
        assertThat(exp.hardcodedAssistantReply().get())
                .contains("opção 14")
                .contains("15:30")
                .contains("14/04/2026");
        assertThat(exp.optionNumber()).isEqualTo(14);
        assertThat(exp.pendingConfirmationDraft())
                .hasValueSatisfying(
                        c -> {
                            assertThat(c.date()).isEqualTo(LocalDate.of(2026, 4, 14));
                            assertThat(c.timeHhMm()).isEqualTo("15:30");
                        });
        assertThat(exp.blockCreateAppointmentThisTurn()).isTrue();
    }

    @Test
    void expand_afterDraft_sim_setsEnforcedChoice() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Ok\n\n[slot_options:09:00,10:00]\n[slot_date:2026-04-14]\n\n[scheduling_draft:2026-04-14|10:00]"));
        var e = SchedulingUserReplyNormalizer.expandNumericSlotChoice("sim", hist);
        assertThat(e.enforcedChoice().orElseThrow().timeHhMm()).isEqualTo("10:00");
        assertThat(e.blockCreateAppointmentThisTurn()).isFalse();
    }
}
