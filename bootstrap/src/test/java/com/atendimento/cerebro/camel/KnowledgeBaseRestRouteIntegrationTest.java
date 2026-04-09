package com.atendimento.cerebro.camel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.domain.knowledge.KnowledgeDocument;
import com.atendimento.cerebro.domain.knowledge.KnowledgeFileSummary;
import com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel.KnowledgeBaseFileHttpResponse;
import com.atendimento.cerebro.infrastructure.adapter.out.knowledge.PgVectorKnowledgeBaseAdapter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.vectorstore.pgvector.initialize-schema=true")
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DisabledIfEnvironmentVariable(named = "CEREBRO_IT_USE_LOCAL_PG", matches = "(?i)^\\s*true\\s*$")
class KnowledgeBaseRestRouteIntegrationTest {

    private static final String TENANT = "tenant-kb-it";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private EmbeddingModel embeddingModel;

    private static final String BATCH_ID = "test-batch-integration";

    @BeforeEach
    void setUp() {
        float[] vec = new float[768];
        java.util.Arrays.fill(vec, 1.0f / 768.0f);

        when(embeddingModel.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<Document> docs = invocation.getArgument(0);
                    List<float[]> rows = new ArrayList<>(docs.size());
                    for (int i = 0; i < docs.size(); i++) {
                        rows.add(vec.clone());
                    }
                    return rows;
                });
        when(embeddingModel.embed(anyString())).thenAnswer(invocation -> vec.clone());
        when(embeddingModel.embed(any(Document.class))).thenAnswer(invocation -> vec.clone());

        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenAnswer(invocation -> {
                    EmbeddingRequest req = invocation.getArgument(0);
                    List<Embedding> embeddings = new ArrayList<>();
                    int i = 0;
                    List<String> instructions = req.getInstructions();
                    if (instructions == null || instructions.isEmpty()) {
                        embeddings.add(new Embedding(vec.clone(), 0));
                    } else {
                        for (String ignored : instructions) {
                            embeddings.add(new Embedding(vec.clone(), i++));
                        }
                    }
                    return new EmbeddingResponse(embeddings);
                });
        when(embeddingModel.dimensions()).thenReturn(768);

        jdbcTemplate.update("DELETE FROM vector_store");
        String uploaded = Instant.parse("2026-04-09T10:00:00Z").toString();
        Map<String, Object> m0 = new HashMap<>();
        m0.put(PgVectorKnowledgeBaseAdapter.TENANT_ID_METADATA_KEY, TENANT);
        m0.put(KnowledgeDocument.META_INGESTION_BATCH_ID, BATCH_ID);
        m0.put(KnowledgeDocument.META_SOURCE_FILENAME, "faq.txt");
        m0.put(KnowledgeDocument.META_UPLOADED_AT, uploaded);
        m0.put(KnowledgeDocument.META_FILE_SIZE_BYTES, "500");
        m0.put(KnowledgeDocument.META_CHUNK_INDEX, "0");
        m0.put(KnowledgeDocument.META_CHUNK_COUNT, "2");
        Map<String, Object> m1 = new HashMap<>(m0);
        m1.put(KnowledgeDocument.META_CHUNK_INDEX, "1");
        vectorStore.add(List.of(new Document("chunk0", m0), new Document("chunk1", m1)));
    }

    @Test
    void getKnowledgeBase_returnsFilesForTenant() {
        ResponseEntity<List<KnowledgeBaseFileHttpResponse>> response =
                restTemplate.exchange(
                        "/api/v1/knowledge-base?tenantId=" + TENANT,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        KnowledgeBaseFileHttpResponse row = response.getBody().get(0);
        assertThat(row.batchId()).isEqualTo(BATCH_ID);
        assertThat(row.fileName()).isEqualTo("faq.txt");
        assertThat(row.chunkCount()).isEqualTo(2);
        assertThat(row.status()).isEqualTo(KnowledgeFileSummary.STATUS_READY);
        assertThat(row.sizeBytes()).isEqualTo(500L);
    }

    @Test
    void getKnowledgeBase_withoutTenant_returns400() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/knowledge-base", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteKnowledgeBase_removesVectors() {
        ResponseEntity<Void> del =
                restTemplate.exchange(
                        "/api/v1/knowledge-base/" + BATCH_ID + "?tenantId=" + TENANT,
                        HttpMethod.DELETE,
                        null,
                        Void.class);

        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List<KnowledgeBaseFileHttpResponse>> again =
                restTemplate.exchange(
                        "/api/v1/knowledge-base?tenantId=" + TENANT,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {});
        assertThat(again.getBody()).isEmpty();
    }

    @Test
    void deleteUnknownBatch_returns404() {
        ResponseEntity<String> del =
                restTemplate.exchange(
                        "/api/v1/knowledge-base/nao-existe?tenantId=" + TENANT,
                        HttpMethod.DELETE,
                        null,
                        String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
