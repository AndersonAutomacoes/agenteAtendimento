package com.atendimento.cerebro.infrastructure.adapter.inbound.rest;

import com.atendimento.cerebro.infrastructure.service.TransactionalPortalRegistrationService;
import com.atendimento.cerebro.domain.tenant.PlanFeature;
import com.atendimento.cerebro.infrastructure.security.FirebasePendingInviteAuthenticationToken;
import com.atendimento.cerebro.infrastructure.security.FeatureAccessEvaluator;
import com.atendimento.cerebro.infrastructure.security.PortalAuthenticationToken;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final TransactionalPortalRegistrationService portalRegistrationService;
    private final FeatureAccessEvaluator featureAccessEvaluator;

    public AuthController(
            TransactionalPortalRegistrationService portalRegistrationService,
            FeatureAccessEvaluator featureAccessEvaluator) {
        this.portalRegistrationService = portalRegistrationService;
        this.featureAccessEvaluator = featureAccessEvaluator;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            HttpServletRequest request,
            Authentication authentication,
            @RequestBody(required = false) RegisterRequest body) {
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorBody(unauthenticatedFirebaseMessage(request)));
        }
        if (authentication instanceof PortalAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                            new ErrorBody(
                                    "Esta conta já está associada a um tenant. Utilize o login (Entrar), não o registo com convite."));
        }
        if (!(authentication instanceof FirebasePendingInviteAuthenticationToken pending)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorBody("Sessão inválida para registo com convite."));
        }
        if (body == null || body.inviteCode() == null || body.inviteCode().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody("inviteCode é obrigatório"));
        }
        try {
            var result = portalRegistrationService.registerWithInvite(pending.getFirebaseUid(), body.inviteCode());
            return ResponseEntity.ok(
                    new RegisterResponse(result.tenantId().value(), result.profileLevel().name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody(e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request, Authentication authentication) {
        if (authentication instanceof PortalAuthenticationToken portal) {
            Map<String, Boolean> features = new LinkedHashMap<>();
            for (PlanFeature feature : PlanFeature.values()) {
                features.put(
                        feature.name(),
                        featureAccessEvaluator.canAccess(portal.getProfileLevel(), feature));
            }
            return ResponseEntity.ok(
                    new SessionResponse(
                            portal.getTenantId(), portal.getProfileLevel().name(), features));
        }
        if (authentication instanceof FirebasePendingInviteAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(
                            new ErrorBody(
                                    "Conta Firebase sem registo no portal. Utilize um código de convite em POST /v1/auth/register (página /register)."));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorBody(unauthenticatedFirebaseMessage(request)));
    }

    /**
     * Distingue pedido sem Bearer (cliente) de token presente mas rejeitado (configuração backend / projeto errado).
     */
    private static String unauthenticatedFirebaseMessage(HttpServletRequest request) {
        String authz = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean hasBearer =
                authz != null
                        && authz.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
                        && authz.length() > "Bearer ".length() + 8;
        if (!hasBearer) {
            return "Pedido sem Authorization: Bearer com o ID token Firebase (getIdToken() no cliente após login).";
        }
        return "O backend não aceitou o ID token. Confirme CEREBRO_FIREBASE_ENABLED=true, credenciais da conta de serviço (mesmo projeto que NEXT_PUBLIC_FIREBASE_PROJECT_ID no frontend) e FIREBASE_SERVICE_ACCOUNT_JSON_PATH ou GOOGLE_APPLICATION_CREDENTIALS.";
    }

    public record RegisterRequest(String inviteCode) {}

    public record RegisterResponse(String tenantId, String profileLevel) {}

    public record SessionResponse(
            String tenantId, String profileLevel, Map<String, Boolean> features) {}

    public record ErrorBody(String error) {}
}
