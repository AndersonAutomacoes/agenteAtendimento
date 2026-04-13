package com.atendimento.cerebro.application.scheduling;

import java.util.Optional;

/**
 * Resultado de {@link SchedulingUserReplyNormalizer#expandNumericSlotChoice}: texto para o modelo,
 * escolha fixa para {@code create_appointment} após confirmação, e rascunho quando o cliente escolheu
 * número da lista mas ainda não confirmou.
 *
 * @param hardcodedAssistantReply quando presente, o backend NÃO chama o modelo de IA: esta é a resposta
 *     final ao cliente (ex.: confirmação de escolha por número).
 * @param optionNumber número da opção escolhida pelo cliente (ex.: 14), para exibir na confirmação.
 */
public record SlotChoiceExpansion(
        String expandedUserMessage,
        Optional<SchedulingEnforcedChoice> enforcedChoice,
        Optional<SchedulingEnforcedChoice> pendingConfirmationDraft,
        boolean blockCreateAppointmentThisTurn,
        Optional<String> hardcodedAssistantReply,
        int optionNumber) {

    public static SlotChoiceExpansion unchanged(String userMessage) {
        return new SlotChoiceExpansion(userMessage, Optional.empty(), Optional.empty(), false, Optional.empty(), 0);
    }
}
