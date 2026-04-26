package com.atendimento.cerebro.application.service;

import com.atendimento.cerebro.application.port.out.TenantInviteStorePort;
import com.atendimento.cerebro.application.port.out.InviteEmailSenderPort;
import com.atendimento.cerebro.domain.tenant.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

public class TenantInviteService {

    private static final char[] ALPHANUM = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RND = new SecureRandom();

    private final TenantInviteStorePort tenantInviteStore;
    private final InviteEmailSenderPort inviteEmailSenderPort;

    public TenantInviteService(
            TenantInviteStorePort tenantInviteStore,
            InviteEmailSenderPort inviteEmailSenderPort) {
        this.tenantInviteStore = tenantInviteStore;
        this.inviteEmailSenderPort = inviteEmailSenderPort;
    }

    /**
     * Gera código legível, persiste o hash (SHA-256) e devolve o código em claro (mostrar/só no momento).
     */
    public String createInvite(TenantId tenantId, int maxUses, Instant expiresAt) {
        if (maxUses < 1) {
            maxUses = 1;
        }
        String plain = randomCode(18);
        String hash = sha256Hex(plain);
        tenantInviteStore.insertNewInvite(UUID.randomUUID(), tenantId, hash, maxUses, expiresAt);
        return plain;
    }

    public String createInviteAndSendEmail(
            TenantId tenantId,
            int maxUses,
            Instant expiresAt,
            String inviteEmail,
            String establishmentName) {
        String normalizedInviteEmail = normalizeInviteEmail(inviteEmail);
        String plain = createInvite(tenantId, maxUses, expiresAt);
        inviteEmailSenderPort.sendInviteEmail(
                new InviteEmailSenderPort.InviteEmailCommand(
                        normalizedInviteEmail,
                        tenantId.value(),
                        establishmentName,
                        plain,
                        maxUses < 1 ? 1 : maxUses,
                        expiresAt));
        return plain;
    }

    private static String randomCode(int len) {
        char[] b = new char[len];
        for (int i = 0; i < len; i++) {
            b[i] = ALPHANUM[RND.nextInt(ALPHANUM.length)];
        }
        return new String(b);
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

    private static String normalizeInviteEmail(String inviteEmail) {
        if (inviteEmail == null || inviteEmail.isBlank()) {
            throw new IllegalArgumentException("inviteEmail é obrigatório");
        }
        String normalized = inviteEmail.strip();
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("inviteEmail inválido");
        }
        return normalized;
    }
}
