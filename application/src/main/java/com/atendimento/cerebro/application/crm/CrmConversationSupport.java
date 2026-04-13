package com.atendimento.cerebro.application.crm;

import java.util.Optional;

/** Normalização alinhada a {@code chat_message.phone_number} e agendamentos {@code wa-...}. */
public final class CrmConversationSupport {

    private CrmConversationSupport() {}

    /** Dígitos do telefone quando {@code conversation_id} é WhatsApp ({@code wa-...}); vazio para portal. */
    public static Optional<String> phoneDigitsOnlyFromConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        String s = conversationId.strip();
        if (!s.startsWith("wa-") || s.length() <= 3) {
            return Optional.empty();
        }
        String digits = s.substring(3).replaceAll("\\D", "");
        return digits.isEmpty() ? Optional.empty() : Optional.of(digits);
    }

    /** {@code wa-} + apenas dígitos, alinhado a {@code chat_message.phone_number} normalizado. */
    public static String whatsAppConversationIdFromPhoneDigits(String phoneRaw) {
        if (phoneRaw == null || phoneRaw.isBlank()) {
            return null;
        }
        String digits = phoneRaw.replaceAll("\\D", "");
        return digits.isEmpty() ? null : "wa-" + digits;
    }
}
