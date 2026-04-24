package com.atendimento.cerebro.application.scheduling;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Indica a data e os horários "de -> para" extraídos de um pedido de reagendamento.
 */
public record ReagendamentoDeParaHint(LocalDate day, LocalTime fromTime, LocalTime toTime) {}
