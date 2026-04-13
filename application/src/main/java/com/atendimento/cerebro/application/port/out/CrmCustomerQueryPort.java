package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.application.dto.CrmCustomerRecord;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;

public interface CrmCustomerQueryPort {

    Optional<CrmCustomerRecord> findByTenantAndConversationId(TenantId tenantId, String conversationId);

    /** Clientes com intenção não fechada (estado PENDING_LEAD). */
    List<CrmCustomerRecord> listPendingLeadOpportunities(TenantId tenantId);
}
