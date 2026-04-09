package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.DashboardRange;
import com.atendimento.cerebro.application.dto.DashboardSummary;
import com.atendimento.cerebro.domain.tenant.TenantId;

public interface DashboardSummaryPort {

    DashboardSummary load(TenantId tenantId, DashboardRange range);
}
