package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.dto.TenantAppointmentRecord;
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcTenantAppointmentStore implements TenantAppointmentStorePort {

    private final JdbcClient jdbcClient;

    public JdbcTenantAppointmentStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void insert(TenantAppointmentRecord record) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO tenant_appointments (
                            tenant_id, conversation_id, client_name, service_name,
                            starts_at, ends_at, google_event_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)
                .param(record.tenantId().value())
                .param(record.conversationId())
                .param(record.clientName())
                .param(record.serviceName())
                .param(Timestamp.from(record.startsAt()))
                .param(Timestamp.from(record.endsAt()))
                .param(record.googleEventId())
                .update();
    }
}
