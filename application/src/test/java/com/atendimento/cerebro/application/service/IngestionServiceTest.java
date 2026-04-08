package com.atendimento.cerebro.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.port.out.KnowledgeBasePort;
import com.atendimento.cerebro.application.port.out.TextExtractorPort;
import com.atendimento.cerebro.domain.knowledge.KnowledgeDocument;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private TextExtractorPort textExtractor;

    @Mock
    private KnowledgeBasePort knowledgeBase;

    private IngestionService service;

    @BeforeEach
    void setUp() {
        service = new IngestionService(textExtractor, knowledgeBase);
    }

    @Test
    void ingest_emptyFile_returnsZero() {
        int n = service.ingest(new TenantId("t1"), new byte[0], "f.txt");
        assertThat(n).isZero();
    }

    @Test
    void ingest_persistsChunks() {
        when(textExtractor.extract(any(), eq("doc.txt"))).thenReturn("a".repeat(1200));

        int n = service.ingest(new TenantId("tenant-a"), "x".getBytes(), "doc.txt");

        assertThat(n).isEqualTo(2);
        ArgumentCaptor<List<KnowledgeDocument>> captor = ArgumentCaptor.captor();
        verify(knowledgeBase).persistKnowledgeDocuments(eq(new TenantId("tenant-a")), captor.capture());
        List<KnowledgeDocument> docs = captor.getValue();
        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).metadata().get(KnowledgeDocument.META_CHUNK_COUNT)).isEqualTo("2");
        assertThat(docs.get(1).metadata().get(KnowledgeDocument.META_CHUNK_INDEX)).isEqualTo("1");
    }
}
