package com.atendimento.cerebro.application.dto;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;

public record TenantAppointmentRecord(
        TenantId tenantId,
        String conversationId,
        String clientName,
        Long serviceId,
        String serviceName,
        Instant startsAt,
        Instant endsAt,
        String googleEventId) {

    public TenantAppointmentRecord {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (clientName == null || clientName.isBlank()) {
            throw new IllegalArgumentException("clientName must not be blank");
        }
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        if (serviceId == null || serviceId <= 0) {
            throw new IllegalArgumentException("serviceId must be provided");
        }
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("startsAt and endsAt are required");
        }
        if (googleEventId == null || googleEventId.isBlank()) {
            throw new IllegalArgumentException("googleEventId must not be blank");
        }
    }
}
