-- Mapeamento nome de instância Evolution (webhook) → tenant; usado por WebhookTenantResolver.
CREATE TABLE tenant_whatsapp_evolution_instance (
    tenant_id                 VARCHAR(512) NOT NULL
        REFERENCES tenant_configuration (tenant_id) ON DELETE CASCADE,
    evolution_instance_name   TEXT         NOT NULL,
    connection_state          TEXT         NULL,
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_tenant_whatsapp_evolution PRIMARY KEY (tenant_id),
    CONSTRAINT ux_evolution_instance_name UNIQUE (evolution_instance_name)
);

CREATE INDEX ix_tenant_evolution_instance_name
    ON tenant_whatsapp_evolution_instance (evolution_instance_name);
