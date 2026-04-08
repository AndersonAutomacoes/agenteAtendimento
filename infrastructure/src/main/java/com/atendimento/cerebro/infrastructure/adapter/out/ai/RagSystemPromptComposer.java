package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;
import java.util.List;
import org.springframework.util.CollectionUtils;

/**
 * Monta o conteúdo do {@link org.springframework.ai.chat.messages.SystemMessage} com persona por tenant e RAG.
 */
public final class RagSystemPromptComposer {

    private static final String ADDITIONAL_INSTRUCTION =
            "Instrução Adicional: Use apenas o contexto fornecido para responder. Se não souber, diga que não "
                    + "possui essa informação.";

    private RagSystemPromptComposer() {}

    public static String compose(String systemPrompt, List<KnowledgeHit> knowledgeHits) {
        String personality = systemPrompt != null ? systemPrompt : "";
        return "Instrução de Personalidade: "
                + personality
                + ".\n\n"
                + "Contexto de Conhecimento: "
                + formatKnowledge(knowledgeHits)
                + "\n\n"
                + ADDITIONAL_INSTRUCTION;
    }

    private static String formatKnowledge(List<KnowledgeHit> hits) {
        if (CollectionUtils.isEmpty(hits)) {
            return "(Nenhum trecho foi recuperado da base de conhecimento para esta pergunta.)";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (KnowledgeHit h : hits) {
            sb.append("[").append(i++).append("] ").append(h.content()).append("\n\n");
        }
        return sb.toString().strip();
    }
}
