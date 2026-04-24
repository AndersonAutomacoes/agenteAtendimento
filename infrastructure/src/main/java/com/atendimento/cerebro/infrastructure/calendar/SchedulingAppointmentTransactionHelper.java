package com.atendimento.cerebro.infrastructure.calendar;

import com.atendimento.cerebro.application.dto.TenantAppointmentRecord;
import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentQueryPort;
import com.atendimento.cerebro.application.port.out.TenantAppointmentStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante {@code existsOverlapping} e {@code insert} (e CRM) na mesma transacção, imediatamente antes da gravação.
 */
@Component
public class SchedulingAppointmentTransactionHelper {

    private final TenantAppointmentQueryPort appointmentQuery;
    private final TenantAppointmentStorePort appointmentStore;
    private final CrmCustomerStorePort crmCustomerStore;

    public SchedulingAppointmentTransactionHelper(
            TenantAppointmentQueryPort appointmentQuery,
            TenantAppointmentStorePort appointmentStore,
            CrmCustomerStorePort crmCustomerStore) {
        this.appointmentQuery = appointmentQuery;
        this.appointmentStore = appointmentStore;
        this.crmCustomerStore = crmCustomerStore;
    }

    @Transactional(rollbackFor = Exception.class)
    public Optional<Long> insertIfNoDbOverlap(
            TenantId tenantId,
            Instant windowStart,
            Instant windowEnd,
            TenantAppointmentRecord record) {
        if (appointmentQuery.existsOverlapping(tenantId, windowStart, windowEnd)) {
            return Optional.empty();
        }
        long id = appointmentStore.insert(record);
        String conv = record.conversationId() != null ? record.conversationId() : "";
        crmCustomerStore.recordSuccessfulAppointment(tenantId, conv, record.clientName() != null ? record.clientName() : "");
        return Optional.of(id);
    }
}
