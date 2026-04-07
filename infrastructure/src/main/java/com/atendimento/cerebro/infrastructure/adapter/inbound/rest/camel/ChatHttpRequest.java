package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corpo JSON da API de chat: {@code tenantId}, {@code sessionId}, {@code message}; opcionais {@code topK}, {@code aiProvider}.
 * <p>{@code aiProvider} escolhe o motor de geração: {@code GEMINI} (por defeito) ou {@code OPENAI} (só se
 * {@code cerebro.ai.openai-chat-enabled=true}).
 * <p>RAG (embeddings + pgvector) é sempre Google GenAI ({@code spring.ai.model.embedding.text}).
 * <p>Valores: {@code GEMINI} ou {@code OPENAI}; se omitido, usa {@code cerebro.ai.default-chat-provider}.
 * Aliases aceitam nomes legados ({@code conversationId}, {@code userMessage}) para compatibilidade.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatHttpRequest(
        String tenantId,
        @JsonProperty("sessionId") @JsonAlias("conversationId") String sessionId,
        @JsonProperty("message") @JsonAlias("userMessage") String message,
        Integer topK,
        String aiProvider) {}
