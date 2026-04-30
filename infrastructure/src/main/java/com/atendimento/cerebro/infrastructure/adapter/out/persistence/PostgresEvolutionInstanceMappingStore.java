package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.port.out.EvolutionInstanceMappingStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresEvolutionInstanceMappingStore implements EvolutionInstanceMappingStorePort {

    private final JdbcClient jdbcClient;

    public PostgresEvolutionInstanceMappingStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantId> findTenantIdByEvolutionInstanceName(String evolutionInstanceName) {
        if (evolutionInstanceName == null || evolutionInstanceName.isBlank()) {
            return Optional.empty();
        }
        return jdbcClient
                .sql("SELECT tenant_id FROM tenant_whatsapp_evolution_instance WHERE evolution_instance_name = ?")
                .param(evolutionInstanceName.strip())
                .query((rs, rowNum) -> rs.getString(1))
                .optional()
                .filter(s -> s != null && !s.isBlank())
                .map(TenantId::new);
    }

    @Override
    @Transactional
    public void upsert(TenantId tenantId, String evolutionInstanceName) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO tenant_whatsapp_evolution_instance
                            (tenant_id, evolution_instance_name, connection_state, updated_at)
                        VALUES (?, ?, NULL, now())
                        ON CONFLICT (tenant_id) DO UPDATE SET
                            evolution_instance_name = EXCLUDED.evolution_instance_name,
                            updated_at = now()
                        """)
                .param(tenantId.value())
                .param(evolutionInstanceName.strip())
                .update();
    }

    @Override
    @Transactional
    public void updateConnectionState(String evolutionInstanceName, String connectionState) {
        if (evolutionInstanceName == null || evolutionInstanceName.isBlank()) {
            return;
        }
        String state = connectionState == null || connectionState.isBlank() ? null : connectionState.strip();
        jdbcClient
                .sql(
                        """
                        UPDATE tenant_whatsapp_evolution_instance SET
                            connection_state = ?,
                            updated_at = now()
                        WHERE evolution_instance_name = ?
                        """)
                .param(state)
                .param(evolutionInstanceName.strip())
                .update();
    }
}
