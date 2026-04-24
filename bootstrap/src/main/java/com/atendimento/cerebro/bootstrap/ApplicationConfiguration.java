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
import com.atendimento.cerebro.application.port.out.CrmCustomerQueryPort;
import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;
import com.atendimento.cerebro.application.port.out.KnowledgeBasePort;
import com.atendimento.cerebro.application.port.out.AppointmentSchedulingPort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentQueryPort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import com.atendimento.cerebro.application.port.out.TenantConfigurationStorePort;
import com.atendimento.cerebro.application.port.out.TextExtractorPort;
import com.atendimento.cerebro.application.service.AnalyticsService;
import com.atendimento.cerebro.application.service.ChatService;
import com.atendimento.cerebro.application.service.AppointmentService;
import com.atendimento.cerebro.application.service.AppointmentValidationService;
import com.atendimento.cerebro.application.service.LeadScoringService;
import com.atendimento.cerebro.application.service.CrmLeadIntentUpdater;
import com.atendimento.cerebro.application.service.ConversationCategoryAnalyticsService;
import com.atendimento.cerebro.application.service.ConversationPrimaryIntentService;
import com.atendimento.cerebro.application.service.IngestionService;
import com.atendimento.cerebro.application.port.out.FirebaseCustomClaimsPort;
import com.atendimento.cerebro.application.port.out.PortalUserStorePort;
import com.atendimento.cerebro.application.port.out.TenantInviteStorePort;
import com.atendimento.cerebro.application.port.out.WhatsAppTextOutboundPort;
import com.atendimento.cerebro.application.service.AppointmentReminderNotificationService;
import com.atendimento.cerebro.application.service.PortalRegistrationService;
import com.atendimento.cerebro.application.service.TenantSettingsService;
import com.atendimento.cerebro.infrastructure.config.AnalyticsCategorizationProperties;
import com.atendimento.cerebro.infrastructure.config.AnalyticsIntentClassificationProperties;
import com.atendimento.cerebro.infrastructure.config.ChatAnalyticsProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {

    @Bean
    public AppointmentValidationService appointmentValidationService() {
        return new AppointmentValidationService();
    }

    @Bean
    public AppointmentService appointmentService(
            TenantAppointmentQueryPort tenantAppointmentQuery,
            TenantAppointmentStorePort tenantAppointmentStore,
            AppointmentSchedulingPort appointmentSchedulingPort,
            CrmCustomerQueryPort crmCustomerQuery,
            ApplicationEventPublisher applicationEventPublisher) {
        return new AppointmentService(
                tenantAppointmentQuery,
                tenantAppointmentStore,
                appointmentSchedulingPort,
                crmCustomerQuery,
                applicationEventPublisher);
    }

    @Bean
    public AppointmentReminderNotificationService appointmentReminderNotificationService(
            WhatsAppTextOutboundPort whatsAppTextOutboundPort) {
        return new AppointmentReminderNotificationService(whatsAppTextOutboundPort);
    }

    @Bean
    public ChatUseCase chatUseCase(
            ConversationContextStorePort conversationContextStore,
            KnowledgeBasePort knowledgeBase,
            AIEnginePort aiEngine,
            TenantConfigurationStorePort tenantConfigurationStore,
            ConversationBotStatePort conversationBotStatePort,
            CrmCustomerStorePort crmCustomerStore,
            CrmCustomerQueryPort crmCustomerQuery,
            TenantAppointmentQueryPort tenantAppointmentQuery,
            AppointmentSchedulingPort appointmentSchedulingPort,
            AppointmentService appointmentService,
            @Value("${cerebro.google.calendar.zone:America/Bahia}") String schedulingZoneId,
            @Value("${cerebro.appointment.confirmation.whatsapp-suppress-in-chat-when-notifying:true}")
                    boolean whatsappSuppressInChatWhenNotifying) {
        return new ChatService(
                conversationContextStore,
                knowledgeBase,
                aiEngine,
                tenantConfigurationStore,
                conversationBotStatePort,
                crmCustomerStore,
                crmCustomerQuery,
                tenantAppointmentQuery,
                appointmentSchedulingPort,
                appointmentService,
                schedulingZoneId,
                whatsappSuppressInChatWhenNotifying);
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
    public CrmLeadIntentUpdater crmLeadIntentUpdater(CrmCustomerStorePort crmCustomerStore) {
        return new CrmLeadIntentUpdater(crmCustomerStore);
    }

    @Bean
    public LeadScoringService leadScoringService(
            ChatAnalyticsRepository chatAnalyticsRepository,
            ChatMessageRepository chatMessageRepository,
            CrmCustomerStorePort crmCustomerStore) {
        return new LeadScoringService(chatAnalyticsRepository, chatMessageRepository, crmCustomerStore);
    }

    @Bean
    public AnalyticsService analyticsService(
            ChatAnalyticsRepository chatAnalyticsRepository,
            AIEnginePort aiEngine,
            CrmLeadIntentUpdater crmLeadIntentUpdater,
            LeadScoringService leadScoringService,
            ChatAnalyticsProperties chatAnalyticsProperties) {
        return new AnalyticsService(
                chatAnalyticsRepository,
                aiEngine,
                crmLeadIntentUpdater,
                leadScoringService,
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
