package com.atendimento.cerebro.application.service.billing;

import com.atendimento.cerebro.application.dto.billing.StripeSubscriptionMirrorCommand;
import com.atendimento.cerebro.application.port.out.TenantSubscriptionPersistencePort;
import org.springframework.stereotype.Service;

@Service
public class BillingSubscriptionSyncService {

    private final TenantSubscriptionPersistencePort tenantSubscriptionPersistence;

    public BillingSubscriptionSyncService(TenantSubscriptionPersistencePort tenantSubscriptionPersistence) {
        this.tenantSubscriptionPersistence = tenantSubscriptionPersistence;
    }

    public void syncFromStripe(StripeSubscriptionMirrorCommand command) {
        tenantSubscriptionPersistence.upsertAndApplyProfileLevel(command.toSnapshot());
    }
}
