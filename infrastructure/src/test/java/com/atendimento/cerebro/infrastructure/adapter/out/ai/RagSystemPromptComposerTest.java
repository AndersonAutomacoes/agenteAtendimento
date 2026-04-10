package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;
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
}
