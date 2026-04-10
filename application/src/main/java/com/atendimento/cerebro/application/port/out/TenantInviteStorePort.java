package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Optional;

public interface TenantInviteStorePort {

    /**
     * Valida o hash do convite, incrementa {@code uses_count} de forma atómica e devolve o tenant se ainda houver vagas.
     */
    Optional<TenantId> consumeInviteReturningTenant(String codeHash);
}
