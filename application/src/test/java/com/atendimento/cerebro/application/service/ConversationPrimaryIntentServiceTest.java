package com.atendimento.cerebro.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.application.analytics.ConversationSentiment;
import com.atendimento.cerebro.application.analytics.PrimaryIntentCategory;
import org.junit.jupiter.api.Test;

class ConversationPrimaryIntentServiceTest {

    @Test
    void parsePrimaryIntent_readsSegmentBeforePipe() {
        assertThat(ConversationPrimaryIntentService.parsePrimaryIntent("ORCAMENTO|POSITIVO"))
                .isEqualTo(PrimaryIntentCategory.ORCAMENTO);
        assertThat(ConversationPrimaryIntentService.parsePrimaryIntent("RECLAMACAO|NEGATIVO"))
                .isEqualTo(PrimaryIntentCategory.RECLAMACAO);
    }

    @Test
    void parsePrimaryIntent_legacySingleToken() {
        assertThat(ConversationPrimaryIntentService.parsePrimaryIntent("AGENDAMENTO"))
                .isEqualTo(PrimaryIntentCategory.AGENDAMENTO);
    }

    @Test
    void parseConversationSentiment_detectsNegativoBeforeNeutroSubstring() {
        assertThat(ConversationPrimaryIntentService.parseConversationSentiment("ORCAMENTO|NEGATIVO"))
                .isEqualTo(ConversationSentiment.NEGATIVO);
        assertThat(ConversationPrimaryIntentService.parseConversationSentiment("ORCAMENTO|NEUTRO"))
                .isEqualTo(ConversationSentiment.NEUTRO);
        assertThat(ConversationPrimaryIntentService.parseConversationSentiment("only_intent"))
                .isEqualTo(ConversationSentiment.NEUTRO);
    }
}
