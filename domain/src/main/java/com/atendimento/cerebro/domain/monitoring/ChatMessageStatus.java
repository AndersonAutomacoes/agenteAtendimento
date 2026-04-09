package com.atendimento.cerebro.domain.monitoring;

public enum ChatMessageStatus {
    /** Resposta do assistente persistida; envio ao provider ainda não confirmado. */
    RECEIVED,
    SENT,
    ERROR
}
