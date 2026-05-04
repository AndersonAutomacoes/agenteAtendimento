package com.atendimento.cerebro.application.dto;

/** Tipo de payload interativo outbound (principalmente Evolution API). */
public enum WhatsAppInteractiveKind {
    /** Lista de vagas HH:mm gerada por disponibilidade. */
    SLOTS,
    /** Confirmar ou alterar rascunho de agendamento (Sim / Não). */
    CONFIRMATION,
    /** Escolher qual agendamento cancelar quando existe {@code [cancel_option_map:…]}. */
    CANCEL_PICK
}
