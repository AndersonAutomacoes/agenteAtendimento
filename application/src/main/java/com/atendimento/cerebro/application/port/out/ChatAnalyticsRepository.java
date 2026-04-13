package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.analytics.ChatAnalyticsAggregates;
import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import com.atendimento.cerebro.application.analytics.ChatSentiment;
import com.atendimento.cerebro.application.dto.ChatAnalyticsLeadSnapshot;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;

public interface ChatAnalyticsRepository {

    void upsert(TenantId tenantId, String phoneNumber, ChatMainIntent mainIntent, ChatSentiment sentiment);

    /** Momento da primeira gravação analytics (âncora para bónus de engajamento). */
    Optional<Instant> getFirstAnalyticsAt(TenantId tenantId, String phoneNumber);

    /** Intenção atual e âncora para contagem de trocas (mensagens após a primeira analytics). */
    Optional<ChatAnalyticsLeadSnapshot> findLeadSnapshot(TenantId tenantId, String phoneNumber);

    ChatAnalyticsAggregates aggregateForTenant(TenantId tenantId);

    /** Agregação em {@code [start, end)} sobre {@code last_updated}. */
    ChatAnalyticsAggregates aggregateForTenant(TenantId tenantId, Instant start, Instant end);
}
