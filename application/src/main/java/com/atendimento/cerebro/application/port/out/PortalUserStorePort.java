package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.portal.PortalUser;
import java.util.Optional;

public interface PortalUserStorePort {

    Optional<PortalUser> findByFirebaseUid(String firebaseUid);

    void insert(PortalUser portalUser);
}
