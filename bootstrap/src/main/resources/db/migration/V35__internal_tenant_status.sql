CREATE TABLE IF NOT EXISTS internal_tenant_status (
    tenant_id   VARCHAR(120) PRIMARY KEY,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_internal_tenant_status_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenant_configuration (tenant_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_internal_tenant_status_active
    ON internal_tenant_status (active);
