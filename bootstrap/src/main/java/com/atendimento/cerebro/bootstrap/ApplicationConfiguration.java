package com.atendimento.cerebro.bootstrap;

import com.atendimento.cerebro.application.port.in.ChatUseCase;
import com.atendimento.cerebro.application.port.in.IngestionUseCase;
import com.atendimento.cerebro.application.port.in.UpdateTenantSettingsUseCase;
import com.atendimento.cerebro.application.port.out.AIEnginePort;
import com.atendimento.cerebro.application.port.out.AnalyticsIntentsRepository;
import com.atendimento.cerebro.application.port.out.AnalyticsStatsRepository;
import com.atendimento.cerebro.application.port.out.ChatAnalyticsRepository;
import com.atendimento.cerebro.application.port.out.ChatMessageRepository;
import com.atendimento.cerebro.application.port.out.ConversationBotStatePort;
import com.atendimento.cerebro.application.port.out.ConversationContextStorePort;
import com.atendimento.cerebro.application.port.out.KnowledgeBasePort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.application.port.out.TextExtractorPort;
import com.atendimento.cerebro.application.service.AnalyticsService;
import com.atendimento.cerebro.application.service.ChatService;
import com.atendimento.cerebro.application.service.ConversationCategoryAnalyticsService;
import com.atendimento.cerebro.application.service.ConversationPrimaryIntentService;
import com.atendimento.cerebro.application.service.IngestionService;
import com.atendimento.cerebro.application.port.out.FirebaseCustomClaimsPort;
import com.atendimento.cerebro.application.port.out.PortalUserStorePort;
import com.atendimento.cerebro.application.port.out.TenantInviteStorePort;
import com.atendimento.cerebro.application.service.PortalRegistrationService;
import com.atendimento.cerebro.application.service.TenantSettingsService;
import com.atendimento.cerebro.infrastructure.config.AnalyticsCategorizationProperties;
import com.atendimento.cerebro.infrastructure.config.AnalyticsIntentClassificationProperties;
import com.atendimento.cerebro.infrastructure.config.ChatAnalyticsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {

    @Bean
    public ChatUseCase chatUseCase(
            ConversationContextStorePort conversationContextStore,
            KnowledgeBasePort knowledgeBase,
            AIEnginePort aiEngine,
            TenantConfigurationStorePort tenantConfigurationStore,
            ConversationBotStatePort conversationBotStatePort) {
        return new ChatService(
                conversationContextStore, knowledgeBase, aiEngine, tenantConfigurationStore, conversationBotStatePort);
    }

    @Bean
    public IngestionUseCase ingestionUseCase(TextExtractorPort textExtractor, KnowledgeBasePort knowledgeBase) {
        return new IngestionService(textExtractor, knowledgeBase);
    }

    @Bean
    public UpdateTenantSettingsUseCase updateTenantSettingsUseCase(TenantConfigurationStorePort tenantConfigurationStore) {
        return new TenantSettingsService(tenantConfigurationStore);
    }

    @Bean
    public PortalRegistrationService portalRegistrationService(
            TenantInviteStorePort tenantInviteStore,
            PortalUserStorePort portalUserStore,
            FirebaseCustomClaimsPort firebaseCustomClaims) {
        return new PortalRegistrationService(tenantInviteStore, portalUserStore, firebaseCustomClaims);
    }

    @Bean
    public ConversationCategoryAnalyticsService conversationCategoryAnalyticsService(
            ChatMessageRepository chatMessageRepository,
            AnalyticsStatsRepository analyticsStatsRepository,
            AIEnginePort aiEngine,
            AnalyticsCategorizationProperties categorizationProperties) {
        return new ConversationCategoryAnalyticsService(
                chatMessageRepository,
                analyticsStatsRepository,
                aiEngine,
                categorizationProperties.getMaxConversationsPerRun(),
                categorizationProperties.getMaxUserTextChars());
    }

    @Bean
    public AnalyticsService analyticsService(
            ChatAnalyticsRepository chatAnalyticsRepository,
            AIEnginePort aiEngine,
            ChatAnalyticsProperties chatAnalyticsProperties) {
        return new AnalyticsService(
                chatAnalyticsRepository,
                aiEngine,
                chatAnalyticsProperties.isEnabled(),
                chatAnalyticsProperties.getMaxTranscriptChars());
    }

    @Bean
    public ConversationPrimaryIntentService conversationPrimaryIntentService(
            ChatMessageRepository chatMessageRepository,
            AnalyticsIntentsRepository analyticsIntentsRepository,
            AIEnginePort aiEngine,
            AnalyticsIntentClassificationProperties properties) {
        return new ConversationPrimaryIntentService(
                chatMessageRepository,
                analyticsIntentsRepository,
                aiEngine,
                properties.isEnabled(),
                properties.getMessageThreshold(),
                properties.getSessionLookbackDays(),
                properties.getMaxTranscriptMessages(),
                properties.getMaxTranscriptChars(),
                properties.isInactivityClassificationEnabled(),
                properties.getInactivityMinutes(),
                properties.getMaxClassificationsPerRun());
    }
}
