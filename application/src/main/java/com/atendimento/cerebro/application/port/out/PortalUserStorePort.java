package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.portal.PortalUser;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;

public interface PortalUserStorePort {

    Optional<PortalUser> findByFirebaseUid(String firebaseUid);

    void insert(PortalUser portalUser);

    List<PortalUser> listByTenantId(TenantId tenantId);
}
