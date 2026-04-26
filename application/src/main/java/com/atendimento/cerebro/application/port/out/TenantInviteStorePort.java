package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TenantInviteStorePort {

    /**
     * Valida o hash do convite, incrementa {@code uses_count} de forma atómica e devolve o tenant se ainda houver vagas.
     */
    Optional<TenantId> consumeInviteReturningTenant(String codeHash);

    void insertNewInvite(UUID id, TenantId tenantId, String codeHash, int maxUses, Instant expiresAt);
}
