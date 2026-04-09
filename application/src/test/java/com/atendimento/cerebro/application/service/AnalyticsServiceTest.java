package com.atendimento.cerebro.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import com.atendimento.cerebro.application.analytics.ChatSentiment;
import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    @Test
    void parseClassification_parsesStructuredLines() {
        var raw =
                """
                INTENCAO: Suporte
                SENTIMENTO: Negativo
                """;
        var parsed = AnalyticsService.parseClassification(raw);
        assertThat(parsed.intent()).isEqualTo(ChatMainIntent.Suporte);
        assertThat(parsed.sentiment()).isEqualTo(ChatSentiment.Negativo);
    }

    @Test
    void parseClassification_acceptsOrcamentoWithAccent() {
        var raw =
                """
                INTENCAO: Orçamento
                SENTIMENTO: Positivo
                """;
        var parsed = AnalyticsService.parseClassification(raw);
        assertThat(parsed.intent()).isEqualTo(ChatMainIntent.Orcamento);
        assertThat(parsed.sentiment()).isEqualTo(ChatSentiment.Positivo);
    }

    @Test
    void parseClassification_fallbackWhenMalformed() {
        var parsed = AnalyticsService.parseClassification("");
        assertThat(parsed.intent()).isEqualTo(ChatMainIntent.Outros);
        assertThat(parsed.sentiment()).isEqualTo(ChatSentiment.Neutro);
    }

    @Test
    void parseMainIntent_mapsKeywords() {
        assertThat(AnalyticsService.parseMainIntent("é um pedido de VENDA")).isEqualTo(ChatMainIntent.Venda);
        assertThat(AnalyticsService.parseMainIntent("orçamento por favor")).isEqualTo(ChatMainIntent.Orcamento);
    }
}
