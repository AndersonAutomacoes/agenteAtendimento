package com.atendimento.cerebro.application.port.out;

import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantId;

/**
 * Sincroniza claims customizados no Firebase Auth (tenant_id, profile_level) após registo ou alteração de perfil.
 */
public interface FirebaseCustomClaimsPort {

    void setPortalClaims(String firebaseUid, TenantId tenantId, ProfileLevel profileLevel);
}
