package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Optional;

/**
 * Persistência do vínculo Evolution (nome de instância) ↔ tenant, para resolução em webhooks e provisionamento.
 */
public interface EvolutionInstanceMappingStorePort {

    Optional<TenantId> findTenantIdByEvolutionInstanceName(String evolutionInstanceName);

    void upsert(TenantId tenantId, String evolutionInstanceName);

    void updateConnectionState(String evolutionInstanceName, String connectionState);
}
