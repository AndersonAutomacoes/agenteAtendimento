package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.dto.TenantServiceDto;
import com.atendimento.cerebro.application.port.out.TenantServicesStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcTenantServicesStore implements TenantServicesStorePort {

    private final JdbcClient jdbcClient;

    public JdbcTenantServicesStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantServiceDto> listByTenant(TenantId tenantId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, name, duracao_estimada, active
                        FROM tenant_services
                        WHERE tenant_id = ?
                        ORDER BY lower(name)
                        """)
                .param(tenantId.value())
                .query(
                        (rs, i) ->
                                new TenantServiceDto(
                                        rs.getObject("id", Long.class),
                                        rs.getString("name"),
                                        (Integer) rs.getObject("duracao_estimada", Integer.class),
                                        rs.getBoolean("active")))
                .list();
    }

    @Override
    @Transactional
    public void upsertAll(TenantId tenantId, List<TenantServiceDto> items) {
        for (TenantServiceDto s : items) {
            String name = s.name().strip();
            jdbcClient
                    .sql(
                            """
                            INSERT INTO tenant_services (tenant_id, name, duracao_estimada, preco_base, active)
                            VALUES (?, ?, ?, NULL, ?)
                            ON CONFLICT (tenant_id, name) DO UPDATE SET
                                duracao_estimada = EXCLUDED.duracao_estimada,
                                active = EXCLUDED.active
                            """)
                    .param(tenantId.value())
                    .param(name)
                    .param(s.durationMinutes())
                    .param(s.active())
                    .update();
        }
    }
}
