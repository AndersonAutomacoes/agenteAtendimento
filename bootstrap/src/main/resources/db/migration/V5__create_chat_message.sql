CREATE TABLE chat_message (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(512) NOT NULL,
    phone_number    VARCHAR(512) NOT NULL,
    role            VARCHAR(32)  NOT NULL,
    content         TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_chat_message_tenant_occurred
    ON chat_message (tenant_id, occurred_at DESC, id DESC);
