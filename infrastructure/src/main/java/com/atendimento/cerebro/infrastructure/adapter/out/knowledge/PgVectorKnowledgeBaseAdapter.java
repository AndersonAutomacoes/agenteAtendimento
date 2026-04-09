package com.atendimento.cerebro.infrastructure.adapter.out.knowledge;

import com.atendimento.cerebro.application.port.out.KnowledgeBasePort;
import com.atendimento.cerebro.domain.knowledge.KnowledgeDocument;
import com.atendimento.cerebro.domain.knowledge.KnowledgeFileSummary;
import com.atendimento.cerebro.domain.knowledge.KnowledgeHit;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.pgvector.PGvector;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorFilterExpressionConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * {@link KnowledgeBasePort} com Spring AI + PGVector. Metadados devem incluir {@value #TENANT_ID_METADATA_KEY} por
 * documento. {@link #findTopThreeRelevantFragments} gera o vetor da pergunta via {@link EmbeddingModel} e consulta
 * o PostgreSQL uma vez (sem segunda chamada ao provedor de embeddings).
 */
@Component
public class PgVectorKnowledgeBaseAdapter implements KnowledgeBasePort {

    public static final String TENANT_ID_METADATA_KEY = "tenant_id";

    /**
     * Mesmos templates que {@code PgDistanceType} no Spring AI (ordem dos placeholders: tabela, filtro jsonpath).
     * Mantidos aqui porque {@code PgDistanceType} não é exposto como API pública no módulo publicado.
     */
    private static final Map<String, String> SIMILARITY_SQL_BY_DISTANCE_TYPE = Map.of(
            "COSINE_DISTANCE",
            "SELECT *, embedding <=> ? AS distance FROM %s WHERE embedding <=> ? < ? %s ORDER BY distance LIMIT ? ",
            "EUCLIDEAN_DISTANCE",
            "SELECT *, embedding <-> ? AS distance FROM %s WHERE embedding <-> ? < ? %s ORDER BY distance LIMIT ? ",
            "NEGATIVE_INNER_PRODUCT",
            "SELECT *, (1 + (embedding <#> ?)) AS distance FROM %s WHERE (1 + (embedding <#> ?)) < ? %s ORDER BY distance LIMIT ? ");

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;
    private final String qualifiedVectorTable;
    private final String similaritySearchSqlTemplate;
    private final PgVectorFilterExpressionConverter filterConverter = new PgVectorFilterExpressionConverter();

    public PgVectorKnowledgeBaseAdapter(
            VectorStore vectorStore,
            EmbeddingModel embeddingModel,
            JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schemaName,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName,
            @Value("${spring.ai.vectorstore.pgvector.distance-type:COSINE_DISTANCE}") String distanceTypeName) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.jdbcTemplate = jdbcTemplate;
        this.qualifiedVectorTable = schemaName + "." + tableName;
        this.similaritySearchSqlTemplate = SIMILARITY_SQL_BY_DISTANCE_TYPE.getOrDefault(
                distanceTypeName, SIMILARITY_SQL_BY_DISTANCE_TYPE.get("COSINE_DISTANCE"));
    }

    /**
     * Gera o embedding da pergunta e busca os 3 fragmentos mais similares, filtrando por {@code tenant_id} no
     * metadata JSONB.
     */
    @Override
    public List<KnowledgeHit> findTopThreeRelevantFragments(TenantId tenantId, String userQuestion) {
        float[] queryVector = embeddingModel.embed(userQuestion);
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalStateException("Falha ao gerar embedding da pergunta do usuário");
        }

        Filter.Expression tenantFilter =
                new FilterExpressionBuilder().eq(TENANT_ID_METADATA_KEY, tenantId.value()).build();
        String nativeFilter = filterConverter.convertExpression(tenantFilter);
        String jsonPathFilter = "";
        if (StringUtils.hasText(nativeFilter)) {
            jsonPathFilter = " AND metadata::jsonb @@ '" + nativeFilter + "'::jsonpath ";
        }

        double distanceCutoff = 1.0 - SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL;
        String sql = String.format(similaritySearchSqlTemplate, qualifiedVectorTable, jsonPathFilter);

        PGvector embeddingParam = new PGvector(queryVector);
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String id = rs.getString("id");
                    String text = rs.getString("content");
                    float dist = rs.getFloat("distance");
                    return new KnowledgeHit(id, text != null ? text : "", (double) (1.0f - dist));
                },
                embeddingParam,
                embeddingParam,
                distanceCutoff,
                KnowledgeBasePort.TOP_KNOWLEDGE_FRAGMENTS);
    }

    @Override
    public List<KnowledgeHit> semanticSearch(TenantId tenantId, String query, int topK) {
        Filter.Expression tenantFilter =
                new FilterExpressionBuilder().eq(TENANT_ID_METADATA_KEY, tenantId.value()).build();

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL)
                .filterExpression(tenantFilter)
                .build();

        return vectorStore.similaritySearch(request).stream()
                .map(PgVectorKnowledgeBaseAdapter::toKnowledgeHit)
                .toList();
    }

    @Override
    public void persistKnowledgeDocuments(TenantId tenantId, List<KnowledgeDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<Document> springDocs = new ArrayList<>(documents.size());
        for (KnowledgeDocument kd : documents) {
            Map<String, Object> meta = new HashMap<>();
            for (var e : kd.metadata().entrySet()) {
                meta.put(e.getKey(), e.getValue());
            }
            meta.put(TENANT_ID_METADATA_KEY, tenantId.value());
            springDocs.add(Document.builder().id(kd.id()).text(kd.content()).metadata(meta).build());
        }
        vectorStore.add(springDocs);
    }

    private static KnowledgeHit toKnowledgeHit(Document doc) {
        String text = doc.isText() && doc.getText() != null ? doc.getText() : "";
        return new KnowledgeHit(doc.getId(), text, doc.getScore());
    }

    @Override
    public List<KnowledgeFileSummary> listUploadedFiles(TenantId tenantId) {
        /*
         * metadata no Spring AI pode ser json ou jsonb; normalizar com ::jsonb evita falhas de operador
         * e alinha-se ao DELETE abaixo.
         */
        String sql =
                """
                SELECT
                  (metadata::jsonb)->>'ingestion_batch_id' AS batch_id,
                  MAX((metadata::jsonb)->>'source_filename') AS file_name,
                  MAX((metadata::jsonb)->>'uploaded_at') AS uploaded_at,
                  COALESCE(MAX(
                      CASE
                        WHEN NULLIF((metadata::jsonb)->>'file_size_bytes', '') IS NOT NULL
                        THEN ((metadata::jsonb)->>'file_size_bytes')::bigint
                        ELSE 0
                      END
                  ), 0) AS size_bytes,
                  COUNT(*)::int AS chunk_count
                FROM """
                        + " "
                        + qualifiedVectorTable
                        + """
                 WHERE (metadata::jsonb)->>'tenant_id' = ?
                   AND COALESCE((metadata::jsonb)->>'ingestion_batch_id', '') <> ''
                 GROUP BY (metadata::jsonb)->>'ingestion_batch_id'
                 ORDER BY MAX((metadata::jsonb)->>'uploaded_at') DESC NULLS LAST
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String batchId = rs.getString("batch_id");
                    String fileName = rs.getString("file_name");
                    String uploadedAtRaw = rs.getString("uploaded_at");
                    Instant uploadedAt = parseUploadedAt(uploadedAtRaw);
                    long sizeBytes = rs.getLong("size_bytes");
                    int chunkCount = rs.getInt("chunk_count");
                    return new KnowledgeFileSummary(
                            batchId,
                            fileName != null ? fileName : "",
                            uploadedAt,
                            sizeBytes,
                            chunkCount,
                            KnowledgeFileSummary.STATUS_READY);
                },
                tenantId.value());
    }

    private static Instant parseUploadedAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(raw.strip());
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    @Override
    public int deleteByBatchId(TenantId tenantId, String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return 0;
        }
        String trimmedBatch = batchId.strip();
        String sql =
                "DELETE FROM "
                        + qualifiedVectorTable
                        + " WHERE metadata::jsonb @> jsonb_build_object('tenant_id', ?, 'ingestion_batch_id', ?)";
        return jdbcTemplate.update(sql, tenantId.value(), trimmedBatch);
    }
}
