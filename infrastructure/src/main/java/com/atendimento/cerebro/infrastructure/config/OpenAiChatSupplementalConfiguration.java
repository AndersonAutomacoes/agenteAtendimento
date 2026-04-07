package com.atendimento.cerebro.infrastructure.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.model.openai.autoconfigure.OpenAIAutoConfigurationUtil;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Quando {@code spring.ai.model.chat=google-genai}, o auto-config OpenAI de chat não regista estes beans.
 * Só são criados se {@code cerebro.ai.openai-chat-enabled=true} (desativado por defeito — só Gemini em produção).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OpenAiConnectionProperties.class, OpenAiChatProperties.class})
@ConditionalOnProperty(prefix = "cerebro.ai", name = "openai-chat-enabled", havingValue = "true")
public class OpenAiChatSupplementalConfiguration {

    @Bean
    @ConditionalOnMissingBean(OpenAiApi.class)
    public OpenAiApi openAiApi(
            OpenAiConnectionProperties connectionProperties,
            OpenAiChatProperties chatProperties,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ObjectProvider<WebClient.Builder> webClientBuilderProvider,
            ResponseErrorHandler responseErrorHandler) {
        var resolved =
                OpenAIAutoConfigurationUtil.resolveConnectionProperties(connectionProperties, chatProperties, "chat");
        var builder = OpenAiApi.builder()
                .baseUrl(resolved.baseUrl())
                .apiKey(new SimpleApiKey(resolved.apiKey()))
                .headers(resolved.headers())
                .completionsPath(chatProperties.getCompletionsPath())
                .embeddingsPath("/v1/embeddings")
                .responseErrorHandler(responseErrorHandler);
        restClientBuilderProvider.ifAvailable(builder::restClientBuilder);
        webClientBuilderProvider.ifAvailable(builder::webClientBuilder);
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "openAiChatModel")
    public OpenAiChatModel openAiChatModel(
            OpenAiApi openAiApi,
            OpenAiChatProperties chatProperties,
            ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<ChatModelObservationConvention> observationConvention,
            ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate) {
        OpenAiChatOptions options = chatProperties.getOptions();
        if (options == null) {
            options = OpenAiChatOptions.builder().build();
        }
        var builder = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate);
        toolExecutionEligibilityPredicate.ifAvailable(builder::toolExecutionEligibilityPredicate);
        observationRegistry.ifAvailable(builder::observationRegistry);
        OpenAiChatModel model = builder.build();
        observationConvention.ifAvailable(model::setObservationConvention);
        return model;
    }
}
