package com.atendimento.cerebro.infrastructure.security;

import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.PlanFeature;
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

    private final FeatureAccessEvaluator featureAccessEvaluator;

    public ProfileTierAuthorizationManager(FeatureAccessEvaluator featureAccessEvaluator) {
        this.featureAccessEvaluator = featureAccessEvaluator;
    }

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
            return new AuthorizationDecision(featureAccessEvaluator.canAccess(level, PlanFeature.DASHBOARD));
        }
        if (path.contains("/api/v1/appointments")) {
            return new AuthorizationDecision(featureAccessEvaluator.canAccess(level, PlanFeature.APPOINTMENTS));
        }
        if (path.contains("/api/v1/analytics/export")) {
            String format = request.getParameter("format");
            if (format != null && format.equalsIgnoreCase("pdf")) {
                return new AuthorizationDecision(featureAccessEvaluator.canAccess(level, PlanFeature.ANALYTICS_EXPORT_PDF));
            }
            return new AuthorizationDecision(featureAccessEvaluator.canAccess(level, PlanFeature.ANALYTICS_EXPORT_CSV));
        }
        if (path.contains("/api/v1/analytics/")) {
            return new AuthorizationDecision(featureAccessEvaluator.canAccess(level, PlanFeature.ANALYTICS));
        }
        if (path.contains("/api/v1/internal/")) {
            return new AuthorizationDecision(level == ProfileLevel.COMERCIAL);
        }
        if (path.contains("/api/v1/messages")
                || path.contains("/api/v1/conversations")) {
            return new AuthorizationDecision(featureAccessEvaluator.canAccess(level, PlanFeature.MONITORING));
        }
        if (path.contains("/api/v1/tenant")) {
            return new AuthorizationDecision(featureAccessEvaluator.canAccess(level, PlanFeature.SETTINGS));
        }
        if (path.contains("/api/v1/knowledge-base")) {
            return new AuthorizationDecision(featureAccessEvaluator.canAccess(level, PlanFeature.KNOWLEDGE_BASE));
        }
        return new AuthorizationDecision(false);
    }
}
