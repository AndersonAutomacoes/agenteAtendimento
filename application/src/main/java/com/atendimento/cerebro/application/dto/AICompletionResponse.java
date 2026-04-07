package com.atendimento.cerebro.application.dto;

public record AICompletionResponse(String content) {
    public AICompletionResponse {
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
    }
}
