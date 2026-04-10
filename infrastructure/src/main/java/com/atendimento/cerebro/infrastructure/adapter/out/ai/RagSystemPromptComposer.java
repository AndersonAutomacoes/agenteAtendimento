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

    private static final String HISTORY_COHERENCE =
            "Continuidade da conversa: seguem mensagens anteriores deste contacto (histórico recente). "
                    + "Usa esse histórico para manter coerência, evitar repetir o que já foi dito sem necessidade "
                    + "e responder em continuidade ao fluxo. Se algo no histórico conflitar com factos da base de "
                    + "conhecimento abaixo, privilegia a informação factual da base quando for aplicável.";

    private static final String RESUME_AFTER_HUMAN =
            "Você está retomando um atendimento que foi conduzido temporariamente por um humano. Analise as "
                    + "mensagens marcadas como atendente humano no histórico recente e siga as orientações ou "
                    + "acordos feitos por ele (como preços, descontos ou agendamentos).";

    private RagSystemPromptComposer() {}

    public static String compose(
            String systemPrompt,
            List<KnowledgeHit> knowledgeHits,
            boolean hasPriorConversationTurns,
            boolean resumeAfterHumanIntervention) {
        String personality = systemPrompt != null ? systemPrompt : "";
        StringBuilder sb = new StringBuilder();
        sb.append("Instrução de Personalidade: ")
                .append(personality)
                .append(".\n\n");
        if (resumeAfterHumanIntervention) {
            sb.append(RESUME_AFTER_HUMAN).append("\n\n");
        }
        if (hasPriorConversationTurns) {
            sb.append(HISTORY_COHERENCE).append("\n\n");
        }
        sb.append("Contexto de Conhecimento: ")
                .append(formatKnowledge(knowledgeHits))
                .append("\n\n")
                .append(ADDITIONAL_INSTRUCTION);
        return sb.toString();
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
