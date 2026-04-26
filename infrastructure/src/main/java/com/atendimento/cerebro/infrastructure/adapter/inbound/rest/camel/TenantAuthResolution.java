package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.infrastructure.security.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Alinha o {@code tenantId} da query/header com o sujeito autenticado (JWT).
 */
public record TenantAuthResolution(Kind kind, String tenantId) {

    public enum Kind {
        OK,
        FORBIDDEN,
        UNAUTHENTICATED
    }

    public static TenantAuthResolution resolve(String optionalRequestedTenant) {
        var tenantFromContext = TenantContext.getTenantIdOrSecurityContext();
        if (tenantFromContext.isPresent()) {
            String principal = tenantFromContext.get();
            if (optionalRequestedTenant != null
                    && !optionalRequestedTenant.isBlank()
                    && !principal.equals(optionalRequestedTenant.strip())) {
                return new TenantAuthResolution(Kind.FORBIDDEN, null);
            }
            return new TenantAuthResolution(Kind.OK, principal);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return new TenantAuthResolution(Kind.UNAUTHENTICATED, null);
        }
        String principal = auth.getName();
        if (optionalRequestedTenant != null && !optionalRequestedTenant.isBlank()) {
            if (!principal.equals(optionalRequestedTenant.strip())) {
                return new TenantAuthResolution(Kind.FORBIDDEN, null);
            }
        }
        return new TenantAuthResolution(Kind.OK, principal);
    }
}
