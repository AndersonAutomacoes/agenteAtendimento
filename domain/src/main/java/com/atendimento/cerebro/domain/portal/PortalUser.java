package com.atendimento.cerebro.domain.portal;

import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.util.UUID;

/**
 * Utilizador do portal (Firebase Auth) associado a um tenant.
 * O {@link ProfileLevel} aqui governa o RBAC do portal (dashboard/analytics), não o plano comercial na linha do tenant.
 */
public record PortalUser(UUID id, String firebaseUid, TenantId tenantId, ProfileLevel profileLevel) {

    public PortalUser {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        if (firebaseUid == null || firebaseUid.isBlank()) {
            throw new IllegalArgumentException("firebaseUid is required");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (profileLevel == null) {
            throw new IllegalArgumentException("profileLevel is required");
        }
    }
}
