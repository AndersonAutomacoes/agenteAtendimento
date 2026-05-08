package com.atendimento.cerebro.infrastructure.billing;

import com.atendimento.cerebro.application.service.billing.TenantEntitlementEvaluator;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingEntitlementConfiguration {

    @Bean
    public TenantEntitlementEvaluator tenantEntitlementEvaluator(
            @Value("${billing.past-due-grace-days:7}") int graceDays) {
        return new TenantEntitlementEvaluator(graceDays);
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}
