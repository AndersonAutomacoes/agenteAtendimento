ALTER TABLE tenant_configuration
    DROP CONSTRAINT IF EXISTS tenant_configuration_profile_level_check;

ALTER TABLE tenant_configuration
    ADD CONSTRAINT tenant_configuration_profile_level_check
    CHECK (profile_level IN ('BASIC', 'PRO', 'ULTRA', 'COMERCIAL'));

ALTER TABLE portal_user
    DROP CONSTRAINT IF EXISTS portal_user_profile_level_check;

ALTER TABLE portal_user
    ADD CONSTRAINT portal_user_profile_level_check
    CHECK (profile_level IN ('BASIC', 'PRO', 'ULTRA', 'COMERCIAL'));

ALTER TABLE tenant_plan_feature
    DROP CONSTRAINT IF EXISTS tenant_plan_feature_profile_check;

ALTER TABLE tenant_plan_feature
    ADD CONSTRAINT tenant_plan_feature_profile_check
    CHECK (profile_level IN ('BASIC', 'PRO', 'ULTRA', 'COMERCIAL'));

ALTER TABLE tenant_plan_limit
    DROP CONSTRAINT IF EXISTS tenant_plan_limit_profile_check;

ALTER TABLE tenant_plan_limit
    ADD CONSTRAINT tenant_plan_limit_profile_check
    CHECK (profile_level IN ('BASIC', 'PRO', 'ULTRA', 'COMERCIAL'));

INSERT INTO tenant_plan_feature (profile_level, feature_key, enabled, updated_at)
SELECT 'COMERCIAL', f.feature_key, f.enabled, NOW()
FROM tenant_plan_feature f
WHERE f.profile_level = 'ULTRA'
ON CONFLICT (profile_level, feature_key) DO UPDATE SET
    enabled = EXCLUDED.enabled,
    updated_at = NOW();

INSERT INTO tenant_plan_limit (profile_level, max_appointments_per_month, updated_at)
VALUES ('COMERCIAL', NULL, NOW())
ON CONFLICT (profile_level) DO UPDATE SET
    max_appointments_per_month = EXCLUDED.max_appointments_per_month,
    updated_at = NOW();
