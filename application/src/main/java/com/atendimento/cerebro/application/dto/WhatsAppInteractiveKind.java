package com.atendimento.cerebro.application.dto;

/** Tipo de payload interativo outbound (principalmente Evolution API). */
public enum WhatsAppInteractiveKind {
    /** Lista de vagas HH:mm gerada por disponibilidade. */
    SLOTS,
    /** Catálogo de serviços ({@code [service_option_map:…]}). */
    SERVICES,
    /** Confirmar ou alterar rascunho de agendamento (Sim / Não). */
    CONFIRMATION,
    /** Resposta a ID {@code cancel_<appointmentId>} (ex.: lista interactiva legacy ou eco estruturado). */
    CANCEL_PICK,
    /** Lista de agendamentos ativos (passo 1: escolher o compromisso). */
    APPOINTMENT_LIST,
    /** Após escolher compromisso: reagendar ou cancelar. */
    APPOINTMENT_ACTION
}
