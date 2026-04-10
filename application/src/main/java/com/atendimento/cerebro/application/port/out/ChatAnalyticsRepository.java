package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.analytics.ChatAnalyticsAggregates;
import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import com.atendimento.cerebro.application.analytics.ChatSentiment;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;

public interface ChatAnalyticsRepository {

    void upsert(TenantId tenantId, String phoneNumber, ChatMainIntent mainIntent, ChatSentiment sentiment);

    ChatAnalyticsAggregates aggregateForTenant(TenantId tenantId);

    /** Agregação em {@code [start, end)} sobre {@code last_updated}. */
    ChatAnalyticsAggregates aggregateForTenant(TenantId tenantId, Instant start, Instant end);
}
