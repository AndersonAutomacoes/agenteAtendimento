package com.atendimento.cerebro.application.port.in;

import com.atendimento.cerebro.application.dto.TenantSettingsUpdateCommand;
import com.atendimento.cerebro.domain.tenant.TenantId;

public interface UpdateTenantSettingsUseCase {

    void updateTenantSettings(TenantId tenantId, TenantSettingsUpdateCommand command);
}
