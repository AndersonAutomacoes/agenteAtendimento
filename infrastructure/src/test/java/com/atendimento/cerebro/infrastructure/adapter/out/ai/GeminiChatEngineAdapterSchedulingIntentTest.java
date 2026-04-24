package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.scheduling.SchedulingEnforcedChoice;
import com.atendimento.cerebro.application.service.AppointmentService;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GeminiChatEngineAdapterSchedulingIntentTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Test
    void likelyIntent_trueForSchedulingKeywords() {
        assertThat(GeminiChatEngineAdapter.likelySchedulingOrConfirmationTurn("Quero agendar revisão amanhã"))
                .isTrue();
        assertThat(GeminiChatEngineAdapter.likelySchedulingOrConfirmationTurn("Agende para 15/04 10:00")).isTrue();
    }

    @Test
    void likelyIntent_trueForShortConfirmation() {
        assertThat(GeminiChatEngineAdapter.likelySchedulingOrConfirmationTurn("confirmado")).isTrue();
        assertThat(GeminiChatEngineAdapter.likelySchedulingOrConfirmationTurn("sim")).isTrue();
    }

    @Test
    void shouldRetrySchedulingToolPass_falseForOkWithoutSchedulingContext() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist = List.of(Message.assistantMessage("Tudo bem Anderson. Se precisar é só chamar."));
        AICompletionRequest req = new AICompletionRequest(tenant, hist, List.of(), "ok", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.shouldRetrySchedulingToolPass(req)).isFalse();
    }

    @Test
    void shouldRetrySchedulingToolPass_trueForOkWithSchedulingContext() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Disponibilidade para amanhã:\n1) 09:00\n2) 09:30\n[slot_options:09:00,09:30]\n[slot_date:2026-04-24]"));
        AICompletionRequest req = new AICompletionRequest(tenant, hist, List.of(), "ok", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.shouldRetrySchedulingToolPass(req)).isTrue();
    }

    @Test
    void likelyIntent_falseForGenericGreeting() {
        assertThat(GeminiChatEngineAdapter.likelySchedulingOrConfirmationTurn("oi, tudo bem?")).isFalse();
    }

    @Test
    void concreteDateInSchedulingFlow_trueWhenUserSendsOnlyDateAfterServiceAndPrompt() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.userMessage("Alinhamento 3d"),
                        Message.assistantMessage(
                                "Poderia confirmar a data exata? Assim verifico os horários disponíveis para o Alinhamento 3D."));
        AICompletionRequest req =
                new AICompletionRequest(tenant, hist, List.of(), "13/04", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.isConcreteDateInSchedulingFlow(req, ZONE)).isTrue();
    }

    @Test
    void concreteDateInSchedulingFlow_falseWithoutSchedulingContextInHistory() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist = List.of(Message.userMessage("Olá"), Message.assistantMessage("Em que posso ajudar?"));
        AICompletionRequest req =
                new AICompletionRequest(tenant, hist, List.of(), "13/04", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.isConcreteDateInSchedulingFlow(req, ZONE)).isFalse();
    }

    @Test
    void cancelContext_falseAfterSuccessfulCancellationWhenUserAsksAvailability() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.userMessage("Quero cancelar"),
                        Message.assistantMessage(
                                AppointmentService.CANCELLATION_SUCCESS_MESSAGE_PREFIX
                                        + " Serviço: X. Data: 01/01/2026. A vaga já está disponível."));
        AICompletionRequest req =
                new AICompletionRequest(tenant, hist, List.of(), "Tem horário amanhã?", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.transcriptSuggestsCancellation(req)).isFalse();
        assertThat(GeminiChatEngineAdapter.schedulingCancellationOrListManagementContext(req)).isFalse();
    }

    @Test
    void cancelContext_falseAfterSuccessWhenOlderTurnStillContainsCancelKeyword() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.userMessage("Quero cancelar"),
                        Message.assistantMessage("Agendamentos AGENDADO:\n1) X\n[cancel_option_map:1=1]"),
                        Message.userMessage("1"),
                        Message.assistantMessage(
                                "Claro! "
                                        + AppointmentService.CANCELLATION_SUCCESS_MESSAGE_PREFIX
                                        + " Serviço: X. Data: 01/01/2026."),
                        Message.assistantMessage("Posso ajudar em mais alguma coisa?"));
        AICompletionRequest req =
                new AICompletionRequest(tenant, hist, List.of(), "Quais horários para terça?", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.transcriptSuggestsCancellation(req)).isFalse();
        assertThat(GeminiChatEngineAdapter.schedulingCancellationOrListManagementContext(req)).isFalse();
    }

    @Test
    void cancelContext_trueAfterSuccessWhenUserInvokesCancelAgain() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.userMessage("Quero cancelar"),
                        Message.assistantMessage(
                                AppointmentService.CANCELLATION_SUCCESS_MESSAGE_PREFIX
                                        + " Serviço: X. Data: 01/01/2026."));
        AICompletionRequest req =
                new AICompletionRequest(tenant, hist, List.of(), "Preciso cancelar outro horário", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.schedulingCancellationOrListManagementContext(req)).isTrue();
    }

    @Test
    void transcriptSuggestsCancellation_trueWhenKeywordInHistoryOrCurrentMessage() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist = List.of(Message.userMessage("Quero remover o agendamento"));
        AICompletionRequest req =
                new AICompletionRequest(tenant, hist, List.of(), "amanhã", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.transcriptSuggestsCancellation(req)).isTrue();
    }

    @Test
    void transcriptSuggestsCancellation_falseForPureSchedulingAsk() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist = List.of(Message.userMessage("Quero marcar corte"));
        AICompletionRequest req =
                new AICompletionRequest(tenant, hist, List.of(), "tem horário amanhã?", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.transcriptSuggestsCancellation(req)).isFalse();
    }

    @Test
    void transcriptSuggestsCancellation_falseForRescheduleEvenIfHistoryMentionedCancel() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist = List.of(Message.userMessage("Quero cancelar o de amanhã"));
        AICompletionRequest req =
                new AICompletionRequest(
                        tenant,
                        hist,
                        List.of(),
                        "Na verdade reagendar o de 24/04/2026 11:00 para 15:00",
                        "",
                        AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.transcriptSuggestsCancellation(req)).isFalse();
    }

    @Test
    void cancelContext_falseAfterAssistantSaysNoActiveAppointmentsAndUserConfirms() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.assistantMessage(AppointmentService.NO_ACTIVE_APPOINTMENTS_FRIENDLY_MESSAGE));
        AICompletionRequest req = new AICompletionRequest(tenant, hist, List.of(), "sim", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.transcriptSuggestsCancellation(req)).isFalse();
        assertThat(GeminiChatEngineAdapter.schedulingCancellationOrListManagementContext(req)).isFalse();
    }

    @Test
    void cancelContext_falseAfterAssistantSaysNoActiveAppointmentsAndUserAsksNewBooking() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist = List.of(Message.assistantMessage(AppointmentService.NO_ACTIVE_APPOINTMENTS_FRIENDLY_MESSAGE));
        AICompletionRequest req =
                new AICompletionRequest(
                        tenant, hist, List.of(), "Gostaria de fazer um novo agendamento", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.transcriptSuggestsCancellation(req)).isFalse();
        assertThat(GeminiChatEngineAdapter.schedulingCancellationOrListManagementContext(req)).isFalse();
    }

    @Test
    void listManagementContext_trueWhenSimAfterAssistantAskedList() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.userMessage("Quero cancelar"),
                        Message.assistantMessage("Quer ver a lista de agendamentos antes?"));
        AICompletionRequest req = new AICompletionRequest(tenant, hist, List.of(), "sim", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.schedulingCancellationOrListManagementContext(req)).isTrue();
    }

    @Test
    void cancelContext_falseWhenSchedulingEnforcedChoicePresent_evenIfHistoryMentionsCancel() {
        TenantId tenant = new TenantId("tenant-1");
        LocalDate day = LocalDate.of(2026, 4, 16);
        List<Message> hist =
                List.of(
                        Message.userMessage("Quero cancelar o horário"),
                        Message.assistantMessage(
                                "Ótimo! Posso confirmar o agendamento?\n\n[scheduling_draft:"
                                        + day
                                        + "|17:30]"));
        AICompletionRequest req =
                new AICompletionRequest(
                        tenant,
                        hist,
                        List.of(),
                        "sim",
                        "",
                        AiChatProvider.GEMINI,
                        false,
                        true,
                        "wa-1",
                        null,
                        Optional.of(new SchedulingEnforcedChoice(day, "17:30")),
                        false,
                        Optional.of(day));
        assertThat(GeminiChatEngineAdapter.transcriptSuggestsCancellation(req)).isFalse();
        assertThat(GeminiChatEngineAdapter.schedulingCancellationOrListManagementContext(req)).isFalse();
    }

    @Test
    void schedulingRestart_falseCancelContextAfterUserWantsToBookAgain() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.userMessage("Quero cancelar"),
                        Message.assistantMessage(
                                "Agendamentos AGENDADO:\n1) X — 01/01/2026\n[cancel_option_map:1=42]"));
        AICompletionRequest req =
                new AICompletionRequest(
                        tenant, hist, List.of(), "Na verdade quero agendar para amanhã", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.transcriptSuggestsCancellation(req)).isFalse();
        assertThat(GeminiChatEngineAdapter.schedulingCancellationOrListManagementContext(req)).isFalse();
    }

    @Test
    void listManagementContext_simAfterListPrompt_evenWithoutCancelKeywordInBlob() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist = List.of(Message.assistantMessage("Deseja ver a lista de agendamentos?"));
        AICompletionRequest req = new AICompletionRequest(tenant, hist, List.of(), "sim", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.transcriptSuggestsCancellation(req)).isFalse();
        assertThat(GeminiChatEngineAdapter.schedulingCancellationOrListManagementContext(req)).isTrue();
    }

    @Test
    void cancelContext_falseWhenAssistantPitchUsedRemoverAndUserConfirms() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Temos alinhamento e balanceamento. Precisamos remover os pneus para medir. "
                                        + "Quer ver a lista de serviços?"));
        AICompletionRequest req = new AICompletionRequest(tenant, hist, List.of(), "sim", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.transcriptSuggestsCancellation(req)).isFalse();
        assertThat(GeminiChatEngineAdapter.schedulingCancellationOrListManagementContext(req)).isFalse();
    }

    @Test
    void cancelContext_falseWhenSimAfterVerAListaDeServicos() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Segue nossa lista de serviços. Quer ver a lista ou prefere agendar direto?"));
        AICompletionRequest req = new AICompletionRequest(tenant, hist, List.of(), "sim", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.schedulingCancellationOrListManagementContext(req)).isFalse();
    }

    @Test
    void likelyScheduling_trueForVerificaShortReply() {
        assertThat(GeminiChatEngineAdapter.likelySchedulingOrConfirmationTurn("Verifica")).isTrue();
        assertThat(GeminiChatEngineAdapter.likelySchedulingOrConfirmationTurn("verifique a disponibilidade"))
                .isTrue();
    }

    @Test
    void transcriptSuggestsCancellation_falseForVerificaEvenIfHistoryMentionedCancelVerb() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.userMessage("Quero cancelar o de segunda"),
                        Message.assistantMessage("Cancelado. Posso ajudar em mais algo?"),
                        Message.assistantMessage(
                                "Para sábado, 18 de abril de 2026, preciso de mais um detalhe. Quer que eu verifique os "
                                        + "horários disponíveis?"));
        AICompletionRequest req = new AICompletionRequest(tenant, hist, List.of(), "Verifica", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.transcriptSuggestsCancellation(req)).isFalse();
        assertThat(GeminiChatEngineAdapter.schedulingCancellationOrListManagementContext(req)).isFalse();
    }

    @Test
    void shouldForceProgrammaticCancel_trueWhenBareDigitAfterCancellationListing() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.userMessage("Quero cancelar"),
                        Message.assistantMessage(
                                "Agendamentos AGENDADO:\n1) Serviço — 01/01/2026 10:00\n[cancel_option_map:1=42]"));
        AICompletionRequest req = new AICompletionRequest(tenant, hist, List.of(), "1", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.shouldForceProgrammaticCancelAppointment(req)).isTrue();
    }

    @Test
    void shouldForceProgrammaticCancel_trueForOpcaoPhrase() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.assistantMessage("x\n[cancel_option_map:2=7]"));
        AICompletionRequest req =
                new AICompletionRequest(tenant, hist, List.of(), "opção 2", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.shouldForceProgrammaticCancelAppointment(req)).isTrue();
    }

    @Test
    void shouldForceProgrammaticCancel_trueWhenBareIdExceedsThreeDigitsAfterCancellationListing() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist =
                List.of(
                        Message.userMessage("Quero cancelar"),
                        Message.assistantMessage(
                                "Agendamentos AGENDADO:\n1234) Serviço — 01/01/2026 10:00\n[cancel_option_map:1234=1234]"));
        AICompletionRequest req =
                new AICompletionRequest(tenant, hist, List.of(), "1234", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.shouldForceProgrammaticCancelAppointment(req)).isTrue();
    }

    @Test
    void shouldForceProgrammaticCancel_falseWhenNoListingInHistory() {
        TenantId tenant = new TenantId("tenant-1");
        List<Message> hist = List.of(Message.assistantMessage("Seguem os horários: 09:00, 10:00"));
        AICompletionRequest req = new AICompletionRequest(tenant, hist, List.of(), "1", "", AiChatProvider.GEMINI);
        assertThat(GeminiChatEngineAdapter.shouldForceProgrammaticCancelAppointment(req)).isFalse();
    }

    @Test
    void isShortPostBookingAckAfterCompletedBooking_trueWhenOkAndPriorConfirmation() {
        List<Message> hist =
                List.of(
                        Message.assistantMessage(
                                "Agendamento confirmado para 24/04/2026 às 11:00. O horário foi registado na agenda da oficina."));
        assertThat(GeminiChatEngineAdapter.isShortPostBookingAckAfterCompletedBooking("ok", hist)).isTrue();
        assertThat(GeminiChatEngineAdapter.isShortPostBookingAckAfterCompletedBooking("obrigado", hist)).isFalse();
    }

    @Test
    void isShortPostBookingAckAfterCompletedBooking_falseWithoutPriorBooking() {
        List<Message> hist = List.of(Message.assistantMessage("Seguem os horários: 09:00, 10:00"));
        assertThat(GeminiChatEngineAdapter.isShortPostBookingAckAfterCompletedBooking("ok", hist)).isFalse();
    }
}
