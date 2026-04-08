package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.ingestion.TextChunker;
import com.atendimento.cerebro.application.port.in.IngestionUseCase;
import com.atendimento.cerebro.application.port.out.KnowledgeBasePort;
import com.atendimento.cerebro.application.port.out.TextExtractorPort;
import com.atendimento.cerebro.domain.knowledge.KnowledgeDocument;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class IngestionService implements IngestionUseCase {

    private final TextExtractorPort textExtractor;
    private final KnowledgeBasePort knowledgeBase;

    public IngestionService(TextExtractorPort textExtractor, KnowledgeBasePort knowledgeBase) {
        this.textExtractor = textExtractor;
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public int ingest(TenantId tenantId, byte[] fileContent, String originalFilename) {
        if (fileContent == null || fileContent.length == 0) {
            return 0;
        }
        String name = originalFilename != null && !originalFilename.isBlank() ? originalFilename : "upload";
        String fullText = textExtractor.extract(fileContent, name);
        if (fullText == null || fullText.isBlank()) {
            return 0;
        }
        String normalized = fullText.strip();
        if (normalized.isEmpty()) {
            return 0;
        }

        List<String> pieces = TextChunker.chunk(
                normalized, TextChunker.DEFAULT_CHUNK_SIZE, TextChunker.DEFAULT_OVERLAP);
        if (pieces.isEmpty()) {
            return 0;
        }

        int total = pieces.size();
        List<KnowledgeDocument> documents = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            Map<String, String> meta = new HashMap<>();
            meta.put(KnowledgeDocument.META_SOURCE_FILENAME, name);
            meta.put(KnowledgeDocument.META_CHUNK_INDEX, String.valueOf(i));
            meta.put(KnowledgeDocument.META_CHUNK_COUNT, String.valueOf(total));

            String id = UUID.randomUUID().toString();
            documents.add(new KnowledgeDocument(id, pieces.get(i), tenantId, meta, Optional.empty()));
        }

        knowledgeBase.persistKnowledgeDocuments(tenantId, documents);
        return total;
    }
}
