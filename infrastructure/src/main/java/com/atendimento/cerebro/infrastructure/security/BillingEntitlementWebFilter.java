package com.atendimento.cerebro.infrastructure.security;

import com.atendimento.cerebro.application.dto.billing.TenantEntitlementDecision;
import com.atendimento.cerebro.application.dto.billing.TenantSubscriptionSnapshot;
import com.atendimento.cerebro.application.port.out.TenantSubscriptionPersistencePort;
import com.atendimento.cerebro.application.service.billing.TenantEntitlementEvaluator;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cobrança por tenant §12.3 da spec: bloqueia operação sob {@code /api/v1/**} quando o espelho Stripe local
 * não concede entitlement, excetuando webhook Meta/WhatsApp e perfil {@link ProfileLevel#COMERCIAL}.
 */
@Component
public class BillingEntitlementWebFilter extends OncePerRequestFilter {

    private static final String BLOCKED_BODY =
            "{\"error\":\"assinatura_inativa\",\"code\":\"BILLING_BLOCKED\"}";

    private final TenantSubscriptionPersistencePort tenantSubscriptionPersistence;
    private final TenantEntitlementEvaluator tenantEntitlementEvaluator;
    private final Clock clock;

    public BillingEntitlementWebFilter(
            TenantSubscriptionPersistencePort tenantSubscriptionPersistence,
            TenantEntitlementEvaluator tenantEntitlementEvaluator,
            Clock clock) {
        this.tenantSubscriptionPersistence = tenantSubscriptionPersistence;
        this.tenantEntitlementEvaluator = tenantEntitlementEvaluator;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!requiresBillingEvaluation(request.getRequestURI(), request.getContextPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof PortalAuthenticationToken portal) || !auth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (portal.getProfileLevel() == ProfileLevel.COMERCIAL) {
            filterChain.doFilter(request, response);
            return;
        }

        TenantSubscriptionSnapshot snapshot =
                tenantSubscriptionPersistence.findByTenantId(portal.getTenantId()).orElse(null);
        TenantEntitlementDecision decision =
                tenantEntitlementEvaluator.evaluate(snapshot, Instant.now(clock));

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeBlocked(response);
    }

    static boolean requiresBillingEvaluation(String requestUri, String contextPath) {
        String path = stripContextPath(requestUri, contextPath);
        return path.startsWith("/api/v1/")
                && !path.startsWith("/api/v1/whatsapp/webhook/")
                && !path.equals("/api/v1/whatsapp/webhook");
    }

    static String stripContextPath(String requestUri, String contextPath) {
        String path = requestUri != null ? requestUri : "/";
        if (contextPath != null && !contextPath.isBlank() && !"/".equals(contextPath)) {
            String prefix =
                    contextPath.endsWith("/") ? contextPath.substring(0, contextPath.length() - 1) : contextPath;
            if (path.startsWith(prefix)) {
                path = path.substring(prefix.length());
            }
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    private static void writeBlocked(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BLOCKED_BODY);
    }
}
