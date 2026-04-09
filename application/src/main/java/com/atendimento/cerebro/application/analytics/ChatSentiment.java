package com.atendimento.cerebro.application.analytics;

/** Sentimento em {@code chat_analytics}. */
public enum ChatSentiment {
    Positivo,
    Neutro,
    Negativo;

    public String dbValue() {
        return name();
    }

    public static ChatSentiment fromDbValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return Neutro;
        }
        String t = raw.strip();
        for (ChatSentiment v : values()) {
            if (v.name().equalsIgnoreCase(t)) {
                return v;
            }
        }
        return Neutro;
    }
}
