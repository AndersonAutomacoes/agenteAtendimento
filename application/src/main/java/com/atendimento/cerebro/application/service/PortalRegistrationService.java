package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.port.out.FirebaseCustomClaimsPort;
import com.atendimento.cerebro.application.port.out.PortalUserStorePort;
import com.atendimento.cerebro.application.port.out.TenantInviteStorePort;
import com.atendimento.cerebro.domain.portal.PortalUser;
import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class PortalRegistrationService {

    private final TenantInviteStorePort tenantInviteStore;
    private final PortalUserStorePort portalUserStore;
    private final FirebaseCustomClaimsPort firebaseCustomClaims;

    public PortalRegistrationService(
            TenantInviteStorePort tenantInviteStore,
            PortalUserStorePort portalUserStore,
            FirebaseCustomClaimsPort firebaseCustomClaims) {
        this.tenantInviteStore = tenantInviteStore;
        this.portalUserStore = portalUserStore;
        this.firebaseCustomClaims = firebaseCustomClaims;
    }

    public record RegistrationResult(TenantId tenantId, ProfileLevel profileLevel) {}

    /**
     * Consome um convite, cria {@link PortalUser} com perfil BASIC e sincroniza claims no Firebase.
     * Deve ser invocado dentro de uma transação (ver infra).
     */
    public RegistrationResult registerWithInvite(String firebaseUid, String invitePlainCode) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            throw new IllegalArgumentException("firebaseUid is required");
        }
        if (invitePlainCode == null || invitePlainCode.isBlank()) {
            throw new IllegalArgumentException("inviteCode is required");
        }
        if (portalUserStore.findByFirebaseUid(firebaseUid.strip()).isPresent()) {
            throw new IllegalStateException("utilizador já associado a um tenant");
        }
        String hash = sha256Hex(invitePlainCode.strip());
        TenantId tenantId = tenantInviteStore
                .consumeInviteReturningTenant(hash)
                .orElseThrow(() -> new IllegalArgumentException("convite inválido, expirado ou esgotado"));

        PortalUser portalUser =
                new PortalUser(UUID.randomUUID(), firebaseUid.strip(), tenantId, ProfileLevel.BASIC);
        portalUserStore.insert(portalUser);
        firebaseCustomClaims.setPortalClaims(firebaseUid.strip(), tenantId, ProfileLevel.BASIC);
        return new RegistrationResult(tenantId, ProfileLevel.BASIC);
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
