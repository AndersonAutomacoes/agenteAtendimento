package com.atendimento.cerebro.infrastructure.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Conta Firebase válida mas ainda sem linha em {@code portal_user} (só pode concluir registo com convite).
 */
public class FirebasePendingInviteAuthenticationToken extends AbstractAuthenticationToken {

    private final String firebaseUid;

    public FirebasePendingInviteAuthenticationToken(String firebaseUid) {
        super(List.of(new SimpleGrantedAuthority("ROLE_PORTAL_PENDING_INVITE")));
        this.firebaseUid = firebaseUid;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return firebaseUid;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }
}
