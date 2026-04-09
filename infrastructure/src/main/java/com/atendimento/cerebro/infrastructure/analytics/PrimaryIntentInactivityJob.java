package com.atendimento.cerebro.infrastructure.analytics;

import com.atendimento.cerebro.application.service.ConversationPrimaryIntentService;
import com.atendimento.cerebro.infrastructure.config.AnalyticsIntentClassificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PrimaryIntentInactivityJob {

    private static final Logger LOG = LoggerFactory.getLogger(PrimaryIntentInactivityJob.class);

    private final ConversationPrimaryIntentService primaryIntentService;
    private final AnalyticsIntentClassificationProperties properties;

    public PrimaryIntentInactivityJob(
            ConversationPrimaryIntentService primaryIntentService,
            AnalyticsIntentClassificationProperties properties) {
        this.primaryIntentService = primaryIntentService;
        this.properties = properties;
    }

    /** A cada 5 minutos UTC — conversas cuja última mensagem excedeu o limiar de inatividade. */
    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void classifyInactiveSessions() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            primaryIntentService.classifyStaleConversations();
        } catch (RuntimeException e) {
            LOG.warn("Primary intent inactivity job falhou: {}", e.toString());
        }
    }
}
