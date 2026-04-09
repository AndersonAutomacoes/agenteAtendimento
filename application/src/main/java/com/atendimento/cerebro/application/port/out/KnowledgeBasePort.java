package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.knowledge.KnowledgeDocument;
import com.atendimento.cerebro.domain.knowledge.KnowledgeFileSummary;
import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;

public interface KnowledgeBasePort {

    /** Quantidade fixa de trechos retornados em {@link #findTopThreeRelevantFragments}. */
    int TOP_KNOWLEDGE_FRAGMENTS = 3;

    /**
     * Busca semântica com limite configurável (o armazém vetorial gera o embedding a partir do texto da consulta).
     */
    List<KnowledgeHit> semanticSearch(TenantId tenantId, String query, int topK);

    /**
     * Gera o embedding da pergunta do usuário e recupera os três fragmentos mais relevantes,
     * filtrados pelo tenant. Implementações podem usar uma única inferência de embedding e consulta nativa ao BD.
     */
    default List<KnowledgeHit> findTopThreeRelevantFragments(TenantId tenantId, String userQuestion) {
        return semanticSearch(tenantId, userQuestion, TOP_KNOWLEDGE_FRAGMENTS);
    }

    /**
     * Persiste fragmentos na base vetorial; cada documento deve referenciar o tenant (metadados + contrato).
     */
    void persistKnowledgeDocuments(TenantId tenantId, List<KnowledgeDocument> documents);

    /**
     * Ficheiros indexados por conta (agrupados por {@link KnowledgeDocument#META_INGESTION_BATCH_ID}).
     */
    default List<KnowledgeFileSummary> listUploadedFiles(TenantId tenantId) {
        return List.of();
    }

    /**
     * Remove todos os vectores de um envio. Retorna quantas linhas foram apagadas.
     */
    default int deleteByBatchId(TenantId tenantId, String batchId) {
        return 0;
    }
}
