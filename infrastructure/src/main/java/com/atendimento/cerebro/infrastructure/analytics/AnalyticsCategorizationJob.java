package com.atendimento.cerebro.infrastructure.analytics;

import com.atendimento.cerebro.application.service.ConversationCategoryAnalyticsService;
import com.atendimento.cerebro.infrastructure.config.AnalyticsCategorizationProperties;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsCategorizationJob {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsCategorizationJob.class);

    private final ConversationCategoryAnalyticsService categorizationService;
    private final AnalyticsCategorizationProperties properties;

    public AnalyticsCategorizationJob(
            ConversationCategoryAnalyticsService categorizationService,
            AnalyticsCategorizationProperties properties) {
        this.categorizationService = categorizationService;
        this.properties = properties;
    }

    /** Início de cada hora em UTC; classifica a hora civil anterior [H-1, H). */
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void classifyPreviousUtcHour() {
        if (!properties.isEnabled()) {
            return;
        }
        Instant hourEnd =
                Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).toInstant();
        Instant hourStart = hourEnd.minus(1, ChronoUnit.HOURS);
        LOG.debug("Analytics categorization job: janela UTC [{}, {})", hourStart, hourEnd);
        categorizationService.classifyForWindow(hourStart, hourEnd);
    }
}
