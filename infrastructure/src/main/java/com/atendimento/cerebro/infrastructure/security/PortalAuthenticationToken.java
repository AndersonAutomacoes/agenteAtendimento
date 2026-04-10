package com.atendimento.cerebro.infrastructure.security;

import com.atendimento.cerebro.domain.tenant.ProfileLevel;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Utilizador do portal com tenant e perfil carregados da base (fonte de verdade para RBAC). */
public class PortalAuthenticationToken extends AbstractAuthenticationToken {

    private final String tenantId;
    private final String firebaseUid;
    private final ProfileLevel profileLevel;

    public PortalAuthenticationToken(String tenantId, String firebaseUid, ProfileLevel profileLevel) {
        super(List.of(new SimpleGrantedAuthority("ROLE_PORTAL_USER")));
        this.tenantId = tenantId;
        this.firebaseUid = firebaseUid;
        this.profileLevel = profileLevel;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public ProfileLevel getProfileLevel() {
        return profileLevel;
    }
}
