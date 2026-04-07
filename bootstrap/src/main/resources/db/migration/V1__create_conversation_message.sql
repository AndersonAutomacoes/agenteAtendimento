CREATE TABLE conversation_message (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(512) NOT NULL,
    conversation_id VARCHAR(512) NOT NULL,
    role            VARCHAR(32)  NOT NULL,
    content         TEXT         NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_conversation_message_tenant_conv_time
    ON conversation_message (tenant_id, conversation_id, occurred_at, id);
