package com.atendimento.cerebro.infrastructure.service;

import com.atendimento.cerebro.application.service.PortalRegistrationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionalPortalRegistrationService {

    private final PortalRegistrationService delegate;

    public TransactionalPortalRegistrationService(PortalRegistrationService delegate) {
        this.delegate = delegate;
    }

    @Transactional
    public PortalRegistrationService.RegistrationResult registerWithInvite(String firebaseUid, String invitePlainCode) {
        return delegate.registerWithInvite(firebaseUid, invitePlainCode);
    }
}
