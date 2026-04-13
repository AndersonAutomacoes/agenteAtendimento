package com.atendimento.cerebro.application.crm;

/** Estado do funil de oportunidade no CRM. */
public enum CrmIntentStatus {
    NONE,
    /** Intenção Orçamento/Agendamento detectada; ainda dentro da janela de 30 min. */
    OPEN,
    /** Passaram 30 min sem conversão (sem agendamento criado, etc.). */
    PENDING_LEAD,
    /**
     * Intenção Orçamento/Agendamento há mais de 1 h sem {@code tenant_appointments} após a deteção — prioridade
     * de recuperação.
     */
    HOT_LEAD,
    /** Dono assumiu para enviar oferta manual. */
    ASSIGNED,
    /** Agendamento criado ou marcado como ganho. */
    CONVERTED,
    /** Descartado (futuro). */
    DISMISSED;

    public String dbValue() {
        return name();
    }

    public static CrmIntentStatus fromDb(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return CrmIntentStatus.valueOf(raw.strip());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
