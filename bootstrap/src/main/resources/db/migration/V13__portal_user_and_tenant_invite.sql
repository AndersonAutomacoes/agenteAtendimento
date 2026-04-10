CREATE TABLE portal_user (
    id             UUID PRIMARY KEY,
    firebase_uid   VARCHAR(128) NOT NULL UNIQUE,
    tenant_id      VARCHAR(512) NOT NULL REFERENCES tenant_configuration (tenant_id) ON DELETE CASCADE,
    profile_level  VARCHAR(16)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT portal_user_profile_level_check
        CHECK (profile_level IN ('BASIC', 'PRO', 'ULTRA'))
);

CREATE INDEX idx_portal_user_tenant_id ON portal_user (tenant_id);

CREATE TABLE tenant_invite (
    id           UUID PRIMARY KEY,
    tenant_id    VARCHAR(512) NOT NULL REFERENCES tenant_configuration (tenant_id) ON DELETE CASCADE,
    code_hash    VARCHAR(64)  NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ,
    max_uses     INT          NOT NULL DEFAULT 1,
    uses_count   INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT tenant_invite_max_uses_check CHECK (max_uses >= 1),
    CONSTRAINT tenant_invite_uses_count_check CHECK (uses_count >= 0)
);

CREATE INDEX idx_tenant_invite_tenant_id ON tenant_invite (tenant_id);
