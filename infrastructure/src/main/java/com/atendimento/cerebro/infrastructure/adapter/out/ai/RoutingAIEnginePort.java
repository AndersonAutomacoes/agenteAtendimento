package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import com.atendimento.cerebro.application.ai.AiChatProvider;
import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.dto.AICompletionResponse;
import com.atendimento.cerebro.application.port.out.AIEnginePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Encaminha {@link AICompletionRequest} para Gemini ou OpenAI (se ativado) conforme {@link AICompletionRequest#chatProvider()}.
 */
@Component
@Primary
public class RoutingAIEnginePort implements AIEnginePort {

    private final ObjectProvider<OpenAiChatEngineAdapter> openAi;
    private final ObjectProvider<GeminiChatEngineAdapter> gemini;

    public RoutingAIEnginePort(
            ObjectProvider<OpenAiChatEngineAdapter> openAi, ObjectProvider<GeminiChatEngineAdapter> gemini) {
        this.openAi = openAi;
        this.gemini = gemini;
    }

    @Override
    public AICompletionResponse complete(AICompletionRequest request) {
        AiChatProvider provider = request.chatProvider();
        if (provider == null) {
            throw new IllegalStateException("chatProvider em AICompletionRequest não deve ser null");
        }
        return switch (provider) {
            case GEMINI -> {
                GeminiChatEngineAdapter engine = gemini.getIfAvailable();
                if (engine == null) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Motor Gemini não disponível: GeminiChatEngineAdapter não está registado. "
                                    + "O RAG usa embeddings (outra configuração); o Google AI Studio pode mostrar só "
                                    + "pedidos de embedding, não de chat. Confirme spring.ai.google.genai.api-key "
                                    + "e spring.ai.model.chat=google-genai; veja GoogleGenAiChatBeanDiagnostics no arranque.");
                }
                yield engine.complete(request);
            }
            case OPENAI -> {
                OpenAiChatEngineAdapter engine = openAi.getIfAvailable();
                if (engine == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "OpenAI não está ativado. Ative cerebro.ai.openai-chat-enabled=true e credenciais, ou use GEMINI.");
                }
                yield engine.complete(request);
            }
        };
    }
}
