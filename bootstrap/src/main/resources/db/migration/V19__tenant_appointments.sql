CREATE TABLE tenant_appointments (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        VARCHAR(512) NOT NULL,
    conversation_id  VARCHAR(512) NULL,
    client_name      VARCHAR(512) NOT NULL,
    service_name     VARCHAR(512) NOT NULL,
    starts_at        TIMESTAMPTZ  NOT NULL,
    ends_at          TIMESTAMPTZ  NOT NULL,
    google_event_id  VARCHAR(512) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_appointments_tenant_starts
    ON tenant_appointments (tenant_id, starts_at);
