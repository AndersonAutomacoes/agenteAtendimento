package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.domain.knowledge.KnowledgeFileSummary;
import java.time.format.DateTimeFormatter;

/**
 * DTO JSON para listagem da base de conhecimento (datas em ISO-8601), compatível com o marshaller Camel/Jackson
 * sem depender de {@link java.time.Instant} no corpo.
 */
public record KnowledgeBaseFileHttpResponse(
        String batchId,
        String fileName,
        String uploadedAt,
        long sizeBytes,
        int chunkCount,
        String status) {

    static KnowledgeBaseFileHttpResponse from(KnowledgeFileSummary s) {
        String at =
                s.uploadedAt() != null
                        ? DateTimeFormatter.ISO_INSTANT.format(s.uploadedAt())
                        : "";
        return new KnowledgeBaseFileHttpResponse(
                s.batchId(), s.fileName(), at, s.sizeBytes(), s.chunkCount(), s.status());
    }
}
