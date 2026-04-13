package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.domain.conversation.Message;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.ZoneId;
import java.util.List;
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
}
