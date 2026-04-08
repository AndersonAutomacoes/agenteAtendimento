package com.atendimento.cerebro.infrastructure.adapter.out.ai;

import com.atendimento.cerebro.application.dto.AICompletionRequest;
import com.atendimento.cerebro.application.dto.AICompletionResponse;
import com.atendimento.cerebro.domain.conversation.Message;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/** Motor de chat OpenAI (Spring AI). Presente quando existe bean {@code openAiChatModel}. */
@Component
@ConditionalOnBean(name = "openAiChatModel")
public class OpenAiChatEngineAdapter {

    public static final String DEFAULT_MODEL = "gpt-4o";

    private final OpenAiChatModel chatModel;
    private final String chatModelName;

    public OpenAiChatEngineAdapter(
            @Qualifier("openAiChatModel") OpenAiChatModel chatModel,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o}") String chatModelName) {
        this.chatModel = chatModel;
        this.chatModelName = chatModelName != null && !chatModelName.isBlank() ? chatModelName : DEFAULT_MODEL;
    }

    public AICompletionResponse complete(AICompletionRequest request) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

        messages.add(new SystemMessage(buildSystemContent(request)));

        if (!CollectionUtils.isEmpty(request.conversationHistory())) {
            for (Message m : request.conversationHistory()) {
                messages.add(toSpringMessage(m));
            }
        }

        messages.add(new UserMessage(request.userMessage()));

        OpenAiChatOptions options = OpenAiChatOptions.builder().model(chatModelName).build();
        ChatResponse response = chatModel.call(new Prompt(messages, options));

        AssistantMessage output = response.getResult().getOutput();
        String content = output.getText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Resposta vazia do modelo de chat");
        }
        return new AICompletionResponse(content.strip());
    }

    private String buildSystemContent(AICompletionRequest request) {
        return RagSystemPromptComposer.compose(request.systemPrompt(), request.knowledgeHits());
    }

    private static org.springframework.ai.chat.messages.Message toSpringMessage(Message m) {
        return switch (m.role()) {
            case USER -> new UserMessage(m.content());
            case ASSISTANT -> new AssistantMessage(m.content());
            case SYSTEM -> new SystemMessage(m.content());
        };
    }
}
