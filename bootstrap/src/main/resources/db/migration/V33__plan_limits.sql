CREATE TABLE IF NOT EXISTS tenant_plan_limit (
    profile_level VARCHAR(16) NOT NULL PRIMARY KEY,
    max_appointments_per_month INTEGER NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT tenant_plan_limit_profile_check
        CHECK (profile_level IN ('BASIC', 'PRO', 'ULTRA'))
);

INSERT INTO tenant_plan_limit (profile_level, max_appointments_per_month) VALUES
    ('BASIC', 50),
    ('PRO', 300),
    ('ULTRA', NULL)
ON CONFLICT (profile_level) DO NOTHING;
