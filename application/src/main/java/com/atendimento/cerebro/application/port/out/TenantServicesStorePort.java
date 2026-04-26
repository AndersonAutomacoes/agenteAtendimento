package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.TenantServiceDto;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;

public interface TenantServicesStorePort {

    List<TenantServiceDto> listByTenant(TenantId tenantId);

    /**
     * Insere ou actualiza serviços por (tenant_id, name); o nome é chave lógica.
     */
    void upsertAll(TenantId tenantId, List<TenantServiceDto> items);
}
