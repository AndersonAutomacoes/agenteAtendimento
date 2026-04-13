package com.atendimento.cerebro.infrastructure.crm;

import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marca {@code HOT_LEAD} quando há intenção Orçamento/Agendamento há mais de 1 h sem
 * {@code tenant_appointments} criado após a deteção.
 */
@Component
public class CrmHotLeadAbandonmentJob {

    private static final Logger LOG = LoggerFactory.getLogger(CrmHotLeadAbandonmentJob.class);

    private final CrmCustomerStorePort crmCustomerStore;

    public CrmHotLeadAbandonmentJob(CrmCustomerStorePort crmCustomerStore) {
        this.crmCustomerStore = crmCustomerStore;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void markHotLeads() {
        try {
            int n = crmCustomerStore.markStaleBudgetSchedulingAsHotLeadWithoutAppointment();
            if (n > 0) {
                LOG.info("CRM: {} cliente(s) marcados como HOT_LEAD (abandono pós-intenção)", n);
            }
        } catch (RuntimeException e) {
            LOG.warn("CRM hot-lead job falhou: {}", e.toString());
        }
    }
}
