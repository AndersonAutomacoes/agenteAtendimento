package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

/** Contagem por categoria de intenção principal (analytics_intents), para gráfico de pizza. */
public record IntentCategoryCountHttp(String category, long count) {}
