package com.atendimento.cerebro.infrastructure.adapter.out.persistence;

import com.atendimento.cerebro.application.port.out.PortalUserStorePort;
import com.atendimento.cerebro.domain.portal.PortalUser;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PostgresPortalUserStore implements PortalUserStorePort {

    private final JdbcClient jdbcClient;

    public PostgresPortalUserStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PortalUser> findByFirebaseUid(String firebaseUid) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, firebase_uid, tenant_id, profile_level
                        FROM portal_user WHERE firebase_uid = ?
                        """)
                .param(firebaseUid)
                .query(this::mapRow)
                .optional();
    }

    private PortalUser mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PortalUser(
                rs.getObject("id", UUID.class),
                rs.getString("firebase_uid"),
                new TenantId(rs.getString("tenant_id")),
                parseProfileLevel(rs.getString("profile_level")));
    }

    private static ProfileLevel parseProfileLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return ProfileLevel.BASIC;
        }
        try {
            return ProfileLevel.valueOf(raw.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("profile_level inválido na base: " + raw, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortalUser> listByTenantId(TenantId tenantId) {
        return jdbcClient
                .sql(
                        """
                        SELECT id, firebase_uid, tenant_id, profile_level
                        FROM portal_user WHERE tenant_id = ?
                        ORDER BY created_at ASC
                        """)
                .param(tenantId.value())
                .query(this::mapRow)
                .list();
    }

    @Override
    @Transactional
    public void insert(PortalUser portalUser) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO portal_user (id, firebase_uid, tenant_id, profile_level, created_at, updated_at)
                        VALUES (?, ?, ?, ?, NOW(), NOW())
                        """)
                .param(portalUser.id())
                .param(portalUser.firebaseUid())
                .param(portalUser.tenantId().value())
                .param(portalUser.profileLevel().name())
                .update();
    }
}
