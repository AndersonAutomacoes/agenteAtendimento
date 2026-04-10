package com.atendimento.cerebro.infrastructure.adapter.out.firebase;

import com.atendimento.cerebro.application.port.out.FirebaseCustomClaimsPort;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantId;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(FirebaseApp.class)
public class FirebaseCustomClaimsAdapter implements FirebaseCustomClaimsPort {

    @Override
    public void setPortalClaims(String firebaseUid, TenantId tenantId, ProfileLevel profileLevel) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tenant_id", tenantId.value());
        claims.put("profile_level", profileLevel.name());
        try {
            FirebaseAuth.getInstance().setCustomUserClaims(firebaseUid, claims);
        } catch (FirebaseAuthException e) {
            throw new IllegalStateException("Falha ao definir claims Firebase", e);
        }
    }
}
