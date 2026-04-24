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

    /**
     * Número só com dígitos para a Evolution API ({@code number} no JSON): DDI+DDD+assinante, sem símbolos. Para Brasil,
     * prefixa {@code 55} quando o valor da sessão vier só com DDD+número (10–11 dígitos).
     */
    public static String digitsForEvolutionApi(String digitsOnlyFromConversation) {
        if (digitsOnlyFromConversation == null || digitsOnlyFromConversation.isBlank()) {
            return "";
        }
        String d = digitsOnlyFromConversation.replaceAll("\\D", "");
        if (d.isEmpty()) {
            return "";
        }
        if (d.startsWith("55") && d.length() >= 12) {
            return d;
        }
        if (d.length() >= 10 && d.length() <= 11) {
            return "55" + d;
        }
        return d;
    }

    /**
     * Máscara para logs de auditoria (ex.: 55719******).
     * Mantém prefixo internacional/DDD quando o número tem o suficiente; caso contrário retorna 4* ou o digest curto.
     */
    public static String maskPhoneForAudit(String digitsOnly) {
        if (digitsOnly == null || digitsOnly.isBlank()) {
            return "?";
        }
        String d = digitsOnly.replaceAll("\\D", "");
        if (d.isEmpty()) {
            return "?";
        }
        if (d.length() <= 4) {
            return "****";
        }
        int visible = Math.min(6, d.length() - 2);
        return d.substring(0, visible) + "..." + d.substring(d.length() - 2);
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
