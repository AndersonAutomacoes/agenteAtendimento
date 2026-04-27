package com.atendimento.cerebro.infrastructure.config;

import com.atendimento.cerebro.infrastructure.multitenancy.CerebroCurrentTenantIdentifierResolver;
import com.atendimento.cerebro.infrastructure.multitenancy.CerebroSchemaMultiTenantConnectionProvider;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cerebro.multitenancy.enabled", havingValue = "true")
public class HibernateSchemaMultitenantConfiguration {

    @Bean
    public HibernatePropertiesCustomizer cerebroSchemaMultitenancyCustomizer(
            CerebroSchemaMultiTenantConnectionProvider connectionProvider,
            CerebroCurrentTenantIdentifierResolver tenantIdentifierResolver) {
        return (properties) -> {
            properties.put("hibernate.multiTenancy", "SCHEMA");
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(
                    AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
            properties.put(AvailableSettings.DEFAULT_SCHEMA, "public");
        };
    }
}
