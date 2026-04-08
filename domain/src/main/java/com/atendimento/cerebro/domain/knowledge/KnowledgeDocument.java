package com.atendimento.cerebro.domain.knowledge;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Fragmento de conhecimento para ingestão e armazenamento vetorial: texto, tenant, metadados (ex.: ficheiro de
 * origem, índice do chunk) e embedding opcional (preenchido quando materializado antes da persistência).
 */
public record KnowledgeDocument(
        String id,
        String content,
        TenantId tenantId,
        Map<String, String> metadata,
        Optional<float[]> embedding) {

    public static final String META_SOURCE_FILENAME = "source_filename";
    public static final String META_CHUNK_INDEX = "chunk_index";
    public static final String META_CHUNK_COUNT = "chunk_count";

    public KnowledgeDocument {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(metadata);
        embedding = embedding != null ? embedding : Optional.empty();
    }
}
