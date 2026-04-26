package com.atendimento.cerebro.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.service.AppointmentService;
import com.atendimento.cerebro.domain.conversation.Message;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulingUserReplyNormalizerTest {

    @Test
    void looksLikeListActiveAppointmentsIntent_detectsPhrases() {
        assertThat(SchedulingUserReplyNormalizer.looksLikeListActiveAppointmentsIntent("Listar meus agendamentos"))
                .isTrue();
        assertThat(SchedulingUserReplyNormalizer.looksLikeListActiveAppointmentsIntent("Lista de agendamentos"))
                .isTrue();
        assertThat(SchedulingUserReplyNormalizer.looksLikeListActiveAppointmentsIntent("Lista meus agendamentos"))
                .isTrue();
        assertThat(SchedulingUserReplyNormalizer.looksLikeListActiveAppointmentsIntent("Quais são meus agendamentos?"))
                .isTrue();
        assertThat(SchedulingUserReplyNormalizer.looksLikeListActiveAppointmentsIntent("ver os agendamentos ativos"))
                .isTrue();
    }

    @Test
    void looksLikeListActiveAppointmentsIntent_rejectsNewBooking() {
        assertThat(SchedulingUserReplyNormalizer.looksLikeListActiveAppointmentsIntent("Quero agendar para sexta"))
                .isFalse();
        assertThat(SchedulingUserReplyNormalizer.looksLikeListActiveAppointmentsIntent("reagendar o de amanhã"))
                .isFalse();
    }

    @Test
    void parseReagendamentoDeParaHint_extractsDateAndFromToTime() {
        var h =
                SchedulingUserReplyNormalizer.parseReagendamentoDeParaHint(
                        "Gostaria de reagendar o serviço do dia 24/04/2026 11:00 para as 15:00");
        assertThat(h).hasValueSatisfying(
                x -> {
                    assertThat(x.day()).isEqualTo(LocalDate.of(2026, 4, 24));
                    assertThat(x.fromTime()).isEqualTo(LocalTime.of(11, 0));
                    assertThat(x.toTime()).isEqualTo(LocalTime.of(15, 0));
                });
    }

    @Test
    void parseReagendamentoDeParaHint_extractsRelativeTomorrow() {
        var h =
                SchedulingUserReplyNormalizer.parseReagendamentoDeParaHint(
                        "Gostaria de reagendar de amanhã às 11:00 para amanhã às 15:00");
        assertThat(h).isPresent();
        assertThat(h.get().fromTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(h.get().toTime()).isEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void parseReagendamentoDeParaHint_extractsRelativeTomorrowDasPara() {
        var h =
                SchedulingUserReplyNormalizer.parseReagendamentoDeParaHint(
                        "Gostaria de reagendar o atendimento amanhã das 11:00 para as 12:00");
        assertThat(h).isPresent();
        assertThat(h.get().fromTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(h.get().toTime()).isEqualTo(LocalTime.of(12, 0));
    }

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
    void expand_explicitTime_requiresConfirmationDraftBeforeCreate() {
        List<Message> hist = List.of(Message.assistantMessage("x\n\n[slot_options:09:00,10:00]\n[slot_date:2026-04-14]"));
        var e = SchedulingUserReplyNormalizer.expandNumericSlotChoice("às 10:00 por favor", hist);
        assertThat(e.hardcodedAssistantReply()).isPresent();
        assertThat(e.hardcodedAssistantReply().orElseThrow()).contains("10:00").contains("Posso confirmar");
        assertThat(e.pendingConfirmationDraft().orElseThrow().timeHhMm()).isEqualTo("10:00");
        assertThat(e.enforcedChoice()).isEmpty();
        assertThat(e.blockCreateAppointmentThisTurn()).isTrue();
    }

    @Test
    void expand_doesNotCaptureTimeWhenMessageIsRescheduleDePara() {
        List<Message> hist = List.of(Message.assistantMessage("x\n\n[slot_options:09:30,10:00,10:30]\n[slot_date:2026-04-24]"));
        String msg = "Gostaria de solicitar um reagendamento do atendimento de amanhã às 09:30 para as 10:30";
        var e = SchedulingUserReplyNormalizer.expandNumericSlotChoice(msg, hist);
        assertThat(e.expandedUserMessage()).isEqualTo(msg);
        assertThat(e.enforcedChoice()).isEmpty();
        assertThat(e.hardcodedAssistantReply()).isEmpty();
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
    void stripInternalSlotAppendix_removesCancelOptionMap() {
        String raw = "Lista\n[cancel_option_map:1=42,2=99]";
        assertThat(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(raw)).isEqualTo("Lista");
    }

    @Test
    void stripInternalSlotAppendix_removesServiceMapsAndSelection() {
        String raw = "Serviços\n[service_option_map:1=Alinhamento|2=Revisão]\n[selected_service:Alinhamento]";
        assertThat(SchedulingUserReplyNormalizer.stripInternalSlotAppendix(raw)).isEqualTo("Serviços");
    }

    @Test
    void resolveSelectedServiceFromUserChoice_mapsOptionNumberFromHistory() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Serviços disponíveis:\n1) Alinhamento\n2) Revisão\n\n[service_option_map:1=Alinhamento|2=Revisão]"));
        assertThat(SchedulingUserReplyNormalizer.resolveSelectedServiceFromUserChoice("2", hist))
                .hasValue("Revisão");
    }

    @Test
    void parseLastSelectedServiceFromHistory_skipsInvalidNumericEcho() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Perfeito [selected_service:Alinhamento e Balanceamento]"),
                        Message.assistantMessage("eco [selected_service:1]"));
        assertThat(SchedulingUserReplyNormalizer.parseLastSelectedServiceFromHistory(hist))
                .contains("Alinhamento e Balanceamento");
    }

    @Test
    void expand_numericIndex_doesNotMapToSlotWhenServiceOptionListIsNewer() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Disponibilidade [slot_options:11:00,10:00,09:00]\n[slot_date:2026-04-25]"),
                        Message.assistantMessage(
                                "Escolha o serviço [service_option_map:1=Alinhamento|2=Higienização]"));
        var e = SchedulingUserReplyNormalizer.expandNumericSlotChoice("2", hist);
        assertThat(e.expandedUserMessage()).isEqualTo("2");
        assertThat(e.hardcodedAssistantReply()).isEmpty();
    }

    @Test
    void stripSlotSchedulingStateOnly_preservesCancelOptionMap() {
        String raw = "x[slot_options:09:00]\n[cancel_option_map:1=5]";
        assertThat(SchedulingUserReplyNormalizer.stripSlotSchedulingStateOnly(raw)).isEqualTo("x\n[cancel_option_map:1=5]");
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

    @Test
    void expand_sim_afterCancelPrompt_doesNotUseStaleDraftFromOlderTurn() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Posso confirmar?\n\n[slot_options:09:00,10:00]\n[slot_date:2026-04-14]\n\n[scheduling_draft:2026-04-14|17:00]"),
                        Message.assistantMessage(
                                "Agendamento confirmado.\n\n[cancel_option_map:10=123]"),
                        Message.assistantMessage(
                                "O agendamento de Revisão está como opção 10. Deseja cancelar?\n\n[cancel_option_map:10=123]"));
        var e = SchedulingUserReplyNormalizer.expandNumericSlotChoice("sim", hist);
        assertThat(e.expandedUserMessage()).isEqualTo("sim");
        assertThat(e.enforcedChoice()).isEmpty();
        assertThat(e.hardcodedAssistantReply()).isEmpty();
    }

    @Test
    void isBackendCreateConfirmationInstruction_detectsExpandedSimFromChatService() {
        assertThat(
                        SchedulingUserReplyNormalizer.isBackendCreateConfirmationInstruction(
                                "O cliente confirmou o agendamento. Chame create_appointment com date=2026-04-16 e time=17:30."))
                .isTrue();
        assertThat(SchedulingUserReplyNormalizer.isBackendCreateConfirmationInstruction("sim")).isFalse();
    }

    @Test
    void parseLastDraftFromHistory_prefersNewestBotMessageWithDraftToken() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage("Antiga\n\n[scheduling_draft:2026-04-10|09:00]"),
                        Message.assistantMessage("Reforço sem apêndice."));
        var d = SchedulingUserReplyNormalizer.parseLastDraftFromHistory(hist);
        assertThat(d).hasValueSatisfying(
                c -> {
                    assertThat(c.date()).isEqualTo(LocalDate.of(2026, 4, 10));
                    assertThat(c.timeHhMm()).isEqualTo("09:00");
                });
    }

    @Test
    void parseLastDraftFromHistory_ignoresDraftWhenBookingAlreadyCompletedAfterDraft() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage("Confirme?\n\n[scheduling_draft:2026-04-24|16:00]"),
                        Message.assistantMessage(
                                "Confirmação Realizada! O agendamento foi confirmado com sucesso."));
        assertThat(SchedulingUserReplyNormalizer.parseLastDraftFromHistory(hist)).isEmpty();
    }

    @Test
    void looksLikeCancellationIntent_portuguesePhrases() {
        assertThat(SchedulingUserReplyNormalizer.looksLikeCancellationIntent("Quero cancelar o agendamento")).isTrue();
        assertThat(SchedulingUserReplyNormalizer.looksLikeCancellationIntent("desmarcar por favor")).isTrue();
        assertThat(SchedulingUserReplyNormalizer.looksLikeCancellationIntent("Quero marcar às 10")).isFalse();
    }

    @Test
    void looksLikeCancellationInBlob_detectsKeywordsInLongTranscript() {
        assertThat(
                        SchedulingUserReplyNormalizer.looksLikeCancellationInBlob(
                                "Oi\nQuero excluir o compromisso de amanhã"))
                .isTrue();
        assertThat(SchedulingUserReplyNormalizer.looksLikeCancellationInBlob("favor remover o agendamento")).isTrue();
        assertThat(SchedulingUserReplyNormalizer.looksLikeCancellationInBlob("só marcar revisão às 10")).isFalse();
    }

    @Test
    void looksLikeCancellationInBlob_falseForRemoverInServicePitch() {
        assertThat(
                        SchedulingUserReplyNormalizer.looksLikeCancellationInBlob(
                                "Alinhamento: precisamos remover os pneus antigos para calibrar."))
                .isFalse();
        assertThat(
                        SchedulingUserReplyNormalizer.looksLikeCancellationInBlob(
                                "Troca de óleo inclui remoção do filtro antigo."))
                .isFalse();
    }

    @Test
    void lastAssistantSuggestedAppointmentCancellation_trueWhenLatestAssistantIsAppointmentList() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage("slots\n[slot_options:09:00,10:00]\n[slot_date:2026-04-14]"),
                        Message.assistantMessage(
                                "Agendamentos AGENDADO:\n1) Serviço — 01/01/2026 10:00\n[cancel_option_map:1=5]"));
        assertThat(SchedulingUserReplyNormalizer.lastAssistantSuggestedAppointmentCancellation(hist)).isTrue();
    }

    @Test
    void lastAssistantSuggestedAppointmentCancellation_trueForFriendlyAgendamentosAtivosPrompt() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                """
                                *Agendamentos*

                                Quais dos atendimentos abaixo gostaria de cancelar?

                                12) *X* — 24/04/2026 13:30"""));
        assertThat(SchedulingUserReplyNormalizer.lastAssistantSuggestedAppointmentCancellation(hist)).isTrue();
    }

    @Test
    void lastAssistantSuggestedAppointmentCancellation_trueForLegacyActivosSpelling() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Existem vários agendamentos activos. Pergunte qual deseja cancelar.\n\n"
                                        + "Escolha uma das opções entre os serviços AGENDADOS:\n20) x — 24/04/2026 13:00"));
        assertThat(SchedulingUserReplyNormalizer.lastAssistantSuggestedAppointmentCancellation(hist)).isTrue();
    }

    @Test
    void expand_numericChoiceAfterAppointmentList_doesNotMapToOlderSlotOptions() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage("slots\n[slot_options:09:00,10:00]\n[slot_date:2026-04-14]"),
                        Message.assistantMessage(
                                "Agendamentos AGENDADO:\n1) Serviço — 01/01/2026 10:00\n[cancel_option_map:1=5]"));
        var e = SchedulingUserReplyNormalizer.expandNumericSlotChoice("1", hist);
        assertThat(e.expandedUserMessage()).isEqualTo("1");
        assertThat(e.hardcodedAssistantReply()).isEmpty();
        assertThat(e.pendingConfirmationDraft()).isEmpty();
    }

    @Test
    void stripSchedulingStateFromHistory_removesInternalMarkers() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Ok\n\n[slot_options:09:00,10:00]\n[slot_date:2026-04-14]\n\n[scheduling_draft:2026-04-14|10:00]"));
        List<Message> stripped = SchedulingUserReplyNormalizer.stripSchedulingStateFromHistory(hist);
        assertThat(stripped.get(0).content()).doesNotContain("scheduling_draft").doesNotContain("slot_options");
    }

    @Test
    void looksLikeSchedulingRestartIntent_trueForAgendarMarcarHorario() {
        assertThat(SchedulingUserReplyNormalizer.looksLikeSchedulingRestartIntent("Quero agendar para amanhã"))
                .isTrue();
        assertThat(SchedulingUserReplyNormalizer.looksLikeSchedulingRestartIntent("tem horário às 10?")).isTrue();
        assertThat(SchedulingUserReplyNormalizer.looksLikeSchedulingRestartIntent("disponibilidade sexta")).isTrue();
    }

    @Test
    void looksLikeSchedulingRestartIntent_falseForDesagendarSimOuCancel() {
        assertThat(SchedulingUserReplyNormalizer.looksLikeSchedulingRestartIntent("desagendar o de amanhã")).isFalse();
        assertThat(SchedulingUserReplyNormalizer.looksLikeSchedulingRestartIntent("sim")).isFalse();
        assertThat(SchedulingUserReplyNormalizer.looksLikeSchedulingRestartIntent("Quero cancelar")).isFalse();
    }

    @Test
    void stripInternalAppendicesFromHistory_removesCancelOptionMap() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Lista\n[cancel_option_map:1=42]\n[slot_options:09:00,10:00]"));
        List<Message> stripped = SchedulingUserReplyNormalizer.stripInternalAppendicesFromHistory(hist);
        assertThat(stripped.get(0).content())
                .doesNotContain("cancel_option_map")
                .doesNotContain("slot_options");
    }

    @Test
    void shouldRefuseAvailability_falseWhenOldCancelButUserPicksTimeAfterNoSuccess() {
        String hint = "Quero cancelar\nNão chame check_availability quando o cliente quer cancelar.";
        assertThat(
                        SchedulingUserReplyNormalizer.shouldRefuseAvailabilityBecauseCancelIntent(
                                hint, "Amanhã 12:00"))
                .isFalse();
    }

    @Test
    void shouldRefuseAvailability_trueWhenExplicitCancelInLatestMessage() {
        assertThat(
                        SchedulingUserReplyNormalizer.shouldRefuseAvailabilityBecauseCancelIntent(
                                "texto neutro", "preciso cancelar o de amanhã"))
                .isTrue();
    }

    @Test
    void shouldRefuseAvailability_falseAfterCancellationSuccessInBlob() {
        String hint =
                "Quero cancelar\n"
                        + AppointmentService.CANCELLATION_SUCCESS_MESSAGE_PREFIX
                        + " Serviço: X.\n";
        assertThat(
                        SchedulingUserReplyNormalizer.shouldRefuseAvailabilityBecauseCancelIntent(
                                hint, "Tem horário amanhã?"))
                .isFalse();
    }

    @Test
    void transcriptAfterLastCancellationSuccess_trimsOldCancelNoise() {
        String blob =
                "Quero cancelar\n"
                        + AppointmentService.CANCELLATION_SUCCESS_MESSAGE_PREFIX
                        + " ok\n"
                        + "Gostaria de agendar";
        assertThat(SchedulingUserReplyNormalizer.transcriptAfterLastCancellationSuccess(blob)).contains("Gostaria");
        assertThat(SchedulingUserReplyNormalizer.transcriptAfterLastCancellationSuccess(blob)).doesNotContain("Quero cancelar");
    }

    @Test
    void looksLikeRescheduleOrTimeChangeIntent_detectsPhrases() {
        assertThat(SchedulingUserReplyNormalizer.looksLikeRescheduleOrTimeChangeIntent("Queria trocar o horário de amanhã"))
                .isTrue();
        assertThat(SchedulingUserReplyNormalizer.looksLikeRescheduleOrTimeChangeIntent("Quero reagendar"))
                .isTrue();
        assertThat(SchedulingUserReplyNormalizer.looksLikeRescheduleOrTimeChangeIntent("Quero desmarcar"))
                .isFalse();
        assertThat(SchedulingUserReplyNormalizer.looksLikeRescheduleOrTimeChangeIntent("Tem vaga amanhã?"))
                .isFalse();
    }

    @Test
    void isBareNewTimeProposalAfterRescheduleIntent_detectsTomorrowTimeAfterReagendar() {
        List<Message> hist =
                List.of(
                        Message.userMessage("Gostaria de reagendar o agendamento 41"),
                        Message.assistantMessage("ok"),
                        Message.userMessage("Amanhã 12:00"));
        assertThat(
                        SchedulingUserReplyNormalizer.isBareNewTimeProposalAfterRescheduleIntent("Amanhã 12:00", hist))
                .isTrue();
    }

    @Test
    void isBareNewTimeProposalAfterRescheduleIntent_falseWhenExplicitCancel() {
        List<Message> hist = List.of(Message.userMessage("reagendar o 41"), Message.assistantMessage("ok"));
        assertThat(
                        SchedulingUserReplyNormalizer.isBareNewTimeProposalAfterRescheduleIntent(
                                "Pode cancelar o agendamento 41", hist))
                .isFalse();
    }

    @Test
    void userMessageOverridesCancel_rescheduleDoesNotOverrideAvailabilityGuard() {
        assertThat(
                        SchedulingUserReplyNormalizer.userMessageOverridesCancelForAvailabilityCheck(
                                "Queria trocar o horário de amanhã"))
                .isFalse();
    }

    @Test
    void parseLastSlotOptionsFromHistory_emptyWhenBookingCompletedAfterList() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Segue a lista.\n\n[slot_options:09:00,10:00]\n[slot_date:2026-04-24]"),
                        Message.userMessage("sim"),
                        Message.assistantMessage(
                                "Agendamento confirmado para 24/04/2026 às 11:00. O horário foi registado na agenda da oficina."));
        assertThat(SchedulingUserReplyNormalizer.parseLastSlotOptionsFromHistory(hist)).isEmpty();
    }

    @Test
    void expand_okAfterSuccessfulBooking_doesNotMapStaleSlot() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Segue a lista.\n\n[slot_options:09:00,10:00]\n[slot_date:2026-04-24]"),
                        Message.assistantMessage(
                                "Agendamento confirmado para 24/04/2026 às 11:00. O horário foi registado na agenda da oficina."));
        var e = SchedulingUserReplyNormalizer.expandNumericSlotChoice("ok", hist);
        assertThat(e.expandedUserMessage()).isEqualTo("ok");
        assertThat(e.enforcedChoice()).isEmpty();
    }
}
