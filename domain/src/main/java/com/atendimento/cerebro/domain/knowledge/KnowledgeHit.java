package com.atendimento.cerebro.domain.knowledge;

public record KnowledgeHit(String id, String content, Double score) {
    public KnowledgeHit {
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
    }
}
