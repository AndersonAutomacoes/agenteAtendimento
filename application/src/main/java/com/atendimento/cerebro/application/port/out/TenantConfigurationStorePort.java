package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.TenantConfiguration;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Optional;

public interface TenantConfigurationStorePort {

    Optional<TenantConfiguration> findByTenantId(TenantId tenantId);

    void upsert(TenantConfiguration configuration);
}
