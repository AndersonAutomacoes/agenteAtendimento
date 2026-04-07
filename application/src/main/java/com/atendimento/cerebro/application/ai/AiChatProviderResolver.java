package com.atendimento.cerebro.application.ai;

import java.util.Locale;

/** Resolve {@link AiChatProvider} a partir do JSON ou do default configurado. */
public final class AiChatProviderResolver {

    private AiChatProviderResolver() {}

    public static AiChatProvider resolve(String rawFromRequest, AiChatProvider applicationDefault) {
        if (rawFromRequest == null || rawFromRequest.isBlank()) {
            return applicationDefault;
        }
        return switch (rawFromRequest.trim().toUpperCase(Locale.ROOT)) {
            case "GEMINI" -> AiChatProvider.GEMINI;
            case "OPENAI" -> AiChatProvider.OPENAI;
            default -> throw new IllegalArgumentException(
                    "aiProvider inválido: use GEMINI ou OPENAI (recebido: " + rawFromRequest + ")");
        };
    }
}
