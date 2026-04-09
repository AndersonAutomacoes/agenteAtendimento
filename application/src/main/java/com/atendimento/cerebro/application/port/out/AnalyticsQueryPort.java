package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.analytics.AnalyticsSummaryResult;
import com.atendimento.cerebro.application.analytics.HourlyMessageVolumePoint;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;

public interface AnalyticsQueryPort {

    /**
     * Métricas para as últimas 24 horas (janela [agora − 24h, agora)).
     */
    AnalyticsSummaryResult summaryLast24Hours(TenantId tenantId);

    /**
     * Volume de mensagens por hora UTC, com buckets contínuos (zeros onde não houve tráfego).
     *
     * @param hours entre 1 e 168
     */
    List<HourlyMessageVolumePoint> hourlyMessageVolume(TenantId tenantId, int hours);
}
