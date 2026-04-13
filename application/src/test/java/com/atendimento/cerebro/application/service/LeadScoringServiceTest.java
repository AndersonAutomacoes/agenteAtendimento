package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class LeadScoringServiceTest {

    @Test
    void basePoints_nullIntent_defaultsToTen() {
        Assertions.assertThat(LeadScoringService.basePoints(null, 0)).isEqualTo(10);
        Assertions.assertThat(LeadScoringService.computeScore(null, 3)).isEqualTo(25);
    }

    @Test
    void basePoints_matrix() {
        Assertions.assertThat(LeadScoringService.basePoints(ChatMainIntent.Outros, 0)).isEqualTo(10);
        Assertions.assertThat(LeadScoringService.basePoints(ChatMainIntent.Suporte, 99)).isEqualTo(10);
        Assertions.assertThat(LeadScoringService.basePoints(ChatMainIntent.Orcamento, 0)).isEqualTo(50);
        Assertions.assertThat(LeadScoringService.basePoints(ChatMainIntent.Agendamento, 0)).isEqualTo(85);
        Assertions.assertThat(LeadScoringService.basePoints(ChatMainIntent.Agendamento, 1)).isEqualTo(85);
        Assertions.assertThat(LeadScoringService.basePoints(ChatMainIntent.Agendamento, 2)).isEqualTo(95);
        Assertions.assertThat(LeadScoringService.basePoints(ChatMainIntent.Venda, 0)).isEqualTo(35);
    }

    @Test
    void computeScore_capsAt100_andAddsEngagement() {
        Assertions.assertThat(LeadScoringService.computeScore(ChatMainIntent.Orcamento, 10))
                .isEqualTo(100);
        Assertions.assertThat(LeadScoringService.computeScore(ChatMainIntent.Agendamento, 3))
                .isEqualTo(100);
        Assertions.assertThat(LeadScoringService.computeScore(ChatMainIntent.Agendamento, 0))
                .isEqualTo(85);
        Assertions.assertThat(LeadScoringService.computeScore(ChatMainIntent.Agendamento, 2))
                .isEqualTo(100);
    }
}
