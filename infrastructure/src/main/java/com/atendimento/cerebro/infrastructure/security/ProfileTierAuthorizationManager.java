package com.atendimento.cerebro.infrastructure.security;

import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

@Component
public class ProfileTierAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return new AuthorizationDecision(false);
        }
        if (!(auth instanceof PortalAuthenticationToken portalAuth)) {
            return new AuthorizationDecision(false);
        }
        ProfileLevel level = portalAuth.getProfileLevel();

        HttpServletRequest request = context.getRequest();
        String path = request.getRequestURI();

        if (path.contains("/api/v1/dashboard/")) {
            return new AuthorizationDecision(level.meets(ProfileLevel.PRO));
        }
        if (path.contains("/api/v1/analytics/export")) {
            String format = request.getParameter("format");
            if (format != null && format.equalsIgnoreCase("pdf")) {
                return new AuthorizationDecision(level.meets(ProfileLevel.ULTRA));
            }
            return new AuthorizationDecision(level.meets(ProfileLevel.PRO));
        }
        if (path.contains("/api/v1/analytics/")) {
            return new AuthorizationDecision(level.meets(ProfileLevel.PRO));
        }
        return new AuthorizationDecision(false);
    }
}
