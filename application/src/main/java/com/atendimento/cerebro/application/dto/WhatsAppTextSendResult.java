package com.atendimento.cerebro.application.dto;

import java.util.Objects;

/** Resultado de {@link com.atendimento.cerebro.application.port.out.WhatsAppTextOutboundPort#sendText}. */
public final class WhatsAppTextSendResult {

    private final boolean success;
    private final String messageId;
    private final String error;

    private WhatsAppTextSendResult(boolean success, String messageId, String error) {
        this.success = success;
        this.messageId = messageId;
        this.error = error;
    }

    public static WhatsAppTextSendResult ok(String messageIdOrNull) {
        return new WhatsAppTextSendResult(true, messageIdOrNull, null);
    }

    public static WhatsAppTextSendResult fail(String error) {
        String e = error != null && !error.isBlank() ? error.strip() : "erro desconhecido";
        return new WhatsAppTextSendResult(false, null, e);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getError() {
        return error;
    }

    @Override
    public String toString() {
        return "WhatsAppTextSendResult{success="
                + success
                + ", messageId="
                + Objects.toString(messageId)
                + ", error="
                + Objects.toString(error)
                + "}";
    }
}
