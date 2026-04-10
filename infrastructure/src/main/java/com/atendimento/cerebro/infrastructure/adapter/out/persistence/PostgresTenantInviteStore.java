package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.port.out.TenantInviteStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresTenantInviteStore implements TenantInviteStorePort {

    private final JdbcClient jdbcClient;

    public PostgresTenantInviteStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public Optional<TenantId> consumeInviteReturningTenant(String codeHash) {
        return jdbcClient
                .sql(
                        """
                        UPDATE tenant_invite
                        SET uses_count = uses_count + 1
                        WHERE code_hash = ?
                          AND (expires_at IS NULL OR expires_at > NOW())
                          AND uses_count < max_uses
                        RETURNING tenant_id
                        """)
                .param(codeHash)
                .query(String.class)
                .optional()
                .map(TenantId::new);
    }
}
