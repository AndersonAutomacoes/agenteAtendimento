package com.atendimento.cerebro.infrastructure.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Contexto por requisição com tenant autenticado, para evitar confiar em tenantId informado no payload.
 */
public final class TenantContext {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            TENANT_ID.remove();
            return;
        }
        TENANT_ID.set(tenantId.strip());
    }

    public static Optional<String> getTenantId() {
        String value = TENANT_ID.get();
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public static Optional<String> getTenantIdOrSecurityContext() {
        Optional<String> fromThreadLocal = getTenantId();
        if (fromThreadLocal.isPresent()) {
            return fromThreadLocal;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        String principal = auth.getName();
        if (principal == null || principal.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(principal.strip());
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
