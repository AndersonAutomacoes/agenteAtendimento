CREATE TABLE crm_customer (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(512) NOT NULL,
    conversation_id     VARCHAR(512) NOT NULL,
    phone_number        VARCHAR(512) NULL,
    full_name           VARCHAR(512) NULL,
    email               VARCHAR(512) NULL,
    first_interaction   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    total_appointments  INT NOT NULL DEFAULT 0,
    internal_notes      TEXT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_crm_customer_tenant_conversation
    ON crm_customer (tenant_id, conversation_id);

CREATE UNIQUE INDEX uq_crm_customer_tenant_phone
    ON crm_customer (tenant_id, phone_number)
    WHERE phone_number IS NOT NULL;

CREATE INDEX idx_crm_customer_tenant
    ON crm_customer (tenant_id);
