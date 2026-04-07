package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

public final class ChatFallbackMessages {

    public static final String MAINTENANCE =
            "Sistema em manutenção momentânea, tente em instantes";

    /** Resposta quando o TimeLimiter do circuit breaker excede {@code chat.circuit.timeout-ms} (pipeline completo). */
    public static final String TIMEOUT =
            "O processamento excedeu o tempo limite. Tente novamente em instantes.";

    private ChatFallbackMessages() {}
}
