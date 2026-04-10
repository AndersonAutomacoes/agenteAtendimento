ALTER TABLE tenant_configuration
    ADD COLUMN profile_level VARCHAR(16) NOT NULL DEFAULT 'BASIC',
    ADD COLUMN portal_password_hash VARCHAR(255) NULL;

ALTER TABLE tenant_configuration
    ADD CONSTRAINT tenant_configuration_profile_level_check
        CHECK (profile_level IN ('BASIC', 'PRO', 'ULTRA'));
