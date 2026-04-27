package com.atendimento.cerebro.infrastructure.multitenancy;

import com.atendimento.cerebro.infrastructure.config.CerebroMultitenancyProperties;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Hibernate (SCHEMA): identificador do tenant = schema físico corrente em {@link TenantContext}.
 */
@Component
@ConditionalOnProperty(name = "cerebro.multitenancy.enabled", havingValue = "true")
public class CerebroCurrentTenantIdentifierResolver implements CurrentTenantIdentifierResolver {

    private final CerebroMultitenancyProperties multitenancyProperties;

    public CerebroCurrentTenantIdentifierResolver(CerebroMultitenancyProperties multitenancyProperties) {
        this.multitenancyProperties = multitenancyProperties;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        String schema = TenantContext.getSchema();
        if (schema == null || schema.isBlank()) {
            return multitenancyProperties.getDefaultSchema();
        }
        return multitenancyProperties.resolveSchema(schema);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
