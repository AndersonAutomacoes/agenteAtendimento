DROP INDEX IF EXISTS idx_tenant_appointments_tenant_conv_day;

ALTER TABLE tenant_appointments
    ADD COLUMN booking_status VARCHAR(20) NOT NULL DEFAULT 'AGENDADO';

UPDATE tenant_appointments SET booking_status = 'CANCELADO' WHERE cancelled_at IS NOT NULL;

ALTER TABLE tenant_appointments
    ADD CONSTRAINT chk_tenant_appointments_booking_status
    CHECK (booking_status IN ('AGENDADO', 'CANCELADO'));

CREATE INDEX IF NOT EXISTS idx_tenant_appointments_tenant_conv_day
    ON tenant_appointments (tenant_id, conversation_id, starts_at)
    WHERE booking_status = 'AGENDADO';
