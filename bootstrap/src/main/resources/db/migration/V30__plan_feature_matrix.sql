CREATE TABLE IF NOT EXISTS tenant_plan_feature (
    profile_level VARCHAR(16) NOT NULL,
    feature_key   VARCHAR(64) NOT NULL,
    enabled       BOOLEAN NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (profile_level, feature_key),
    CONSTRAINT tenant_plan_feature_profile_check
        CHECK (profile_level IN ('BASIC', 'PRO', 'ULTRA'))
);

INSERT INTO tenant_plan_feature (profile_level, feature_key, enabled) VALUES
    ('BASIC', 'DASHBOARD', false),
    ('BASIC', 'ANALYTICS', false),
    ('BASIC', 'ANALYTICS_EXPORT_CSV', false),
    ('BASIC', 'ANALYTICS_EXPORT_PDF', false),
    ('BASIC', 'APPOINTMENTS', false),
    ('BASIC', 'KNOWLEDGE_BASE', false),
    ('BASIC', 'MONITORING', false),
    ('BASIC', 'SETTINGS', true),
    ('PRO', 'DASHBOARD', true),
    ('PRO', 'ANALYTICS', true),
    ('PRO', 'ANALYTICS_EXPORT_CSV', true),
    ('PRO', 'ANALYTICS_EXPORT_PDF', false),
    ('PRO', 'APPOINTMENTS', true),
    ('PRO', 'KNOWLEDGE_BASE', true),
    ('PRO', 'MONITORING', true),
    ('PRO', 'SETTINGS', true),
    ('ULTRA', 'DASHBOARD', true),
    ('ULTRA', 'ANALYTICS', true),
    ('ULTRA', 'ANALYTICS_EXPORT_CSV', true),
    ('ULTRA', 'ANALYTICS_EXPORT_PDF', true),
    ('ULTRA', 'APPOINTMENTS', true),
    ('ULTRA', 'KNOWLEDGE_BASE', true),
    ('ULTRA', 'MONITORING', true),
    ('ULTRA', 'SETTINGS', true)
ON CONFLICT (profile_level, feature_key) DO NOTHING;
