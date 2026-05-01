package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.infrastructure.security.PortalAuthenticationToken;
import com.atendimento.cerebro.infrastructure.security.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Alinha o {@code tenantId} da query/header com o sujeito autenticado (JWT).
 *
 * <p>Perfil {@link ProfileLevel#COMERCIAL}: pode operar no {@code tenantId} solicitado na API (ex.: edição
 * de outro cliente no backoffice), sem exigir que coincida com o tenant da sessão.
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
            return resolveWithPrincipal(tenantFromContext.get(), optionalRequestedTenant);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return new TenantAuthResolution(Kind.UNAUTHENTICATED, null);
        }
        String principal = auth.getName();
        return resolveWithPrincipal(principal, optionalRequestedTenant);
    }

    private static TenantAuthResolution resolveWithPrincipal(String principal, String optionalRequestedTenant) {
        String requested =
                optionalRequestedTenant != null && !optionalRequestedTenant.isBlank()
                        ? optionalRequestedTenant.strip()
                        : null;
        if (requested != null && !principal.equals(requested)) {
            if (isComercialPortalUser()) {
                return new TenantAuthResolution(Kind.OK, requested);
            }
            return new TenantAuthResolution(Kind.FORBIDDEN, null);
        }
        return new TenantAuthResolution(Kind.OK, principal);
    }

    private static boolean isComercialPortalUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof PortalAuthenticationToken pat && pat.getProfileLevel() == ProfileLevel.COMERCIAL;
    }
}
