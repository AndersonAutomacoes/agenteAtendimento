package com.atendimento.cerebro.infrastructure.whatsapp;

/**
 * Como enviar payloads interativos de agendamento via Evolution quando existe {@link
 * com.atendimento.cerebro.application.dto.WhatsAppInteractiveReply}.
 */
public enum EvolutionInteractiveMode {
    /** Lista formatada apenas em texto (omitido quando houver apenas confirmação/cancel lista). */
    TEXT,
    /** Até três botões de resposta (limitados no WhatsApp; Baileys pode não renderizar). */
    BUTTONS,
    /** Lista interativa oficial ({@code /message/sendList/…}). Recomendado para muitos horários. */
    LIST;

    public static EvolutionInteractiveMode fromConfig(String raw, boolean legacySendInteractiveButtons) {
        if (raw != null && !raw.isBlank()) {
            try {
                return EvolutionInteractiveMode.valueOf(raw.strip().replace('-', '_').toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return legacySendInteractiveButtons ? BUTTONS : TEXT;
            }
        }
        return legacySendInteractiveButtons ? BUTTONS : TEXT;
    }
}
