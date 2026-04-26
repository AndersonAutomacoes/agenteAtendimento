package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.port.out.TenantServiceCatalogPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcTenantServiceCatalogRepository implements TenantServiceCatalogPort {

    private final JdbcClient jdbcClient;

    public JdbcTenantServiceCatalogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Long> findServiceIdByName(TenantId tenantId, String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient
                .sql(
                        """
                        SELECT id
                        FROM tenant_services
                        WHERE tenant_id = ?
                          AND lower(trim(name)) = lower(trim(?))
                          AND active = true
                        ORDER BY id
                        LIMIT 1
                        """)
                .param(tenantId.value())
                .param(serviceName.strip())
                .query(Long.class)
                .optional();
    }

    @Override
    public List<String> listActiveServiceNames(TenantId tenantId) {
        return jdbcClient
                .sql(
                        """
                        SELECT trim(name)
                        FROM tenant_services
                        WHERE tenant_id = ?
                          AND active = true
                          AND trim(name) <> ''
                        ORDER BY lower(trim(name))
                        """)
                .param(tenantId.value())
                .query(String.class)
                .list();
    }
}
