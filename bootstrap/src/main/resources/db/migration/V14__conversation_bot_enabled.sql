CREATE TABLE conversation (
    tenant_id     VARCHAR(512) NOT NULL,
    phone_number  VARCHAR(512) NOT NULL,
    bot_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, phone_number)
);

CREATE INDEX idx_conversation_tenant ON conversation (tenant_id);
