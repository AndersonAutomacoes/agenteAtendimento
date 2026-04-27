package com.atendimento.cerebro.infrastructure.multitenancy.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Entidade mínima para o {@code EntityManagerFactory} com multi-tenancy Hibernate; tabela já existente em {@code public}.
 */
@Entity
@Immutable
@Table(name = "tenant_configuration", schema = "public")
public class TenantConfigurationReferenceEntity {

    @Id
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "system_prompt", nullable = false)
    private String systemPrompt;

    protected TenantConfigurationReferenceEntity() {}

    public String getTenantId() {
        return tenantId;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }
}
