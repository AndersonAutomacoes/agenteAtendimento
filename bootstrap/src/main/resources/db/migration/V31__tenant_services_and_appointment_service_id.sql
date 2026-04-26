CREATE TABLE IF NOT EXISTS tenant_services (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         VARCHAR(512) NOT NULL,
    name              VARCHAR(255) NOT NULL,
    duracao_estimada  INTEGER NULL,
    preco_base        NUMERIC(12,2) NULL,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_tenant_services_tenant_active
    ON tenant_services (tenant_id, active);

ALTER TABLE tenant_appointments
    ADD COLUMN IF NOT EXISTS service_id BIGINT NULL;

ALTER TABLE tenant_appointments
    ADD CONSTRAINT fk_tenant_appointments_service
        FOREIGN KEY (service_id) REFERENCES tenant_services(id);
