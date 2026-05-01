package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import static org.assertj.core.api.Assertions.assertThat;

import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.infrastructure.security.PortalAuthenticationToken;
import com.atendimento.cerebro.infrastructure.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantAuthResolutionTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void unauthenticated_returnsUnauthenticated() {
        assertThat(TenantAuthResolution.resolve("any").kind())
                .isEqualTo(TenantAuthResolution.Kind.UNAUTHENTICATED);
    }

    @Test
    void basic_mismatchedTenant_forbidden() {
        SecurityContextHolder.getContext()
                .setAuthentication(new PortalAuthenticationToken("home", "uid", ProfileLevel.BASIC));
        TenantContext.setTenantId("home");
        var r = TenantAuthResolution.resolve("other");
        assertThat(r.kind()).isEqualTo(TenantAuthResolution.Kind.FORBIDDEN);
    }

    @Test
    void comercial_mismatchedTenant_okWithRequested() {
        SecurityContextHolder.getContext()
                .setAuthentication(new PortalAuthenticationToken("home", "uid", ProfileLevel.COMERCIAL));
        TenantContext.setTenantId("home");
        var r = TenantAuthResolution.resolve("Pilates 6");
        assertThat(r.kind()).isEqualTo(TenantAuthResolution.Kind.OK);
        assertThat(r.tenantId()).isEqualTo("Pilates 6");
    }

    @Test
    void comercial_sameTenant_okWithPrincipal() {
        SecurityContextHolder.getContext()
                .setAuthentication(new PortalAuthenticationToken("home", "uid", ProfileLevel.COMERCIAL));
        TenantContext.setTenantId("home");
        var r = TenantAuthResolution.resolve("home");
        assertThat(r.kind()).isEqualTo(TenantAuthResolution.Kind.OK);
        assertThat(r.tenantId()).isEqualTo("home");
    }

    @Test
    void comercial_blankRequested_usesSessionTenant() {
        SecurityContextHolder.getContext()
                .setAuthentication(new PortalAuthenticationToken("home", "uid", ProfileLevel.COMERCIAL));
        TenantContext.setTenantId("home");
        var r = TenantAuthResolution.resolve("  ");
        assertThat(r.kind()).isEqualTo(TenantAuthResolution.Kind.OK);
        assertThat(r.tenantId()).isEqualTo("home");
    }
}
