package com.atendimento.cerebro.application.analytics;

/** Contagem por categoria (gráfico de pizza). */
public record PrimaryIntentCount(PrimaryIntentCategory category, long count) {}
