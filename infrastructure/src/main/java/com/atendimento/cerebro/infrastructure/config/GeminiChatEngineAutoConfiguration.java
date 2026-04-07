package com.atendimento.cerebro.infrastructure.config;

import com.atendimento.cerebro.infrastructure.adapter.out.ai.GeminiChatEngineAdapter;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Regista {@link GeminiChatEngineAdapter} <strong>depois</strong> do Spring AI criar
 * {@code googleGenAiChatModel}. Um {@code @Component} com {@code @ConditionalOnBean} no mesmo bean pode ser
 * avaliado demasiado cedo (antes do auto-config), pelo que o adapter nunca era criado apesar do modelo existir.
 */
@AutoConfiguration(after = GoogleGenAiChatAutoConfiguration.class)
@ConditionalOnClass(GoogleGenAiChatModel.class)
public class GeminiChatEngineAutoConfiguration {

    @Bean
    @ConditionalOnBean(GoogleGenAiChatModel.class)
    public GeminiChatEngineAdapter geminiChatEngineAdapter(
            @Qualifier("googleGenAiChatModel") GoogleGenAiChatModel chatModel,
            @Value("${spring.ai.google.genai.chat.options.model:gemini-1.5-flash}") String chatModelName) {
        return new GeminiChatEngineAdapter(chatModel, chatModelName);
    }
}
