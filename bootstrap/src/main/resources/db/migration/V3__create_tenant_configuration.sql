CREATE TABLE tenant_configuration (
    tenant_id     VARCHAR(512) PRIMARY KEY,
    system_prompt TEXT         NOT NULL DEFAULT ''
);
