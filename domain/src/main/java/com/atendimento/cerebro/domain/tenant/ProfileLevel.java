package com.atendimento.cerebro.domain.tenant;

/**
 * Plano / perfil da conta tenant (RBAC).
 */
public enum ProfileLevel {
    BASIC,
    PRO,
    ULTRA,
    COMERCIAL;

    /** {@code true} se este nível for igual ou superior a {@code required}. */
    public boolean meets(ProfileLevel required) {
        if (required == null) {
            throw new IllegalArgumentException("required must not be null");
        }
        return this.ordinal() >= required.ordinal();
    }
}
