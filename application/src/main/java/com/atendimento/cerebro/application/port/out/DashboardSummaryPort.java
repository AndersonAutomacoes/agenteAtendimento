package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.DashboardRange;
import com.atendimento.cerebro.application.dto.DashboardSummary;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;

public interface DashboardSummaryPort {

    DashboardSummary load(TenantId tenantId, DashboardRange range);

    /** Resumo para intervalo meia-aberto {@code [start, end)} (ISO instant). */
    DashboardSummary loadForPeriod(TenantId tenantId, Instant start, Instant end);
}
