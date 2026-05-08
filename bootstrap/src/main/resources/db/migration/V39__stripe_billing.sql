-- Dono da cobrança Stripe por tenant (um billing_owner verdadeiro por tenant).
ALTER TABLE portal_user
    ADD COLUMN billing_owner BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX ux_portal_user_billing_owner_one_per_tenant
    ON portal_user (tenant_id)
    WHERE billing_owner;

-- Primeiro utilizador criado por tenant torna-se dono quando ainda não existe dono explícito.
WITH first_owner AS (
    SELECT DISTINCT ON (tenant_id) id
    FROM portal_user
    ORDER BY tenant_id, created_at ASC
)
UPDATE portal_user pu
SET billing_owner = TRUE
FROM first_owner fo
WHERE pu.id = fo.id
  AND NOT EXISTS (
        SELECT 1 FROM portal_user p2 WHERE p2.tenant_id = pu.tenant_id AND p2.billing_owner
    );

CREATE TABLE stripe_customer (
    tenant_id               VARCHAR(512) PRIMARY KEY REFERENCES tenant_configuration (tenant_id) ON DELETE CASCADE,
    stripe_customer_id      VARCHAR(255) NOT NULL UNIQUE,
    owner_firebase_uid      VARCHAR(128) NOT NULL REFERENCES portal_user (firebase_uid),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant_subscription (
    tenant_id                 VARCHAR(512) PRIMARY KEY REFERENCES tenant_configuration (tenant_id) ON DELETE CASCADE,
    stripe_subscription_id    VARCHAR(255) NOT NULL UNIQUE,
    stripe_customer_id        VARCHAR(255) NOT NULL,
    stripe_status             VARCHAR(32)  NOT NULL,
    tier                      VARCHAR(16)  NOT NULL CHECK (tier IN ('BASIC', 'PRO', 'ULTRA')),
    price_id                  VARCHAR(255) NOT NULL,
    billing_interval          VARCHAR(16)  NOT NULL CHECK (billing_interval IN ('MONTH', 'YEAR')),
    current_period_start      TIMESTAMPTZ NOT NULL,
    current_period_end        TIMESTAMPTZ NOT NULL,
    cancel_at_period_end      BOOLEAN NOT NULL DEFAULT FALSE,
    past_due_since            TIMESTAMPTZ NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_subscription_customer ON tenant_subscription (stripe_customer_id);

CREATE TABLE stripe_webhook_event (
    event_id         VARCHAR(255) PRIMARY KEY,
    event_type       VARCHAR(128) NOT NULL,
    received_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at     TIMESTAMPTZ NULL,
    processing_error TEXT NULL
);
