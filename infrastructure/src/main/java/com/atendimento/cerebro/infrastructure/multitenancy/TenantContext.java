package com.atendimento.cerebro.infrastructure.multitenancy;

/**
 * Contexto do tenant corrente (thread-bound). O identificador de negócio ({@code tenantId}) alinha-se com
 * {@link com.atendimento.cerebro.domain.tenant.TenantId}; o {@code schema} é o nome do schema PostgreSQL usado em
 * {@code search_path} (modo schema-per-tenant).
 */
public final class TenantContext {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SCHEMA = new ThreadLocal<>();

    private TenantContext() {}

    /**
     * @param tenantId identificador lógico (ex.: oficina_ssa_01)
     * @param schema nome do schema físico (pode coincidir com tenantId ou mapeamento ex.: oficina → public)
     */
    public static void set(String tenantId, String schema) {
        TENANT_ID.set(tenantId);
        SCHEMA.set(schema);
    }

    public static void clear() {
        TENANT_ID.remove();
        SCHEMA.remove();
    }

    /** Identificador de negócio, ou {@code null} se não definido. */
    public static String getTenantId() {
        return TENANT_ID.get();
    }

    /**
     * Schema Hibernate / JDBC; se não definido, retorna {@code null} (o resolver usa o default da configuração).
     */
    public static String getSchema() {
        return SCHEMA.get();
    }
}
