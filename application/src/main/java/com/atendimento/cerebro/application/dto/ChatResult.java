package com.atendimento.cerebro.application.dto;

public record ChatResult(String assistantMessage) {
    public ChatResult {
        if (assistantMessage == null) {
            throw new IllegalArgumentException("assistantMessage is required");
        }
    }
}
