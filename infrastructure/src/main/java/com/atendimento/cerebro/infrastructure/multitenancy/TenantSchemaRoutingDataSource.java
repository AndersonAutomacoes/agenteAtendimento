package com.atendimento.cerebro.infrastructure.multitenancy;

import com.atendimento.cerebro.infrastructure.config.CerebroMultitenancyProperties;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Ajusta {@code search_path} em cada {@link Connection} obtida do pool, com base em {@link TenantContext}
 * (pedidos webhook / thread com tenant resolvido).
 */
public final class TenantSchemaRoutingDataSource extends DelegatingDataSource {

    private final CerebroMultitenancyProperties properties;

    public TenantSchemaRoutingDataSource(DataSource targetDataSource, CerebroMultitenancyProperties properties) {
        super(targetDataSource);
        this.properties = properties;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection c = super.getConnection();
        applySearchPath(c);
        return c;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection c = super.getConnection(username, password);
        applySearchPath(c);
        return c;
    }

    private void applySearchPath(Connection c) throws SQLException {
        if (!properties.isEnabled()) {
            return;
        }
        String schema = TenantContext.getSchema();
        if (schema == null || schema.isBlank()) {
            schema = properties.getDefaultSchema();
        }
        String safe = properties.resolveSchema(schema);
        try (Statement st = c.createStatement()) {
            st.execute("SET search_path TO " + safe + ", public");
        }
    }
}
