package com.atendimento.cerebro.application.scheduling;

import java.time.LocalDate;

/**
 * Data e hora deduzidas no backend a partir da lista numerada + {@code [slot_date:…]} no histórico.
 * Passadas no {@link com.atendimento.cerebro.application.dto.AICompletionRequest} para
 * {@code create_appointment} não depender de ThreadLocal (execução de ferramentas pode ser noutra thread).
 */
public record SchedulingEnforcedChoice(LocalDate date, String timeHhMm) {}
