package com.atendimento.cerebro.application.dto;

/**
 * Resultado mínimo de transcrição de áudio para encaminhar ao fluxo textual do chat.
 */
public record AudioTranscriptionResult(String text, Double confidence, String language) {
    public AudioTranscriptionResult {
        if (text == null) {
            text = "";
        }
    }
}
