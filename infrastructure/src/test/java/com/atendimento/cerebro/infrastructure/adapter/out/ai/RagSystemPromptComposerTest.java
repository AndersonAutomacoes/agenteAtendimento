package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagSystemPromptComposerTest {

    @Test
    void compose_includesPersonaContextAndAdditionalInstruction() {
        String text = RagSystemPromptComposer.compose(
                "Seja cordial",
                List.of(new KnowledgeHit("id", "Trecho único sobre política.", 0.9)),
                false,
                false);

        assertThat(text)
                .startsWith("Instrução de Personalidade: Seja cordial.\n\nContexto de Conhecimento:")
                .contains("[1] Trecho único sobre política.")
                .endsWith(
                        "Instrução Adicional: Use apenas o contexto fornecido para responder. Se não souber, diga que não possui essa informação.");
    }

    @Test
    void compose_whenNoHits_showsExplicitEmptyMessage() {
        String text = RagSystemPromptComposer.compose("x", List.of(), false, false);
        assertThat(text).contains("Contexto de Conhecimento: (Nenhum trecho foi recuperado");
    }

    @Test
    void compose_whenHasPriorTurns_addsContinuityInstruction() {
        String text = RagSystemPromptComposer.compose("x", List.of(), true, false);
        assertThat(text)
                .contains("Continuidade da conversa:")
                .contains("Contexto de Conhecimento:");
    }

    @Test
    void compose_whenResumeAfterHuman_addsParagraph() {
        String text = RagSystemPromptComposer.compose("x", List.of(), false, true);
        assertThat(text)
                .contains("retomando um atendimento")
                .contains("atendente humano");
    }

    @Test
    void compose_whenSchedulingToolsEnabled_addsSchedulingPolicy() {
        String text = RagSystemPromptComposer.compose("x", List.of(), false, false, true);
        assertThat(text)
                .contains("check_availability")
                .contains("create_appointment")
                .contains("PROIBIDO de chamar create_appointment se o usuário enviar apenas um número");
    }

    @Test
    void compose_whenSchedulingWithTemporal_includesAnchor() {
        String anchor = RagSystemPromptComposer.schedulingTemporalAnchor(ZoneId.of("UTC"));
        String text = RagSystemPromptComposer.compose("x", List.of(), false, false, true, anchor);
        assertThat(text)
                .contains("Referência temporal")
                .contains("UTC")
                .contains("dd/MM")
                .contains("check_availability");
    }

    @Test
    void schedulingTemporalAnchor_listsZoneAndHintsYear() {
        String anchor = RagSystemPromptComposer.schedulingTemporalAnchor(ZoneId.of("America/Sao_Paulo"));
        assertThat(anchor).contains("America/Sao_Paulo").contains("yyyy-MM-DD").contains("HOJE_EH").contains("amanhã");
    }
}
