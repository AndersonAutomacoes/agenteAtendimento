package com.atendimento.cerebro.infrastructure.crm;

import com.atendimento.cerebro.application.port.out.CrmCustomerStorePort;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Promove oportunidades OPEN para PENDING_LEAD após 30 minutos sem conversão (ex.: agendamento).
 */
@Component
public class CrmLeadOpportunityRefreshJob {

    private static final Logger LOG = LoggerFactory.getLogger(CrmLeadOpportunityRefreshJob.class);

    static final Duration PENDING_WINDOW = Duration.ofMinutes(30);

    private final CrmCustomerStorePort crmCustomerStore;

    public CrmLeadOpportunityRefreshJob(CrmCustomerStorePort crmCustomerStore) {
        this.crmCustomerStore = crmCustomerStore;
    }

    @Scheduled(cron = "0 */2 * * * *", zone = "UTC")
    public void promoteStaleOpenLeads() {
        try {
            int n = crmCustomerStore.promoteStaleOpenLeadsToPending(PENDING_WINDOW);
            if (n > 0) {
                LOG.info("CRM: {} oportunidade(s) OPEN → PENDING_LEAD (janela {} min)", n, PENDING_WINDOW.toMinutes());
            }
        } catch (RuntimeException e) {
            LOG.warn("CRM lead refresh job falhou: {}", e.toString());
        }
    }
}
