package com.atendimento.cerebro.infrastructure.multitenancy;

import com.atendimento.cerebro.infrastructure.config.CerebroMultitenancyProperties;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Hibernate SCHEMA: conexões do pool raw com {@code search_path} por identificador de tenant (schema físico).
 */
@Component
@ConditionalOnProperty(name = "cerebro.multitenancy.enabled", havingValue = "true")
public class CerebroSchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;
    private final CerebroMultitenancyProperties multitenancyProperties;

    public CerebroSchemaMultiTenantConnectionProvider(
            @Qualifier("cerebroRawDataSource") DataSource dataSource,
            CerebroMultitenancyProperties multitenancyProperties) {
        this.dataSource = dataSource;
        this.multitenancyProperties = multitenancyProperties;
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection c = getAnyConnection();
        applySearchPath(c, tenantIdentifier);
        return c;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType.isInstance(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return (T) this;
        }
        throw new org.hibernate.HibernateException("Cannot unwrap " + unwrapType);
    }

    private void applySearchPath(Connection c, String tenantIdentifier) throws SQLException {
        String safe =
                tenantIdentifier == null || tenantIdentifier.isBlank()
                        ? multitenancyProperties.getDefaultSchema()
                        : multitenancyProperties.resolveSchema(tenantIdentifier);
        try (Statement st = c.createStatement()) {
            st.execute("SET search_path TO " + safe + ", public");
        }
    }
}
