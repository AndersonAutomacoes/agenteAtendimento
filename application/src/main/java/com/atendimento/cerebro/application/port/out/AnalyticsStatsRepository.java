package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.analytics.ConversationAnalyticsCategory;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;

public interface AnalyticsStatsRepository {

    boolean existsForBucketAndPhone(TenantId tenantId, Instant bucketHourUtc, String phoneNumber);

    /**
     * Insere classificação; devolve false se já existia (unique) ou conflito.
     */
    boolean insertIfAbsent(
            TenantId tenantId,
            Instant bucketHourUtc,
            String phoneNumber,
            ConversationAnalyticsCategory category,
            String modelLabel);
}
