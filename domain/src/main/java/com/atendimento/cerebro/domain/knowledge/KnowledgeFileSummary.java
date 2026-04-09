package com.atendimento.cerebro.domain.knowledge;

import java.time.Instant;

/** Linha agregada por envio ({@link KnowledgeDocument#META_INGESTION_BATCH_ID}) na listagem da base vetorial. */
public record KnowledgeFileSummary(
        String batchId,
        String fileName,
        Instant uploadedAt,
        long sizeBytes,
        int chunkCount,
        String status) {

    public static final String STATUS_READY = "READY";
}
