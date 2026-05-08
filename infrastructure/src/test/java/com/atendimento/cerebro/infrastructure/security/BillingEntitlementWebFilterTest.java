package com.atendimento.cerebro.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atendimento.cerebro.application.dto.billing.TenantSubscriptionSnapshot;
import com.atendimento.cerebro.application.port.out.TenantSubscriptionPersistencePort;
import com.atendimento.cerebro.application.service.billing.TenantEntitlementEvaluator;
import com.atendimento.cerebro.domain.billing.BillingPlanTier;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class BillingEntitlementWebFilterTest {

    private static final String TENANT = "tenant-billing-filter";

    @Mock
    private TenantSubscriptionPersistencePort tenantSubscriptionPersistence;

    @Mock
    private FilterChain filterChain;

    private BillingEntitlementWebFilter filter;
    private final TenantEntitlementEvaluator evaluator = new TenantEntitlementEvaluator(7);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        filter = new BillingEntitlementWebFilter(tenantSubscriptionPersistence, evaluator, clock);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsWhenPathNotUnderDashboardApiNamespace() throws Exception {
        var req = new MockHttpServletRequest("GET", "/v1/auth/me");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void skipsWebhookPathUnderApiPrefix() throws Exception {
        authenticatePortal(ProfileLevel.PRO);
        var req = new MockHttpServletRequest("POST", "/api/v1/whatsapp/webhook/foo");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void skipsAnonymousUser() throws Exception {
        var anonymous =
                new AnonymousAuthenticationToken(
                        "billing-test", "anon", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
        var req = new MockHttpServletRequest("GET", "/api/v1/dashboard/overview");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void skipsComercialPortalUser() throws Exception {
        authenticatePortal(ProfileLevel.COMERCIAL);
        var req = new MockHttpServletRequest("GET", "/api/v1/internal/something");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);
        verify(filterChain).doFilter(any(), any());
        verify(tenantSubscriptionPersistence, never()).findByTenantId(any());
    }

    @Test
    void responds403BillingBlockedWhenNoSubscription() throws Exception {
        authenticatePortal(ProfileLevel.PRO);
        when(tenantSubscriptionPersistence.findByTenantId(TENANT)).thenReturn(Optional.empty());
        var req = new MockHttpServletRequest("GET", "/api/v1/dashboard/overview");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString())
                .isEqualTo("{\"error\":\"assinatura_inativa\",\"code\":\"BILLING_BLOCKED\"}");
    }

    @Test
    void forwardsWhenMirrorSubscriptionIsActiveInsidePeriod() throws Exception {
        authenticatePortal(ProfileLevel.PRO);
        Instant start = Instant.parse("2026-05-08T12:00:00Z");
        Instant end = start.plus(30, ChronoUnit.DAYS);
        TenantSubscriptionSnapshot snap =
                new TenantSubscriptionSnapshot(
                        TENANT,
                        "sub_ok",
                        "cus_ok",
                        "active",
                        BillingPlanTier.PRO,
                        "price_pro",
                        "MONTH",
                        start,
                        end,
                        false,
                        null);
        when(tenantSubscriptionPersistence.findByTenantId(TENANT)).thenReturn(Optional.of(snap));

        var req = new MockHttpServletRequest("GET", "/api/v1/dashboard/overview");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);

        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void stripsContextPathBeforeMatchingApiNamespace() {
        assertThat(BillingEntitlementWebFilter.requiresBillingEvaluation(
                        "/portal/api/v1/dashboard/x", "/portal"))
                .isTrue();

        assertThat(BillingEntitlementWebFilter.requiresBillingEvaluation(
                        "/portal/api/v1/whatsapp/webhook", "/portal"))
                .isFalse();
    }

    private void authenticatePortal(ProfileLevel profileLevel) {
        SecurityContextHolder.getContext()
                .setAuthentication(new PortalAuthenticationToken(TENANT, "firebaseUid", profileLevel));
    }
}
