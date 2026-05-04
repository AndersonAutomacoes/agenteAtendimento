package com.atendimento.cerebro.application.dto;

/**
 * Linha de lista interativa Evolution ({@code sendList}); {@code rowId} aparece nas respostas do webhook e deve ser estável.
 */
public record WhatsAppInteractiveRow(String rowId, String title, String description) {
    public WhatsAppInteractiveRow {
        rowId = rowId == null ? "" : rowId.strip();
        title = title == null ? "" : title.strip();
        description = description == null ? "" : description.strip();
    }

    /** Sem descrição (limite menor de texto no cliente). */
    public WhatsAppInteractiveRow(String rowId, String title) {
        this(rowId, title, "");
    }
}
