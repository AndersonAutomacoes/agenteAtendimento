package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;

public interface TenantServiceCatalogPort {
    Optional<Long> findServiceIdByName(TenantId tenantId, String serviceName);

    List<String> listActiveServiceNames(TenantId tenantId);
}
