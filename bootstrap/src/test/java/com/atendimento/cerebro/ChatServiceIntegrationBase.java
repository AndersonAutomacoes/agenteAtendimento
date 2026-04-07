package com.atendimento.cerebro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.dto.AICompletionResponse;
import com.atendimento.cerebro.application.dto.ChatCommand;
import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.application.port.out.AIEnginePort;
import com.atendimento.cerebro.application.port.out.ConversationContextStorePort;
import com.atendimento.cerebro.domain.conversation.ConversationId;
import com.atendimento.cerebro.domain.conversation.MessageRole;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.atendimento.cerebro.infrastructure.adapter.out.knowledge.PgVectorKnowledgeBaseAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cenário comum aos testes de integração do fluxo de chat (KB vetorial + mock da IA).
 */
public abstract class ChatServiceIntegrationBase {

    protected static final String TENANT = "integration-tenant";
    protected static final String SESSION = "integration-session";

    @MockBean
    protected AIEnginePort aiEnginePort;

    @MockBean
    protected EmbeddingModel embeddingModel;

    @Autowired
    protected ChatUseCase chatUseCase;

    @Autowired
    protected VectorStore vectorStore;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ConversationContextStorePort conversationContextStore;

    @BeforeEach
    void setUp() {
        float[] vec = new float[1536];
        Arrays.fill(vec, 1.0f / 1536.0f);

        // PgVectorStore.add usa embed(List<Document>, …), não call(EmbeddingRequest).
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
        when(embeddingModel.dimensions()).thenReturn(1536);

        when(aiEnginePort.complete(any())).thenAnswer(invocation -> {
            AICompletionRequest req = invocation.getArgument(0);
            assertThat(req.knowledgeHits()).isNotEmpty();
            String first = req.knowledgeHits().get(0).content();
            return new AICompletionResponse("IA mock: " + first.substring(0, Math.min(first.length(), 40)));
        });

        jdbcTemplate.update("DELETE FROM conversation_message");
        jdbcTemplate.update("DELETE FROM vector_store");

        vectorStore.add(List.of(new Document(
                "Política: devolução em até 7 dias corridos para produtos lacrados.",
                Map.of(PgVectorKnowledgeBaseAdapter.TENANT_ID_METADATA_KEY, TENANT))));
    }

    @Test
    void chat_persistsContext_vectorSearchFeedsAi_mockOpenAiPort() {
        var tenantId = new TenantId(TENANT);
        var conversationId = new ConversationId(SESSION);

        var result = chatUseCase.chat(new ChatCommand(tenantId, conversationId, "Qual é a política de devolução?"));

        assertThat(result.assistantMessage()).startsWith("IA mock:");

        var saved = conversationContextStore.load(tenantId, conversationId).orElseThrow();
        assertThat(saved.getMessages()).hasSize(2);
        assertThat(saved.getMessages().get(0).role()).isEqualTo(MessageRole.USER);
        assertThat(saved.getMessages().get(1).role()).isEqualTo(MessageRole.ASSISTANT);

        ArgumentCaptor<AICompletionRequest> aiCaptor = ArgumentCaptor.forClass(AICompletionRequest.class);
        verify(aiEnginePort).complete(aiCaptor.capture());
        AICompletionRequest sent = aiCaptor.getValue();
        assertThat(sent.knowledgeHits())
                .anyMatch(hit -> hit.content().toLowerCase().contains("devolução"));
        assertThat(sent.userMessage().toLowerCase()).contains("devolução");
        assertThat(sent.chatProvider()).isEqualTo(AiChatProvider.GEMINI);
    }
}
