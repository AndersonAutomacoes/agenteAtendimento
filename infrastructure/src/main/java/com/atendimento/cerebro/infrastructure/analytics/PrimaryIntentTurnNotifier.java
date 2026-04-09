package com.atendimento.cerebro.infrastructure.analytics;

import com.atendimento.cerebro.application.service.ConversationPrimaryIntentService;
import com.atendimento.cerebro.domain.tenant.TenantId;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class PrimaryIntentTurnNotifier {

    private final ConversationPrimaryIntentService primaryIntentService;

    public PrimaryIntentTurnNotifier(ConversationPrimaryIntentService primaryIntentService) {
        this.primaryIntentService = primaryIntentService;
    }

    @Async
    public void notifyTurnCompleted(TenantId tenantId, String phoneNumber) {
        primaryIntentService.handleTurnCompleted(tenantId, phoneNumber);
    }
}
