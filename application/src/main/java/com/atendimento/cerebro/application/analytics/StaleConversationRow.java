package com.atendimento.cerebro.application.analytics;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;

public record StaleConversationRow(TenantId tenantId, String phoneNumber, Instant lastActivityAt) {}
