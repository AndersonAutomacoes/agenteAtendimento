package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.analytics.ChatMainIntent;
import com.atendimento.cerebro.application.crm.CrmConversationSupport;
import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.time.Instant;

/**
 * Liga a classificação de intenção (analytics) ao funil de oportunidades no CRM.
 */
public class CrmLeadIntentUpdater {

    private final CrmCustomerStorePort crmCustomerStore;

    public CrmLeadIntentUpdater(CrmCustomerStorePort crmCustomerStore) {
        this.crmCustomerStore = crmCustomerStore;
    }

    public void onMainIntentClassified(TenantId tenantId, String phoneNumber, ChatMainIntent intent, Instant at) {
        if (tenantId == null || phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }
        if (intent != ChatMainIntent.Orcamento && intent != ChatMainIntent.Agendamento) {
            return;
        }
        String conv = CrmConversationSupport.whatsAppConversationIdFromPhoneDigits(phoneNumber);
        if (conv == null) {
            return;
        }
        crmCustomerStore.applyLeadIntentFromClassification(tenantId, conv, intent, at);
    }
}
