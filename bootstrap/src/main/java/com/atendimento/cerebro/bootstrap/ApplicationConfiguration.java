package com.atendimento.cerebro.bootstrap;

import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.application.port.out.AIEnginePort;
import com.atendimento.cerebro.application.port.out.ConversationContextStorePort;
import com.atendimento.cerebro.application.port.out.KnowledgeBasePort;
import com.atendimento.cerebro.application.service.ChatService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {

    @Bean
    public ChatUseCase chatUseCase(
            ConversationContextStorePort conversationContextStore,
            KnowledgeBasePort knowledgeBase,
            AIEnginePort aiEngine) {
        return new ChatService(conversationContextStore, knowledgeBase, aiEngine);
    }
}
