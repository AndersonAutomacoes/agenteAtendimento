package com.atendimento.cerebro.application.analytics;

/** Intenção principal em {@code chat_analytics} (rótulos em português). */
public enum ChatMainIntent {
    Venda,
    Suporte,
    Orcamento,
    Agendamento,
    Outros;

    /** Valor persistido em PostgreSQL (com acentos onde aplicável). */
    public String dbValue() {
        return this == Orcamento ? "Orçamento" : name();
    }

    public static ChatMainIntent fromDbValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return Outros;
        }
        String t = raw.strip();
        for (ChatMainIntent v : values()) {
            if (v.dbValue().equalsIgnoreCase(t) || v.name().equalsIgnoreCase(t)) {
                return v;
            }
        }
        return Outros;
    }
}
