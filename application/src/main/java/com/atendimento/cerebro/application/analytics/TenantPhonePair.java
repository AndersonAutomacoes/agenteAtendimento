package com.atendimento.cerebro.application.analytics;

import com.atendimento.cerebro.domain.tenant.TenantId;

public record TenantPhonePair(TenantId tenantId, String phoneNumber) {

    public TenantPhonePair {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phoneNumber must not be blank");
        }
    }
}
