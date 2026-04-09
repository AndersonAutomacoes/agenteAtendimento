package com.atendimento.cerebro.application.analytics;

/** Contagem por sentimento (gráfico de atendimento). */
public record SentimentCount(ConversationSentiment sentiment, long count) {}
