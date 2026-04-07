package com.atendimento.cerebro.domain.tenant;

public record TenantId(String value) {
    public TenantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
    }
}
