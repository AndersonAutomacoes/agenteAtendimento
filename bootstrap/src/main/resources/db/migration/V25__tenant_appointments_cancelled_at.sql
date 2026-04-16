ALTER TABLE tenant_appointments
    ADD COLUMN cancelled_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_tenant_appointments_tenant_conv_day
    ON tenant_appointments (tenant_id, conversation_id, starts_at)
    WHERE cancelled_at IS NULL;
